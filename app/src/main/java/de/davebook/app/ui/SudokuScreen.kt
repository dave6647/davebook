package de.davebook.app.ui

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import de.davebook.app.Person

/**
 * Sudoku – aufgebaut wie Pfeilfrei: Das ganze Spiel liegt in `assets/sudoku.html`
 * (Quelle `web/sudoku.html`, erzeugt mit `tools/build_sudoku.py`), die App zeigt es nur in einer WebView an.
 *
 * Über `AndroidBridge` speichert das Spiel seinen Stand (SharedPreferences „sudoku“), fragt Name und Design ab
 * und lässt das Handy kurz vibrieren. Zurück-Taste: erst fragt die App das Spiel (`sdBack`), sonst [onExit].
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun SudokuScreen(me: Person, onExit: () -> Unit) {
    val dark = isSystemInDarkTheme()
    val background = MaterialTheme.colorScheme.background.toArgb()
    val holder = remember { WebHolder() }
    val lifecycleOwner = LocalLifecycleOwner.current

    // App im Hintergrund: Uhr anhalten und Stand sichern
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            val w = holder.view ?: return@LifecycleEventObserver
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    w.evaluateJavascript(PAUSE_JS, null)
                    w.onPause()
                }
                Lifecycle.Event.ON_RESUME -> w.onResume()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    BackHandler {
        val w = holder.view
        if (w == null) {
            onExit()
        } else {
            w.evaluateJavascript("(window.sdBack && window.sdBack()) ? 'ja' : 'nein'") { v ->
                if (v == null || !v.contains("ja")) onExit()
            }
        }
    }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            WebView(ctx).apply {
                // Wichtig: ohne MATCH_PARENT gilt die WebView in Compose als „wrap_content“. Dann setzt Android
                // die Seitenhöhe auf 0, „height:100%“ greift nicht und das Spielfeld rutscht über die Knöpfe.
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                setBackgroundColor(background)
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false
                settings.setSupportZoom(false)
                settings.builtInZoomControls = false
                settings.textZoom = 100
                overScrollMode = View.OVER_SCROLL_NEVER
                isVerticalScrollBarEnabled = false
                isHorizontalScrollBarEnabled = false
                webViewClient = WebViewClient()
                addJavascriptInterface(SudokuBridge(this, me, dark), "AndroidBridge")
                loadUrl("file:///android_asset/sudoku.html")
                holder.view = this
            }
        },
        onRelease = { w ->
            // Tab gewechselt: erst den Stand sichern lassen, dann die WebView freigeben
            holder.view = null
            var done = false
            val finish = Runnable {
                if (!done) {
                    done = true
                    w.destroy()
                }
            }
            w.evaluateJavascript(PAUSE_JS) { finish.run() }
            Handler(Looper.getMainLooper()).postDelayed(finish, 1500)
        },
    )
}

private const val PAUSE_JS = "window.sdPause && window.sdPause()"

private class WebHolder {
    var view: WebView? = null
}

/** Wird im Spiel als `window.AndroidBridge` angesprochen. Läuft nicht auf dem UI-Thread. */
private class SudokuBridge(private val view: View, private val me: Person, private val dark: Boolean) {
    private val prefs = view.context.applicationContext.getSharedPreferences("sudoku", Context.MODE_PRIVATE)

    @JavascriptInterface
    fun load(key: String): String? = prefs.getString(key, null)

    @JavascriptInterface
    fun save(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }

    /** "David" oder "Sophie" (für den Glückwunsch). */
    @JavascriptInterface
    fun name(): String = me.display

    /** "dark" oder "light", wie die App gerade aussieht. */
    @JavascriptInterface
    fun theme(): String = if (dark) "dark" else "light"

    /** Kurzes Vibrieren: "tap", "error" oder "win". */
    @JavascriptInterface
    fun haptic(kind: String) {
        val effect = when (kind) {
            "error" -> if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.LONG_PRESS
            "win" -> if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.LONG_PRESS
            else -> HapticFeedbackConstants.KEYBOARD_TAP
        }
        view.post { view.performHapticFeedback(effect) }
    }
}
