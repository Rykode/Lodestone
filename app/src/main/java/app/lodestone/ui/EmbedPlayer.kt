package app.lodestone.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import app.lodestone.data.Embed

/**
 * The platform's own player, shown inside the app. It only exists once you tap play, so a library full of
 * links loads nothing from the internet, and it is torn down the moment you leave the screen.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun EmbedPlayer(embed: Embed, modifier: Modifier = Modifier.fillMaxSize()) {
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.mediaPlaybackRequiresUserGesture = true
                settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                settings.setSupportMultipleWindows(false)
                setBackgroundColor(0)
                // Embedded players (YouTube, Spotify, Instagram...) keep their own cookies for the session.
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                webChromeClient = WebChromeClient()
                webViewClient = object : WebViewClient() {
                    // Taps on links inside a post open in the browser instead of replacing the player.
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                        val target = request.url
                        if (!request.isForMainFrame) return false
                        val first = Uri.parse(embed.src).host
                        if (target.host == first || target.scheme !in setOf("http", "https")) return false
                        runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, target).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                        return true
                    }
                }
                loadUrl(embed.src, if (embed.referer != null) mapOf("Referer" to embed.referer) else emptyMap())
            }
        },
        onRelease = { web -> web.stopLoading(); web.loadUrl("about:blank"); web.destroy() },
    )
}
