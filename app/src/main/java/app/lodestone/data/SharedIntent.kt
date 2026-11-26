package app.lodestone.data

import android.content.Intent
import android.net.Uri
import android.os.Build

/** Reads what another app shared with us: text, a link, or one or more files. */
object SharedIntent {
    @Suppress("DEPRECATION")
    fun parse(intent: Intent?): Pending? {
        intent ?: return null
        if (intent.action != Intent.ACTION_SEND && intent.action != Intent.ACTION_SEND_MULTIPLE) return null
        // getCharSequenceExtra: apps that share styled text don't send a plain String.
        val text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString().orEmpty()
        val uris = buildList {
            if (intent.action == Intent.ACTION_SEND) {
                val u = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java) else intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                if (u != null) add(u)
            } else {
                val l = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java) else intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
                l?.let { addAll(it) }
            }
        }
        return if (text.isNotBlank() || uris.isNotEmpty()) Pending(text, uris) else null
    }

    /** Human message after a save, shared by the quick share target and the Save sheet. */
    fun message(r: Ingest.Result): String = when {
        r.saved > 0 && r.duplicates > 0 -> "Saved ${r.saved}, ${r.duplicates} already in your library"
        r.saved > 1 -> "Saved ${r.saved} items to Lodestone"
        r.saved == 1 -> "Saved to Lodestone"
        r.duplicates > 0 -> "Already in your library"
        else -> "Couldn't save that"
    }
}
