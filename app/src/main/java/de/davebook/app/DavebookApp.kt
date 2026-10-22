package de.davebook.app

import android.app.Application

class DavebookApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Store.init(this)
        Notifier.createChannels(this)
    }
}
