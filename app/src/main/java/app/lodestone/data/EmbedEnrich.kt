package app.lodestone.data

import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** Finds the title, author, picture and caption of a post on a known platform. */
object EmbedEnrich {
    private data class Meta(val title: String, val author: String, val thumbUrl: String, val text: String)

    /** Returns true when something useful was found and stored. */
    suspend fun run(id: Long, embed: Embed): Boolean = withContext(Dispatchers.IO) {
        val meta = metaFor(embed)
        val kind = embed.platform.name.lowercase()
        if (meta == null) {
            // Still record what it is, so the card shows its platform and the player works.
            Store.modifyNow(id) { it.copy(kind = kind, extra = extraJson(embed, "")) }
            return@withContext false
        }
        var thumb = ""
        var aspect = 0f
        var color = 0
        if (meta.thumbUrl.isNotBlank()) {
            val tmp = File(Store.thumbDir, "tmp-" + UUID.randomUUID())
            try {
                Ingest.download(meta.thumbUrl, tmp, 5_000_000)
                val bmp = Ingest.decodeSampled(tmp, 900)
                if (bmp != null) {
                    thumb = UUID.randomUUID().toString() + ".jpg"
                    File(Store.thumbDir, thumb).outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 85, it) }
                    aspect = bmp.height.toFloat() / bmp.width.toFloat()
                    color = Ingest.dominantColor(bmp)
                }
            } catch (_: Exception) {
            } finally { tmp.delete() }
        }
        val title = meta.title.ifBlank { embed.platform.label + (if (meta.author.isNotBlank()) " · " + meta.author else "") }
        Store.modifyNow(id) { cur ->
            val untouched = cur.title == cur.domain || cur.title == cur.url || cur.title == embed.platform.label.lowercase()
            cur.copy(
                kind = kind, extra = extraJson(embed, meta.author),
                title = if (untouched) title.take(200) else cur.title,
                thumb = if (thumb.isNotEmpty() && cur.thumb.isEmpty()) thumb else cur.thumb,
                aspect = if (thumb.isNotEmpty() && cur.thumb.isEmpty()) aspect else cur.aspect,
                color = if (thumb.isNotEmpty() && cur.thumb.isEmpty()) color else cur.color,
                text = if (cur.text.isEmpty()) meta.text else cur.text,
            )
        }
        if (thumb.isNotEmpty() && !Store.isThumbUsed(thumb)) File(Store.thumbDir, thumb).delete()
        true
    }

    private fun extraJson(e: Embed, author: String): String =
        JSONObject().put("platform", e.platform.label).put("author", author).put("embed", true).toString()

    private suspend fun metaFor(e: Embed): Meta? {
        // 1) the platform's public oEmbed address
        Embeds.oembedUrl(e)?.let { u ->
            val json = runCatching { JSONObject(Ingest.fetchRaw(u) ?: return@runCatching null) }.getOrNull()
            if (json != null) {
                val html = json.optString("html")
                val text = if (html.isNotBlank()) android.text.Html.fromHtml(html, android.text.Html.FROM_HTML_MODE_COMPACT).toString().replace(Regex("\\s+"), " ").trim() else ""
                val title = json.optString("title").ifBlank { text.take(120) }
                val thumb = json.optString("thumbnail_url")
                if (title.isNotBlank() || thumb.isNotBlank()) return Meta(title, json.optString("author_name"), thumb, text.take(2000))
            }
        }
        // 2) platforms that hide everything behind a login: read their public embed page
        if (Embeds.needsEmbedScrape(e)) {
            val f = EmbedScraper.read(e.src, e.referer) ?: return null
            val title = if (f.caption.isNotBlank()) f.caption.lineSequence().first().take(120) else (e.platform.label + " post" + (if (f.author.isNotBlank()) " by @${f.author}" else ""))
            return Meta(title, f.author, f.image, f.caption)
        }
        return null
    }
}
