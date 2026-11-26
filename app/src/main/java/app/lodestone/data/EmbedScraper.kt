package app.lodestone.data

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import org.json.JSONTokener
import kotlin.coroutines.resume

/**
 * Instagram and Threads hide their pages behind a login, but their public embed page shows a post to anyone.
 * This loads that embed page in an invisible browser, waits for it to draw, and reads the picture and caption.
 */
object EmbedScraper {
    data class Found(val image: String, val caption: String, val author: String)

    private const val READ_JS = """
(function(){
  var imgs=[].slice.call(document.images).filter(function(i){return i.naturalWidth>240 && i.src && i.src.indexOf('data:')!==0});
  imgs.sort(function(a,b){return b.naturalWidth*b.naturalHeight-a.naturalWidth*a.naturalHeight});
  var v=document.querySelector('video');
  var cap=document.querySelector('.Caption,[class*=Caption]');
  var user=document.querySelector('.UsernameText,[class*=Username],[class*=username]');
  var gone=/isn.t available|no longer available|not available|log in|sign up/i.test(document.body?document.body.innerText.slice(0,400):'');
  return JSON.stringify({img: imgs[0]?imgs[0].src:(v&&v.poster?v.poster:''), cap: cap?cap.innerText:'', user: user?user.innerText:'', gone: gone});
})()
"""

    @SuppressLint("SetJavaScriptEnabled")
    suspend fun read(embedUrl: String, referer: String?): Found? = withContext(Dispatchers.Main) {
        withTimeoutOrNull(28_000) {
            suspendCancellableCoroutine { cont ->
                val handler = Handler(Looper.getMainLooper())
                val web = WebView(Store.app)
                var finished = false
                fun finish(f: Found?) {
                    if (finished) return
                    finished = true
                    handler.removeCallbacksAndMessages(null)
                    runCatching { web.stopLoading(); web.destroy() }
                    cont.resume(f)
                }
                web.settings.javaScriptEnabled = true
                web.settings.domStorageEnabled = true
                // A browser that is not on screen still needs a size, or the page lays out at zero width.
                web.layout(0, 0, 1080, 1920)
                var tries = 0
                fun poll() {
                    if (finished) return
                    web.evaluateJavascript(READ_JS) { raw ->
                        android.util.Log.d("LodeScrape", "try $tries url=${web.url} raw=${raw?.take(300)}")
                        val found = runCatching {
                            val s = JSONTokener(raw).nextValue() as String
                            val o = JSONObject(s)
                            Found(o.optString("img"), o.optString("cap").trim(), o.optString("user").trim().removePrefix("@")) to o.optBoolean("gone")
                        }.getOrNull()
                        if (found != null && found.first.image.isNotBlank()) finish(found.first)
                        else if (++tries >= 22) finish(null)
                        else handler.postDelayed({ poll() }, 1000)
                    }
                }
                web.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) { handler.postDelayed({ poll() }, 800) }
                }
                cont.invokeOnCancellation { handler.post { runCatching { web.destroy() } } }
                web.loadUrl(embedUrl, if (referer != null) mapOf("Referer" to referer) else emptyMap())
            }
        }
    }
}
