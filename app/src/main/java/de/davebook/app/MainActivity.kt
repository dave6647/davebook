package de.davebook.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import de.davebook.app.ui.DavebookRoot
import de.davebook.app.ui.DavebookTheme

class MainActivity : ComponentActivity() {

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DavebookTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    DavebookRoot(onRequestNotifications = { requestNotifications() })
                }
            }
        }
        if (Store.state.value.setupDone) requestNotifications()
    }

    override fun onResume() {
        super.onResume()
        Store.appVisible = true
        Store.clearUnseen()
        Notifier.cancelHearts(this)
        Store.resumeTick.value = Store.resumeTick.value + 1
        SyncService.start(this)
        Sync.maybeHello()
        Sync.flush()
    }

    override fun onPause() {
        Store.appVisible = false
        super.onPause()
    }

    private fun requestNotifications() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
