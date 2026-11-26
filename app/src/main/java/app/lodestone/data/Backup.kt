package app.lodestone.data

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Export and restore the whole library as one .zip file, and import browser bookmarks. */
object Backup {
    private const val JSON = "lodestone.json"

    data class Report(val added: Int, val skipped: Int, val spaces: Int, val problem: String? = null)

    suspend fun export(uri: Uri): Report = withContext(Dispatchers.IO) {
        runCatching {
            val items = Store.items.mapNotNull { Store.database().getItem(it.id) }
            val root = JSONObject().put("version", 1).put("exported", System.currentTimeMillis())
            root.put("spaces", JSONArray().also { a ->
                Store.spaces.forEach { s -> a.put(JSONObject().put("id", s.id).put("name", s.name).put("colorIdx", s.colorIdx).put("created", s.created).put("rule", s.rule)) }
            })
            root.put("items", JSONArray().also { a ->
                items.forEach { i ->
                    a.put(
                        JSONObject().put("type", i.type.name).put("title", i.title).put("note", i.note).put("url", i.url)
                            .put("file", i.file).put("thumb", i.thumb).put("domain", i.domain).put("tags", JSONArray(i.tags))
                            .put("spaceId", i.spaceId).put("pinned", i.pinned).put("created", i.created).put("deleted", i.deleted)
                            .put("color", i.color).put("text", i.text).put("mime", i.mime).put("enriched", i.enriched).put("aspect", i.aspect.toDouble())
                    )
                }
            })
            val resolver = Store.app.contentResolver
            val out = runCatching { resolver.openOutputStream(uri, "wt") }.getOrNull() ?: resolver.openOutputStream(uri) ?: error("Couldn't open the file to write")
            ZipOutputStream(out.buffered()).use { zip ->
                zip.putNextEntry(ZipEntry(JSON)); zip.write(root.toString().toByteArray()); zip.closeEntry()
                val written = HashSet<String>()
                items.forEachIndexed { n, i ->
                    if (n % 5 == 0) Notifier.progress(Notifier.ID_BACKUP, "Backing up Lodestone", "$n of ${items.size} saved to the file", n, items.size)
                    addFile(zip, "media/", Store.mediaDir, i.file, written)
                    addFile(zip, "thumbs/", Store.thumbDir, i.thumb, written)
                }
            }
            Report(items.size, 0, Store.spaces.size)
        }.getOrElse { Report(0, 0, 0, it.message ?: "Export failed") }
    }

    private fun addFile(zip: ZipOutputStream, prefix: String, dir: File, name: String, written: MutableSet<String>) {
        if (name.isEmpty() || !written.add(prefix + name)) return
        val f = File(dir, name)
        if (!f.exists()) return
        zip.putNextEntry(ZipEntry(prefix + name))
        f.inputStream().use { it.copyTo(zip) }
        zip.closeEntry()
    }

    /** Merges a backup into the library. Nothing is overwritten; duplicates are skipped. */
    suspend fun restore(uri: Uri): Report = withContext(Dispatchers.IO) {
        val tmp = File(Store.app.cacheDir, "restore-" + System.currentTimeMillis()).apply { mkdirs() }
        try {
            val input = Store.app.contentResolver.openInputStream(uri) ?: error("Couldn't open the file")
            var json: String? = null
            ZipInputStream(input.buffered()).use { zip ->
                while (true) {
                    val e = zip.nextEntry ?: break
                    val name = e.name
                    when {
                        name == JSON -> json = zip.readBytes().toString(Charsets.UTF_8)
                        name.startsWith("media/") && !e.isDirectory -> File(tmp, "m-" + File(name).name).outputStream().use { zip.copyTo(it) }
                        name.startsWith("thumbs/") && !e.isDirectory -> File(tmp, "t-" + File(name).name).outputStream().use { zip.copyTo(it) }
                    }
                    zip.closeEntry()
                }
            }
            val root = JSONObject(json ?: error("This doesn't look like a Lodestone backup"))
            val db = Store.database()
            var added = 0
            var skipped = 0
            var newSpaces = 0
            Store.lock.withLock {
                val existing = db.allSpaces().associateBy { it.name }.toMutableMap()
                val spaceMap = HashMap<Long, Long>()
                val sp = root.optJSONArray("spaces") ?: JSONArray()
                for (k in 0 until sp.length()) {
                    val o = sp.getJSONObject(k)
                    val name = o.getString("name")
                    val found = existing[name]
                    if (found != null) {
                        spaceMap[o.getLong("id")] = found.id
                    } else {
                        val id = db.insertSpace(Space(name = name, colorIdx = o.optInt("colorIdx"), created = o.optLong("created", System.currentTimeMillis()), rule = o.optString("rule")))
                        spaceMap[o.getLong("id")] = id
                        newSpaces++
                        existing[name] = Space(id, name)
                    }
                }
                // An item already here is recognised by what it is and when it was saved, not by its title (which may have been edited).
                val have = db.allItems().map { Triple(it.created, it.type, it.url.ifEmpty { it.file.substringAfterLast('.', "") }) }.toHashSet()
                val arr = root.optJSONArray("items") ?: JSONArray()
                for (k in 0 until arr.length()) {
                    if (k % 10 == 0) Notifier.progress(Notifier.ID_BACKUP, "Restoring from backup", "$k of ${arr.length()}", k, arr.length())
                    val o = arr.getJSONObject(k)
                    val type = runCatching { ItemType.valueOf(o.getString("type")) }.getOrDefault(ItemType.NOTE)
                    val url = o.optString("url")
                    val oldFile = File(o.optString("file")).name
                    val key = Triple(o.optLong("created"), type, url.ifEmpty { oldFile.substringAfterLast('.', "") })
                    if (key in have) { skipped++; continue }
                    // Every restored item gets its own copy of its files, so two rows never share one file.
                    val file = freshCopy(File(tmp, "m-$oldFile"), Store.mediaDir)
                    if (type != ItemType.NOTE && type != ItemType.LINK && file.isEmpty()) { skipped++; continue }
                    val thumb = freshCopy(File(tmp, "t-" + File(o.optString("thumb")).name), Store.thumbDir)
                    val tags = o.optJSONArray("tags")?.let { a -> List(a.length()) { a.getString(it) } } ?: emptyList()
                    db.insert(
                        Item(
                            type = type, title = o.optString("title"), note = o.optString("note"), url = url,
                            file = file, thumb = thumb, domain = o.optString("domain"), tags = tags,
                            spaceId = spaceMap[o.optLong("spaceId")] ?: 0L, pinned = o.optBoolean("pinned"), created = o.optLong("created"),
                            deleted = o.optLong("deleted"), color = o.optInt("color"), text = o.optString("text"), mime = o.optString("mime"),
                            enriched = o.optBoolean("enriched", true), aspect = o.optDouble("aspect", 0.0).toFloat(),
                        )
                    )
                    have.add(key)
                    added++
                }
            }
            Store.reloadNow()
            Report(added, skipped, newSpaces)
        } catch (e: Exception) {
            runCatching { Store.reloadNow() }
            Report(0, 0, 0, (e.message ?: "Restore failed") + ". Anything restored before the problem was kept.")
        } finally {
            tmp.deleteRecursively()
        }
    }

    /** Copies [src] into [dir] under a new random name. Returns the new name, or "" when there is nothing to copy. */
    private fun freshCopy(src: File, dir: File): String {
        if (!src.isFile || src.length() == 0L) return ""
        val ext = src.name.substringAfterLast('.', "")
        val name = java.util.UUID.randomUUID().toString() + if (ext.isNotEmpty()) ".$ext" else ""
        src.copyTo(File(dir, name))
        return name
    }

    /** Reads a browser's exported bookmarks.html. Folders become spaces; previews are fetched later. */
    suspend fun importBookmarks(uri: Uri): Report = withContext(Dispatchers.IO) {
        runCatching {
            val html = Store.app.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } ?: error("Couldn't open the file")
            val token = Regex("<DL|</DL|<H3[^>]*>(.*?)</H3>|<A\\s+[^>]*HREF=\"([^\"]+)\"[^>]*>(.*?)</A>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            val db = Store.database()
            val spaceIds = db.allSpaces().associate { it.name to it.id }.toMutableMap()
            val haveUrls = db.allItems().map { it.url }.toHashSet()
            val folders = ArrayList<String>()
            var pendingFolder: String? = null
            var added = 0; var skipped = 0; var newSpaces = 0
            val now = System.currentTimeMillis()
            var seen = 0
            Store.lock.withLock { for (m in token.findAll(html)) {
                if (++seen % 40 == 0) Notifier.progress(Notifier.ID_BACKUP, "Importing bookmarks", "$added links so far", 0, 0)
                val t = m.value
                when {
                    t.startsWith("<DL", true) -> { folders.add(pendingFolder ?: ""); pendingFolder = null }
                    t.startsWith("</DL", true) -> if (folders.isNotEmpty()) folders.removeAt(folders.lastIndex)
                    t.startsWith("<H3", true) -> pendingFolder = plain(m.groupValues[1])
                    else -> {
                        val url = plain(m.groupValues[2])
                        if (!url.startsWith("http", true)) continue
                        if (url in haveUrls) { skipped++; continue }
                        val folder = folders.lastOrNull { it.isNotBlank() }
                        val spaceId = if (folder == null || folder.equals("Bookmarks bar", true) || folder.equals("Bookmarks", true)) 0L else spaceIds.getOrPut(folder) {
                            newSpaces++; db.insertSpace(Space(name = folder, colorIdx = spaceIds.size % 5))
                        }
                        val added1 = Regex("ADD_DATE=\"(\\d+)\"", RegexOption.IGNORE_CASE).find(t)?.groupValues?.get(1)?.toLongOrNull()?.times(1000) ?: now
                        val title = plain(m.groupValues[3]).ifBlank { Ingest.domainOf(url) }
                        db.insert(Item(type = ItemType.LINK, title = title, url = url, domain = Ingest.domainOf(url), spaceId = spaceId, created = added1, enriched = false))
                        haveUrls.add(url); added++
                    }
                }
            } }
            Store.reloadNow()
            Report(added, skipped, newSpaces)
        }.getOrElse { Report(0, 0, 0, it.message ?: "Import failed") }
    }

    private fun plain(s: String) = android.text.Html.fromHtml(s, android.text.Html.FROM_HTML_MODE_COMPACT).toString().trim()
}
