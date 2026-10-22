package de.davebook.app

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

object Notifier {
    const val CH_LOVE = "love"
    const val CH_SERVICE = "service"
    const val CH_REMINDER = "reminder"
    const val ID_SERVICE = 1
    private const val ID_HEART = 2
    private const val ID_NOTE = 3
    private val eventIds = AtomicInteger(100)
    private val ROSE = 0xFFD81B60.toInt()

    fun createChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        val love = NotificationChannel(CH_LOVE, "Herzen & Nachrichten", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Wenn dein Schatz an dich denkt, dir etwas schreibt oder einen Termin einträgt"
            enableVibration(true)
        }
        val service = NotificationChannel(CH_SERVICE, "Hintergrund-Verbindung", NotificationManager.IMPORTANCE_MIN).apply {
            description = "Android verlangt diese Benachrichtigung für die Dauerverbindung. " +
                "Ausschalten geht problemlos – Herzen kommen trotzdem sofort an."
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_SECRET
        }
        val reminder = NotificationChannel(CH_REMINDER, "Erinnerungen", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Liebe Erinnerungen, z. B. ans Trinken"
            enableVibration(true)
        }
        nm.createNotificationChannel(love)
        nm.createNotificationChannel(service)
        nm.createNotificationChannel(reminder)
    }

    fun reminder(ctx: Context, r: Reminder, from: Person) {
        val n = NotificationCompat.Builder(ctx, CH_REMINDER)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(ROSE)
            .setAutoCancel(true)
            .setContentIntent(openApp(ctx))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentTitle(r.text)
            .setContentText("Erinnerung von ${from.display} ❤️")
            .build()
        post(ctx, 1000 + (r.id.hashCode() and 0xFFF), n)
    }

    private fun canNotify(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun openApp(ctx: Context): PendingIntent {
        val intent = Intent(ctx, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            ctx, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun loveBuilder(ctx: Context): NotificationCompat.Builder =
        NotificationCompat.Builder(ctx, CH_LOVE)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(ROSE)
            .setAutoCancel(true)
            .setContentIntent(openApp(ctx))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)

    private fun post(ctx: Context, id: Int, n: Notification) {
        if (!canNotify(ctx)) return
        try {
            ctx.getSystemService(NotificationManager::class.java)?.notify(id, n)
        } catch (e: Exception) {
            // keine Berechtigung o. Ä.
        }
    }

    fun heart(ctx: Context, from: Person, count: Int) {
        val text = if (count > 1) {
            "Schon ${count}× seit du zuletzt reingeschaut hast 💕"
        } else {
            "Tippe hier und denk zurück 💕"
        }
        post(ctx, ID_HEART, loveBuilder(ctx).setContentTitle("${from.display} denkt an dich ❤️").setContentText(text).build())
    }

    fun note(ctx: Context, from: Person, text: String) {
        post(
            ctx, ID_NOTE,
            loveBuilder(ctx)
                .setContentTitle("Neue Nachricht von ${from.display} 💌")
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .build(),
        )
    }

    fun event(ctx: Context, from: Person, e: CalEvent) {
        val date = try {
            LocalDate.parse(e.date).format(DateTimeFormatter.ofPattern("EEEE, d. MMMM", Locale.GERMAN))
        } catch (ex: Exception) {
            e.date
        }
        val time = if (e.time.isNotEmpty()) " um ${e.time} Uhr" else ""
        post(
            ctx, eventIds.incrementAndGet(),
            loveBuilder(ctx)
                .setContentTitle("${from.display} hat etwas eingetragen 📅")
                .setContentText("${e.emoji} ${e.title} · $date$time")
                .build(),
        )
    }

    fun cancelHearts(ctx: Context) {
        try {
            ctx.getSystemService(NotificationManager::class.java)?.cancel(ID_HEART)
        } catch (e: Exception) {
            // egal
        }
    }

    /**
     * Android verlangt für den Hintergrunddienst eine Benachrichtigung, die App kann sie nicht
     * selbst verstecken. Schaltet man den Kanal "Hintergrund-Verbindung" aus, verschwindet sie,
     * der Dienst läuft aber weiter. Der Verbindungsstatus steht stattdessen in der App.
     */
    fun serviceNotification(ctx: Context): Notification {
        val hide = PendingIntent.getActivity(
            ctx, 1, serviceSettingsIntent(ctx).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(ctx, CH_SERVICE)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(ROSE)
            .setContentTitle("Davebook")
            .setContentText("Tippe hier, um diesen Hinweis auszublenden")
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(hide)
            .build()
    }

    /** Öffnet direkt den Schalter für den Kanal "Hintergrund-Verbindung". */
    fun serviceSettingsIntent(ctx: Context): Intent =
        Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
            .putExtra(Settings.EXTRA_CHANNEL_ID, CH_SERVICE)

    /** true, wenn der Hinweis des Hintergrunddienstes nicht (mehr) in der Leiste erscheint. */
    fun serviceHintHidden(ctx: Context): Boolean {
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return true
        // Ganz ohne Benachrichtigungen ist ohnehin nichts in der Leiste
        if (!nm.areNotificationsEnabled()) return true
        val ch = nm.getNotificationChannel(CH_SERVICE) ?: return false
        return ch.importance == NotificationManager.IMPORTANCE_NONE
    }
}
