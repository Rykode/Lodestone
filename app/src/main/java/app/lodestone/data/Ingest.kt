package app.lodestone.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.media.ExifInterface
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import android.text.Html
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

/** Turns shared text, links and files into stored items. Saving never needs a connection. */
object Ingest {
    private val urlRegex = Regex("https?://[^\\s<>\"']+", RegexOption.IGNORE_CASE)

    fun extractUrl(text: String): String? {
        var u = urlRegex.find(text)?.value ?: return null
        u = u.trimEnd('.', ',', ';', ']', '!', '?')
        // A closing bracket belongs to the link only if it has a matching opening one.
        while (u.endsWith(")") && u.count { it == ')' } > u.count { it == '(' }) u = u.dropLast(1)
        return u
    }

    fun domainOf(url: String): String = runCatching { Uri.parse(url).host.orEmpty().removePrefix("www.") }.getOrDefault("")

    data class Result(val saved: Int, val duplicates: Int)

    /** Saves whatever was shared or typed. Returns how many items were kept. */
    suspend fun keep(text: String, uris: List<Uri>, tags: List<String>, spaceId: Long): Result = withContext(Dispatchers.IO) {
        var saved = 0
        var dup = 0
        val trimmed = text.trim()
        if (trimmed.isNotEmpty()) {
            val raw = extractUrl(trimmed)
            if (raw != null) {
                val remainder = trimmed.replace(raw, "").trim()
                val embed = Embeds.parse(raw)
                val url = embed?.canonical ?: Embeds.clean(raw)
                val existing = Store.live.firstOrNull { it.url == url || (embed != null && Embeds.parse(it.url)?.canonical == url) }
                if (existing != null) dup++ else { saveLink(url, remainder, tags, spaceId); saved++ }
            } else {
                saveNote(trimmed, tags, spaceId); saved++
            }
        }
        for ((n, u) in uris.withIndex()) {
            if (uris.size > 1) Notifier.progress(Notifier.ID_SAVE, "Saving to Lodestone", "${n + 1} of ${uris.size}", n, uris.size)
            if (saveMedia(u, tags, spaceId) != 0L) saved++
        }
        if (uris.size > 1) Notifier.finish(Notifier.ID_SAVE, "Saved to Lodestone", "$saved of ${uris.size} items kept")
        Result(saved, dup)
    }

    suspend fun saveNote(text: String, tags: List<String>, spaceId: Long): Long {
        val first = text.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty().take(80)
        return Store.insert(Item(type = ItemType.NOTE, title = first, note = text, tags = tags, spaceId = spaceId))
    }

    /** Uses a picture you choose as the preview of a link that would not save, and reads its words. */
    suspend fun attachScreenshot(id: Long, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        val tmp = File(Store.app.cacheDir, "shot-" + UUID.randomUUID())
        try {
            val ok = Store.app.contentResolver.openInputStream(uri)?.use { input -> tmp.outputStream().use { input.copyTo(it) } } != null
            val bmp = if (ok) decodeSampled(tmp, 900)?.let { rotateByExif(tmp, it) } else null
            if (bmp == null) return@withContext false
            val name = UUID.randomUUID().toString() + ".jpg"
            File(Store.thumbDir, name).outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 85, it) }
            val words = runCatching { Ocr.read(tmp) }.getOrDefault("")
            val aspect = bmp.height.toFloat() / bmp.width.toFloat()
            val color = dominantColor(bmp)
            Store.modifyNow(id) { cur ->
                cur.copy(thumb = name, aspect = aspect, color = color, text = if (cur.text.isEmpty()) words else cur.text,
                    enriched = true, extra = mergeExtra(cur.extra, mapOf("blocked" to false, "screenshot" to true)))
            }
            true
        } finally { tmp.delete() }
    }

    /** Keeps just the address, with no preview, and stops offering fallbacks. */
    fun keepLinkOnly(id: Long) { Store.edit(id) { it.copy(enriched = true, extra = mergeExtra(it.extra, mapOf("blocked" to false))) } }

    suspend fun saveLink(url: String, note: String, tags: List<String>, spaceId: Long): Long {
        val domain = domainOf(url)
        val id = Store.insert(
            Item(type = ItemType.LINK, title = domain.ifEmpty { url }, note = note, url = url, domain = domain,
                tags = tags, spaceId = spaceId, enriched = false, kind = Embeds.parse(url)?.platform?.name?.lowercase().orEmpty())
        )
        Store.scope.launch { enrich(id) }
        return id
    }

    suspend fun saveMedia(uri: Uri, tags: List<String>, spaceId: Long, mimeHint: String? = null): Long = withContext(Dispatchers.IO) {
        val ctx = Store.app
        val cr = ctx.contentResolver
        val mime = cr.getType(uri) ?: mimeHint ?: "application/octet-stream"
        var name = ""
        runCatching {
            cr.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) name = it.getString(0).orEmpty() }
        }
        val ext = name.substringAfterLast('.', "").lowercase().ifEmpty { mime.substringAfter('/', "bin").substringBefore('+').take(5) }
        val stored = UUID.randomUUID().toString() + "." + ext
        val dest = File(Store.mediaDir, stored)
        val ok = runCatching {
            cr.openInputStream(uri)?.use { input -> dest.outputStream().use { input.copyTo(it) } } != null
        }.getOrDefault(false)
        if (!ok || !dest.exists() || dest.length() == 0L) { dest.delete(); return@withContext 0L }

        val type = when {
            mime.startsWith("image/") -> ItemType.IMAGE
            mime.startsWith("video/") -> ItemType.VIDEO
            else -> ItemType.FILE
        }
        var thumbName = ""
        var aspect = 0f
        var color = 0
        val bmp: Bitmap? = when (type) {
            ItemType.IMAGE -> decodeSampled(dest, 900)?.let { rotateByExif(dest, it) }
            ItemType.VIDEO -> videoFrame(dest)
            else -> if (mime == "application/pdf") Pdfs.render(dest, 0, 900) else null
        }
        val isPdf = type == ItemType.FILE && mime == "application/pdf"
        val pdfPages = if (isPdf) Pdfs.pageCount(dest) else 0
        if (bmp != null) {
            thumbName = UUID.randomUUID().toString() + ".jpg"
            File(Store.thumbDir, thumbName).outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 85, it) }
            aspect = bmp.height.toFloat() / bmp.width.toFloat()
            color = dominantColor(bmp)
        }
        val title = name.substringBeforeLast('.').ifEmpty { type.name.lowercase().replaceFirstChar { it.uppercase() } }
        val newId = Store.insert(
            Item(type = type, title = title, file = stored, thumb = thumbName, mime = mime, tags = tags,
                spaceId = spaceId, aspect = aspect, color = color,
                kind = if (isPdf) "pdf" else "", extra = if (isPdf) mergeExtra("", mapOf("pages" to pdfPages)) else "")
        )
        if (type == ItemType.IMAGE || isPdf) Store.scope.launch { Ocr.process(newId) }
        newId
    }

    // ---------- images ----------

    fun decodeSampled(file: File, maxDim: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (bounds.outWidth / sample > maxDim * 2 || bounds.outHeight / sample > maxDim * 2) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val bmp = BitmapFactory.decodeFile(file.path, opts) ?: return null
        val scale = maxDim.toFloat() / maxOf(bmp.width, bmp.height)
        return if (scale < 1f) Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt().coerceAtLeast(1), (bmp.height * scale).toInt().coerceAtLeast(1), true) else bmp
    }

    internal fun rotateByExif(file: File, bmp: Bitmap): Bitmap {
        val degrees = runCatching {
            when (ExifInterface(file.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        }.getOrDefault(0f)
        if (degrees == 0f) return bmp
        return Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(degrees) }, true)
    }

    private fun videoFrame(file: File): Bitmap? = runCatching {
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(file.path)
            val frame = r.getFrameAtTime(1_000_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC) ?: r.getFrameAtTime(0)
            frame?.let {
                val scale = 900f / maxOf(it.width, it.height)
                if (scale < 1f) Bitmap.createScaledBitmap(it, (it.width * scale).toInt(), (it.height * scale).toInt(), true) else it
            }
        } finally { r.release() }
    }.getOrNull()

    /** Average colour weighted towards vivid pixels, so a photo of a sunset reads as orange, not grey. */
    fun dominantColor(src: Bitmap): Int {
        val small = Bitmap.createScaledBitmap(src, 24, 24, true)
        var r = 0.0; var g = 0.0; var b = 0.0; var wSum = 0.0
        var ar = 0.0; var ag = 0.0; var ab = 0.0
        val hsv = FloatArray(3)
        for (y in 0 until small.height) for (x in 0 until small.width) {
            val p = small.getPixel(x, y)
            Color.colorToHSV(p, hsv)
            val w = 0.05 + hsv[1] * hsv[2]
            r += Color.red(p) * w; g += Color.green(p) * w; b += Color.blue(p) * w; wSum += w
            ar += Color.red(p); ag += Color.green(p); ab += Color.blue(p)
        }
        val n = small.width * small.height
        return if (wSum > 0) Color.rgb((r / wSum).toInt(), (g / wSum).toInt(), (b / wSum).toInt())
        else Color.rgb((ar / n).toInt(), (ag / n).toInt(), (ab / n).toInt())
    }

    // ---------- link previews (best effort, needs a connection) ----------

    private const val MAX_TRIES = 3

    /** HTTP status of the last page fetched on this thread, so a refusal can be told apart from "no connection". */
    private val lastStatus = ThreadLocal<Int>()

    private val blockedWords = Regex("log ?in|sign ?in|enable javascript|not supported|no longer supported|update your browser|unsupported browser|access denied|captcha|verify you are human|are you a robot", RegexOption.IGNORE_CASE)

    /** A page that is only a login wall, a robot check or a "your browser is too old" notice. */
    internal fun looksBlocked(title: String?, html: String, text: String): Boolean =
        text.length < 500 && blockedWords.containsMatchIn((title ?: "") + " " + html.take(4000))

    internal fun mergeExtra(old: String, add: Map<String, Any>): String {
        val o = runCatching { if (old.isBlank()) org.json.JSONObject() else org.json.JSONObject(old) }.getOrDefault(org.json.JSONObject())
        add.forEach { (k, v) -> o.put(k, v) }
        return o.toString()
    }

    /** Fetches the previews of every link that has none yet, three at a time, reporting progress. */
    suspend fun enrichAll() {
        val pending = Store.items.filter { it.type == ItemType.LINK && !it.enriched && it.deleted == 0L }
        if (pending.isEmpty()) return
        val gate = kotlinx.coroutines.sync.Semaphore(3)
        val done = java.util.concurrent.atomic.AtomicInteger(0)
        Notifier.progress(Notifier.ID_PREVIEWS, "Fetching previews", "0 of ${pending.size}", 0, pending.size)
        kotlinx.coroutines.coroutineScope {
            val scope = this
            pending.map { item ->
                scope.async(Dispatchers.IO) {
                    gate.acquire()
                    try { enrich(item.id) } finally {
                        gate.release()
                        val n = done.incrementAndGet()
                        Notifier.progress(Notifier.ID_PREVIEWS, "Fetching previews", "$n of ${pending.size}", n, pending.size)
                    }
                }
            }.forEach { it.await() }
        }
        Notifier.finish(Notifier.ID_PREVIEWS, "Previews ready", "${pending.size} links updated")
    }

    suspend fun retryEnrichment() {
        Store.items.filter { it.type == ItemType.LINK && !it.enriched && it.deleted == 0L }.take(15).forEach { enrich(it.id) }
    }

    /** Fetches title, preview image and readable text. After a few failed tries the link is kept as it is, without a preview. */
    suspend fun enrich(id: Long, force: Boolean = false) = withContext(Dispatchers.IO) {
        val item = Store.get(id) ?: return@withContext
        val key = "tries_$id"
        // Known platforms (YouTube, Instagram, X, ...) are read through their embed and oEmbed pages.
        val embed = Embeds.parse(item.url)
        if (embed != null) {
            val ok = runCatching { EmbedEnrich.run(id, embed) }.getOrDefault(false)
            if (ok) {
                Store.modifyNow(id) { it.copy(enriched = true) }
                Store.prefs.edit().remove(key).apply()
                return@withContext
            }
            val tries = Store.prefs.getInt(key, 0) + 1
            Store.prefs.edit().putInt(key, tries).apply()
            if (tries >= MAX_TRIES) Store.modifyNow(id) { it.copy(enriched = true) }
            return@withContext
        }
        lastStatus.set(0)
        val html = runCatching { fetch(item.url) }.getOrNull()
        if (html == null) {
            val status = lastStatus.get() ?: 0
            // A refusal (login required, forbidden, blocked as a robot) will not change by retrying: offer the fallbacks now.
            if (status == 401 || status == 403 || status == 429) {
                Store.modifyNow(id) { it.copy(enriched = true, extra = mergeExtra(it.extra, mapOf("blocked" to true))) }
                return@withContext
            }
            val tries = Store.prefs.getInt(key, 0) + 1
            Store.prefs.edit().putInt(key, tries).apply()
            if (tries >= MAX_TRIES) Store.modifyNow(id) { it.copy(enriched = true) }
            return@withContext
        }
        val title = meta(html, "og:title") ?: Regex("<title[^>]*>(.*?)</title>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).find(html)?.groupValues?.get(1)?.let(::clean)
        val desc = meta(html, "og:description") ?: meta(html, "description")
        val imageUrl = meta(html, "og:image") ?: meta(html, "twitter:image")
        var thumbName = ""
        var aspect = 0f
        var color = 0
        if (imageUrl != null) {
            runCatching {
                val abs = URL(URL(item.url), imageUrl).toString()
                val tmp = File(Store.thumbDir, "tmp-" + UUID.randomUUID())
                try {
                    download(abs, tmp, 4_000_000)
                    val bmp = decodeSampled(tmp, 900)
                    if (bmp != null) {
                        thumbName = UUID.randomUUID().toString() + ".jpg"
                        File(Store.thumbDir, thumbName).outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 85, it) }
                        aspect = bmp.height.toFloat() / bmp.width.toFloat()
                        color = dominantColor(bmp)
                    }
                } finally { tmp.delete() }
            }
        }
        val text = runCatching { readable(html) }.getOrDefault("")
        val cleanedDesc = desc?.let { clean(it).take(400) }
        // A page with no real content whose only title is the site's own name (such as a login wall drawn by script).
        val brand = item.domain.removePrefix("www.").substringBefore('.')
        val hollow = text.length < 80 && brand.isNotEmpty() && title?.trim()?.equals(brand, ignoreCase = true) == true
        if (!force && (looksBlocked(title, html, text) || hollow)) {
            // Only a login wall came back. Keep the address, do not store the wall as the page, and offer the fallbacks.
            Store.modifyNow(id) { it.copy(enriched = true, extra = mergeExtra(it.extra, mapOf("blocked" to true))) }
            return@withContext
        }
        val card = runCatching { CardKinds.detect(html) }.getOrNull()
        val words = if (text.isBlank()) 0 else text.split(Regex("\\s+")).size
        val facts = HashMap<String, Any>()
        card?.facts?.forEach { (k, v) -> facts[k] = v }
        if (words >= 250) facts["read"] = Math.ceil(words / 220.0).toInt()
        facts["blocked"] = false
        // Applied to the latest stored row, so anything edited while the page was loading is kept.
        Store.modifyNow(id) { cur ->
            val untouched = force || cur.title == cur.domain || cur.title == cur.url
            cur.copy(
                title = if (untouched) title?.takeIf { it.isNotBlank() }?.take(200) ?: cur.title else cur.title,
                note = if (cur.note.isEmpty() && cleanedDesc != null) cleanedDesc else cur.note,
                thumb = if (thumbName.isNotEmpty() && (force || cur.thumb.isEmpty())) thumbName else cur.thumb,
                aspect = if (thumbName.isNotEmpty() && (force || cur.thumb.isEmpty())) aspect else cur.aspect,
                color = if (thumbName.isNotEmpty() && (force || cur.thumb.isEmpty())) color else cur.color,
                text = text, enriched = true,
                kind = card?.kind ?: cur.kind,
                extra = mergeExtra(cur.extra, facts),
                // Tags the page declares for itself, only when you have not added your own.
                tags = if (cur.tags.isEmpty() && card != null) card.keywords else cur.tags,
            )
        }
        if (thumbName.isNotEmpty() && !Store.isThumbUsed(thumbName)) File(Store.thumbDir, thumbName).delete()
        Store.prefs.edit().remove(key).apply()
    }

    internal fun open(url: String): HttpURLConnection = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 10_000; readTimeout = 12_000; instanceFollowRedirects = true
        // The same string Chrome on Android sends. A bare "Android" with no version makes many sites answer
        // "your Android is no longer supported" instead of the real page.
        setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/139.0.0.0 Mobile Safari/537.36")
        setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
        setRequestProperty("Accept-Language", "en")
    }

    private fun fetch(url: String): String? {
        val c = open(url)
        try {
            lastStatus.set(c.responseCode)
            if (c.responseCode !in 200..299) return null
            val type = c.contentType.orEmpty()
            if (type.isNotEmpty() && !type.contains("html", true) && !type.contains("text", true)) return null
            val bytes = c.inputStream.use { input ->
                val out = java.io.ByteArrayOutputStream()
                val buf = ByteArray(16 * 1024)
                while (out.size() < 1_500_000) { val n = input.read(buf); if (n < 0) break; out.write(buf, 0, n) }
                out.toByteArray()
            }
            val head = String(bytes, 0, minOf(bytes.size, 4000), Charsets.ISO_8859_1)
            val cs = Regex("charset=([\\w-]+)", RegexOption.IGNORE_CASE).find(type)?.groupValues?.get(1)
                ?: Regex("charset=[\"']?([\\w-]+)", RegexOption.IGNORE_CASE).find(head)?.groupValues?.get(1)
            return String(bytes, runCatching { charset(cs ?: "UTF-8") }.getOrDefault(Charsets.UTF_8))
        } finally { c.disconnect() }
    }

    /** Fetches any text response, such as an oEmbed JSON document. */
    internal fun fetchRaw(url: String): String? {
        val c = open(url)
        return try {
            c.setRequestProperty("Accept", "application/json,text/plain,*/*")
            if (c.responseCode !in 200..299) null else c.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
        } finally { c.disconnect() }
    }

    internal fun download(url: String, dest: File, max: Int) {
        val c = open(url)
        try {
            if (c.responseCode !in 200..299) error("http ${c.responseCode}")
            c.inputStream.use { input -> dest.outputStream().use { out ->
                var total = 0; val buf = ByteArray(16 * 1024)
                while (true) { val n = input.read(buf); if (n < 0) break; total += n; if (total > max) error("too large"); out.write(buf, 0, n) }
            } }
        } finally { c.disconnect() }
    }

    private fun meta(html: String, key: String): String? {
        val k = Regex.escape(key)
        val a = Regex("<meta[^>]+(?:property|name)=[\"']$k[\"'][^>]*content=[\"']([^\"']*)[\"']", RegexOption.IGNORE_CASE).find(html)
        val b = Regex("<meta[^>]+content=[\"']([^\"']*)[\"'][^>]*(?:property|name)=[\"']$k[\"']", RegexOption.IGNORE_CASE).find(html)
        return (a ?: b)?.groupValues?.get(1)?.let(::clean)?.takeIf { it.isNotBlank() }
    }

    private fun clean(s: String): String = Html.fromHtml(s, Html.FROM_HTML_MODE_COMPACT).toString().replace(Regex("\\s+"), " ").trim()

    /** Rough reader view: paragraphs and headings, scripts and navigation stripped. */
    private fun readable(html: String): String {
        val opts = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        var body = html.replace(Regex("<(script|style|nav|header|footer|aside|form|noscript)[^>]*>.*?</\\1>", opts), " ")
        val article = Regex("<(article|main)[^>]*>(.*?)</\\1>", opts).find(body)?.groupValues?.get(2)
        if (article != null && article.length > 500) body = article
        val blocks = Regex("<(p|h1|h2|h3|li|blockquote)[^>]*>(.*?)</\\1>", opts).findAll(body).map { clean(it.groupValues[2]) }.filter { it.length > 25 }.toList()
        return blocks.joinToString("\n\n").take(60_000)
    }
}
