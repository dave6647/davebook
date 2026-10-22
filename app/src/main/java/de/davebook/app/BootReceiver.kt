package de.davebook.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Startet die Verbindung nach einem Neustart des Handys oder nach einem App-Update. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Store.init(context)
        SyncService.start(context)
    }
}
