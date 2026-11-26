package app.lodestone

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import app.lodestone.data.Ingest
import app.lodestone.data.SharedIntent
import app.lodestone.data.Store
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The share-sheet target. It has no screen: it saves what was shared, shows a toast and closes, so you stay in the
 * app you were using. If "Save instantly" is off in Settings it hands the share to the full Save sheet instead.
 */
class ShareReceiverActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val shared = SharedIntent.parse(intent)
        if (shared == null) { finish(); return }

        if (!Store.quickShare) {
            startActivity(Intent(intent).setClass(this, MainActivity::class.java).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
            finish()
            return
        }

        // A short tick so you feel it was saved even though no screen appears.
        window.decorView.performHapticFeedback(if (android.os.Build.VERSION.SDK_INT >= 30) android.view.HapticFeedbackConstants.CONFIRM else android.view.HapticFeedbackConstants.KEYBOARD_TAP)

        // Stay alive until the files are copied: the sender's permission to read them ends when this screen closes.
        Store.scope.launch {
            val result = Ingest.keep(shared.text, shared.uris, emptyList(), 0L)
            withContext(Dispatchers.Main) {
                Toast.makeText(applicationContext, SharedIntent.message(result), Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }
}
