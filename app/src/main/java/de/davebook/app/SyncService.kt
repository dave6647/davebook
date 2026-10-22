package de.davebook.app

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Hält im Hintergrund die Verbindung zum gemeinsamen Kanal offen,
 * damit Herzen und Nachrichten sofort als Benachrichtigung ankommen.
 */
class SyncService : Service() {
    private var job: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = Notifier.serviceNotification(this)
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(Notifier.ID_SERVICE, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING)
            } else {
                startForeground(Notifier.ID_SERVICE, notification)
            }
        } catch (e: Exception) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (job?.isActive != true) {
            job = Store.scope.launch { Sync.runLoop() }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        job?.cancel()
        job = null
        Sync.restart()
        Store.setConnected(false)
        super.onDestroy()
    }

    companion object {
        fun start(context: Context) {
            if (Store.state.value.secret == null) return
            try {
                ContextCompat.startForegroundService(context, Intent(context, SyncService::class.java))
            } catch (e: Exception) {
                // Start aus dem Hintergrund nicht erlaubt – beim nächsten Öffnen der App klappt es
            }
        }
    }
}
