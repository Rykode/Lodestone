package app.lodestone

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import app.lodestone.data.SharedIntent
import app.lodestone.data.Store
import app.lodestone.ui.LodestoneApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Keep the splash screen up until the library has loaded (at most 1.5 s), so the empty state never flashes.
        val started = SystemClock.uptimeMillis()
        installSplashScreen().setKeepOnScreenCondition { !Store.loaded && SystemClock.uptimeMillis() - started < 1500 }
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handle(intent)
        setContent { LodestoneApp() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    /** A share handed over from [ShareReceiverActivity] (when "Save instantly" is off): hold it for the Save sheet. */
    private fun handle(intent: Intent?) {
        intent ?: return
        // Reopening from Recents replays the old intent; ignore that.
        if (intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0) return
        SharedIntent.parse(intent)?.let { Store.pending = it }
    }
}
