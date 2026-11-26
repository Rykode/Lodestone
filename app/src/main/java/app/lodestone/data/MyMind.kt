package app.lodestone.data

import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import java.util.zip.ZipInputStream

/**
 * Brings a mymind export into Lodestone.
 *
 * mymind's own help page says an export is a folder: every picture and PDF you saved as its own file, plus a
 * "cards.csv" holding your notes, quotes, bookmark links and highlights. It does not publish the column names, so the
 * reader here finds each column from its header name, and from what the values look like when there is no header.
 * A zip of that folder, or the cards.csv on its own, works too. Everything is read on this phone.
 */
object MyMind {
    class Entry(val name: String, val uri: Uri, val mime: String?)

    /** Which CSV column holds what. A null means the export has no such column. */
    data class Columns(
        val title: Int?, val url: Int?, val notes: List<Int>, val tags: Int?, val created: Int?, val file: Int?, val type: Int?,
        val hasHeader: Boolean,
    )

    // ---------- CSV ----------

    /** A small RFC 4180 reader: quoted cells, doubled quotes, line breaks inside quotes, an optional byte-order mark. */
    fun parseCsv(input: String): List<List<String>> {
        val text = input.removePrefix("﻿")
        val rows = ArrayList<List<String>>()
        var row = ArrayList<String>()
        val cell = StringBuilder()
        var quoted = false
        var i = 0
        while (i < text.length) {
            val ch = text[i]
            if (quoted) {
                if (ch == '"') {
                    if (i + 1 < text.length && text[i + 1] == '"') { cell.append('"'); i++ } else quoted = false
                } else cell.append(ch)
            } else when (ch) {
                '"' -> if (cell.isEmpty()) quoted = true else cell.append(ch)
                ',' -> { row.add(cell.toString()); cell.setLength(0) }
                '\r' -> {}
                '\n' -> { row.add(cell.toString()); cell.setLength(0); if (row.any { it.isNotEmpty() }) rows.add(row); row = ArrayList() }
                else -> cell.append(ch)
            }
            i++
        }
        if (cell.isNotEmpty() || row.isNotEmpty()) { row.add(cell.toString()); if (row.any { it.isNotEmpty() }) rows.add(row) }
        return rows
    }

    private val titleNames = setOf("title", "name", "heading", "card title")
    private val urlNames = setOf("url", "link", "source", "source url", "source_url", "website", "href", "address")
    private val noteNames = setOf("note", "notes", "content", "text", "body", "description", "quote", "highlight", "highlights", "excerpt", "caption", "comment")
    private val tagNames = setOf("tags", "tag", "labels", "label", "keywords")
    private val createdNames = setOf("created", "created_at", "created at", "createdat", "date", "added", "added_at", "saved", "saved_at", "timestamp", "time")
    private val fileNames = setOf("file", "filename", "file name", "file_name", "path", "image", "attachment", "media", "asset")
    private val typeNames = setOf("type", "kind", "card type", "card_type")

    private fun looksLikeUrl(s: String) = s.trim().let { it.startsWith("http://", true) || it.startsWith("https://", true) }

    /** Finds the columns by header name; if no header names are recognised, treats the first row as data and guesses from the values. */
    fun mapColumns(rows: List<List<String>>): Columns {
        val head = rows.firstOrNull().orEmpty().map { it.trim().lowercase() }
        fun find(names: Set<String>) = head.indexOfFirst { it in names }.takeIf { it >= 0 }
        val byName = Columns(
            title = find(titleNames), url = find(urlNames), notes = head.withIndex().filter { it.value in noteNames }.map { it.index },
            tags = find(tagNames), created = find(createdNames), file = find(fileNames), type = find(typeNames), hasHeader = true,
        )
        val named = listOfNotNull(byName.title, byName.url, byName.tags, byName.created, byName.file).size + byName.notes.size
        if (named >= 2 || (named == 1 && rows.size > 1 && head.none { looksLikeUrl(it) })) return byName

        // No usable header: look at the values.
        val width = rows.maxOfOrNull { it.size } ?: 0
        val sample = rows.take(60)
        fun col(i: Int) = sample.mapNotNull { it.getOrNull(i)?.trim()?.takeIf { v -> v.isNotEmpty() } }
        val urlCol = (0 until width).firstOrNull { c -> col(c).let { v -> v.size >= 2 && v.count(::looksLikeUrl) * 10 >= v.size * 6 } }
        val longest = (0 until width).filter { it != urlCol }.maxByOrNull { c -> col(c).maxOfOrNull { it.length } ?: 0 }
        return Columns(title = null, url = urlCol, notes = listOfNotNull(longest), tags = null, created = null, file = null, type = null, hasHeader = false)
    }

    // ---------- values ----------

    fun splitTags(s: String): List<String> =
        s.split(',', ';', '|').map { it.trim().removePrefix("#").trim() }.filter { it.isNotEmpty() && it.length <= 40 }.distinct().take(12)

    /** Accepts ISO dates and times, and Unix seconds or milliseconds. Returns null when it cannot tell. */
    fun parseTime(s: String): Long? {
        val v = s.trim()
        if (v.isEmpty()) return null
        v.toLongOrNull()?.let { n -> return if (n in 1..99_999_999_999L) n * 1000 else if (n >= 100_000_000_000L) n else null }
        return runCatching { java.time.OffsetDateTime.parse(v).toInstant().toEpochMilli() }.getOrNull()
            ?: runCatching { java.time.Instant.parse(v).toEpochMilli() }.getOrNull()
            ?: runCatching { java.time.LocalDateTime.parse(v.replace(' ', 'T')).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() }.getOrNull()
            ?: runCatching { java.time.LocalDate.parse(v.take(10)).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() }.getOrNull()
    }

    /** What one CSV row becomes. */
    sealed interface Card {
        data class Link(val url: String, val title: String, val note: String, val tags: List<String>, val created: Long?) : Card
        data class Note(val title: String, val text: String, val tags: List<String>, val created: Long?) : Card
        data class Media(val file: String, val title: String, val note: String, val tags: List<String>, val created: Long?) : Card
    }

    fun cardOf(row: List<String>, cols: Columns): Card? {
        fun cell(i: Int?) = i?.let { row.getOrNull(it) }?.trim().orEmpty()
        val title = cell(cols.title)
        val notes = cols.notes.map { cell(it) }.filter { it.isNotEmpty() }
        val note = notes.joinToString("\n\n")
        val tags = splitTags(cell(cols.tags))
        val created = parseTime(cell(cols.created))
        val file = cell(cols.file)
        var url = cell(cols.url).let { u -> if (looksLikeUrl(u)) u else Regex("https?://\\S+").find(u)?.value.orEmpty() }
        if (url.isEmpty() && title.isEmpty() && notes.size == 1 && looksLikeUrl(notes[0]) && !notes[0].contains(' ') && !notes[0].contains('\n')) {
            return Card.Link(notes[0], "", "", tags, created)
        }
        if (url.isNotEmpty()) return Card.Link(url.trimEnd(')', ',', '.'), title, note, tags, created)
        if (file.isNotEmpty() && !looksLikeUrl(file)) return Card.Media(file, title, note, tags, created)
        if (title.isEmpty() && note.isEmpty()) return null
        val text = if (note.isNotEmpty()) note else title
        return Card.Note(title.ifEmpty { text.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty().take(80) }, if (note.isEmpty()) title else note, tags, created)
    }

    // ---------- reading the export ----------

    private fun mimeOfName(name: String): String? =
        MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substringAfterLast('.', "").lowercase())

    private fun sourceName(uri: Uri): String {
        var name = ""
        runCatching {
            Store.app.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) name = it.getString(0).orEmpty() }
        }
        return name
    }

    /** Lists every file inside a folder the person picked, going into sub-folders too. */
    private fun listTree(tree: Uri): List<Entry> {
        val cr = Store.app.contentResolver
        val out = ArrayList<Entry>()
        fun walk(docId: String, depth: Int) {
            if (depth > 6) return
            val kids = DocumentsContract.buildChildDocumentsUriUsingTree(tree, docId)
            cr.query(kids, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE), null, null, null)?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getString(0); val name = c.getString(1).orEmpty(); val mime = c.getString(2)
                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) walk(id, depth + 1)
                    else out.add(Entry(name, DocumentsContract.buildDocumentUriUsingTree(tree, id), mime))
                }
            }
        }
        walk(DocumentsContract.getTreeDocumentId(tree), 0)
        return out
    }

    /** Unpacks a zip into the cache so its files can be read like any others. Only the file names are used, never paths. */
    private fun unzip(zip: Uri, into: File): List<Entry> {
        into.mkdirs()
        val out = ArrayList<Entry>()
        val used = HashSet<String>()
        Store.app.contentResolver.openInputStream(zip)?.use { raw ->
            ZipInputStream(raw.buffered()).use { z ->
                while (true) {
                    val e = z.nextEntry ?: break
                    if (e.isDirectory) continue
                    val base = e.name.substringAfterLast('/').substringAfterLast('\\')
                    if (base.isEmpty() || base.startsWith(".") || e.name.contains("__MACOSX")) continue
                    var name = base
                    var n = 1
                    while (!used.add(name.lowercase())) name = base.substringBeforeLast('.') + "-" + (n++) + (if (base.contains('.')) "." + base.substringAfterLast('.') else "")
                    val f = File(into, name)
                    f.outputStream().use { z.copyTo(it) }
                    out.add(Entry(base, Uri.fromFile(f), mimeOfName(base)))
                }
            }
        }
        return out
    }

    /** [uri] is a folder (tree), a zip, or a cards.csv. */
    suspend fun import(uri: Uri, isFolder: Boolean): Backup.Report = withContext(Dispatchers.IO) {
        val scratch = File(Store.app.cacheDir, "mymind-" + UUID.randomUUID())
        try {
            val name = if (isFolder) "" else sourceName(uri)
            val entries: List<Entry> = when {
                isFolder -> listTree(uri)
                name.endsWith(".zip", true) || Store.app.contentResolver.getType(uri)?.contains("zip") == true -> unzip(uri, scratch)
                else -> listOf(Entry(name.ifEmpty { "cards.csv" }, uri, "text/csv"))
            }
            val csvEntry = entries.firstOrNull { it.name.equals("cards.csv", true) } ?: entries.firstOrNull { it.name.endsWith(".csv", true) }
            val media = entries.filter { it !== csvEntry && !it.name.endsWith(".csv", true) && !it.name.endsWith(".json", true) && !it.name.endsWith(".txt", true) && !it.name.endsWith(".html", true) }
            if (csvEntry == null && media.isEmpty()) return@withContext Backup.Report(0, 0, 0, "That doesn't look like a mymind export. Pick the exported folder, a zip of it, or the cards.csv file.")

            val rows = csvEntry?.let { e -> Store.app.contentResolver.openInputStream(e.uri)?.use { parseCsv(it.readBytes().toString(Charsets.UTF_8)) } }.orEmpty()
            val cols = if (rows.isEmpty()) null else mapColumns(rows)
            val data = if (cols == null) emptyList() else if (cols.hasHeader) rows.drop(1) else rows

            val db = Store.database()
            val now = System.currentTimeMillis()
            val spaceId = Store.lock.withLock {
                db.allSpaces().firstOrNull { it.name == "From mymind" }?.id ?: db.insertSpace(Space(name = "From mymind", colorIdx = 2))
            }
            val haveUrls = db.allItems().map { it.url }.filter { it.isNotEmpty() }.toHashSet()
            val haveNotes = db.allItems().filter { it.type == ItemType.NOTE }.map { it.title + "\u0000" + it.note }.toHashSet()
            val byName = media.associateBy { it.name.lowercase() }
            val usedMedia = HashSet<String>()
            var added = 0; var skipped = 0
            val total = data.size + media.size

            for ((n, row) in data.withIndex()) {
                if (n % 25 == 0) Notifier.progress(Notifier.ID_BACKUP, "Importing from mymind", "$added of $total", n, total)
                val card = cols?.let { cardOf(row, it) } ?: continue
                when (card) {
                    is Card.Link -> {
                        if (card.url in haveUrls) { skipped++; continue }
                        val domain = Ingest.domainOf(card.url)
                        Store.lock.withLock {
                            db.insert(Item(type = ItemType.LINK, title = card.title.ifEmpty { domain.ifEmpty { card.url } }, note = card.note, url = card.url, domain = domain,
                                tags = card.tags, spaceId = spaceId, created = card.created ?: now, enriched = false,
                                kind = Embeds.parse(card.url)?.platform?.name?.lowercase().orEmpty()))
                        }
                        haveUrls.add(card.url); added++
                    }
                    is Card.Note -> {
                        val key = card.title + "\u0000" + card.text
                        if (key in haveNotes) { skipped++; continue }
                        Store.lock.withLock { db.insert(Item(type = ItemType.NOTE, title = card.title, note = card.text, tags = card.tags, spaceId = spaceId, created = card.created ?: now)) }
                        haveNotes.add(key); added++
                    }
                    is Card.Media -> {
                        val e = byName[card.file.substringAfterLast('/').substringAfterLast('\\').lowercase()] ?: run { skipped++; null } ?: continue
                        if (!usedMedia.add(e.name.lowercase())) continue
                        val id = Ingest.saveMedia(e.uri, card.tags, spaceId, e.mime ?: mimeOfName(e.name))
                        if (id == 0L) { skipped++; continue }
                        if (card.title.isNotEmpty() || card.note.isNotEmpty() || card.created != null) {
                            Store.modifyNow(id) { it.copy(title = card.title.ifEmpty { it.title }, note = card.note, created = card.created ?: it.created) }
                        }
                        added++
                    }
                }
            }
            // Pictures and PDFs the csv does not mention still came from the person's mind, so they come in too.
            for ((n, e) in media.withIndex()) {
                if (e.name.lowercase() in usedMedia) continue
                if ((data.size + n) % 10 == 0) Notifier.progress(Notifier.ID_BACKUP, "Importing from mymind", "$added of $total", data.size + n, total)
                val id = Ingest.saveMedia(e.uri, emptyList(), spaceId, e.mime ?: mimeOfName(e.name))
                if (id == 0L) skipped++ else added++
            }
            Store.reloadNow()
            Ingest.retryEnrichment()
            Backup.Report(added, skipped, 1)
        } catch (t: Throwable) {
            Backup.Report(0, 0, 0, t.message ?: "Import failed")
        } finally {
            scratch.deleteRecursively()
        }
    }
}
