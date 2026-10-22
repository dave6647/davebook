package de.davebook.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import java.time.Instant
import java.time.ZoneId

/**
 * Plant die Erinnerungen auf dem Handy, für das sie bestimmt sind (Sophie),
 * über den AlarmManager – das funktioniert auch, wenn die App zu ist.
 */
object Reminders {
    private const val QUIET_START = 22
    private const val QUIET_END = 8

    val intervalOptions: List<Int> = listOf(30, 60, 120, 180, 240, 360, 480, 720, 1440)

    fun intervalLabel(minutes: Int): String = when {
        minutes == 1440 -> "1× täglich"
        minutes < 60 -> "alle $minutes Min"
        minutes == 60 -> "jede Stunde"
        minutes % 60 == 0 -> "alle ${minutes / 60} Std"
        else -> "alle ${minutes / 60} Std ${minutes % 60} Min"
    }

    fun chipLabel(minutes: Int): String = when {
        minutes == 1440 -> "1× am Tag"
        minutes < 60 -> "$minutes Min"
        else -> "${minutes / 60} Std"
    }

    private fun hourOf(t: Long): Int = Instant.ofEpochMilli(t).atZone(ZoneId.systemDefault()).hour

    fun isQuiet(t: Long): Boolean {
        val h = hourOf(t)
        return h >= QUIET_START || h < QUIET_END
    }

    /** Verschiebt einen Zeitpunkt in der Nachtruhe auf 8 Uhr morgens. */
    private fun shiftOutOfQuiet(t: Long): Long {
        val zone = ZoneId.systemDefault()
        val dt = Instant.ofEpochMilli(t).atZone(zone)
        return when {
            dt.hour >= QUIET_START ->
                dt.toLocalDate().plusDays(1).atTime(QUIET_END, 0).atZone(zone).toInstant().toEpochMilli()
            dt.hour < QUIET_END ->
                dt.toLocalDate().atTime(QUIET_END, 0).atZone(zone).toInstant().toEpochMilli()
            else -> t
        }
    }

    fun nextFire(r: Reminder, lastFired: Long?, now: Long): Long {
        val interval = r.intervalMinutes.coerceAtLeast(15) * 60_000L
        val anchor = maxOf(lastFired ?: 0L, r.since)
        var t = anchor + interval
        if (t < now + 30_000L) t = now + 60_000L
        if (r.quiet) t = shiftOutOfQuiet(t)
        return t
    }

    private fun pendingIntent(ctx: Context, id: String): PendingIntent {
        val intent = Intent(ctx, ReminderReceiver::class.java)
            .setData(Uri.parse("davebook://reminder/" + Uri.encode(id)))
            .putExtra("id", id)
        return PendingIntent.getBroadcast(
            ctx, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    fun reschedule(ctx: Context) {
        val s = Store.state.value
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        val me = s.me
        val now = System.currentTimeMillis()
        for (r in s.reminders.values) {
            val pi = pendingIntent(ctx, r.id)
            val active = me != null && s.setupDone && r.enabled && !r.deleted && r.target == me.id
            try {
                if (active) {
                    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextFire(r, s.reminderLastFired[r.id], now), pi)
                } else {
                    am.cancel(pi)
                }
            } catch (e: Exception) {
                // Planen fehlgeschlagen – beim nächsten Öffnen erneut
            }
        }
    }

    fun fire(ctx: Context, id: String) {
        val s = Store.state.value
        val r = s.reminders[id] ?: return
        val me = s.me ?: return
        if (!s.setupDone || !r.enabled || r.deleted || r.target != me.id) {
            reschedule(ctx)
            return
        }
        val now = System.currentTimeMillis()
        if (!(r.quiet && isQuiet(now))) {
            val from = Person.fromId(r.by) ?: me.partner
            Notifier.reminder(ctx, r, from)
            Store.markReminderFired(id, now)
        }
        reschedule(ctx)
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Store.init(context)
        val id = intent.getStringExtra("id") ?: return
        Reminders.fire(context, id)
    }
}
