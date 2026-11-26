package app.lodestone.data

import com.googlecode.tesseract.android.TessBaseAPI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

/**
 * Reads the words inside pictures and screenshots so they can be searched.
 * It uses Tesseract, which runs entirely on this phone: nothing is uploaded and nothing is reported anywhere.
 * It reads printed text well; handwriting is not supported.
 */
object Ocr {
    private val lock = Mutex()
    private var api: TessBaseAPI? = null

    private fun engine(): TessBaseAPI {
        api?.let { return it }
        val root = File(Store.app.filesDir, "ocr")
        val tessdata = File(root, "tessdata").apply { mkdirs() }
        val model = File(tessdata, "eng.traineddata")
        if (!model.exists() || model.length() < 1_000_000) {
            Store.app.assets.open("tessdata/eng.traineddata").use { input -> model.outputStream().use { input.copyTo(it) } }
        }
        val t = TessBaseAPI()
        check(t.init(root.absolutePath, "eng")) { "Text reader could not start" }
        t.pageSegMode = TessBaseAPI.PageSegMode.PSM_AUTO
        api = t
        return t
    }

    /** Words found in the picture, or an empty string. One picture at a time. */
    suspend fun read(file: File): String {
        val bmp = Ingest.decodeSampled(file, 2000)?.let { Ingest.rotateByExif(file, it) } ?: return ""
        return readBitmap(bmp)
    }

    suspend fun readBitmap(bmp: android.graphics.Bitmap): String = withContext(Dispatchers.Default) {
        lock.withLock {
            val t = engine()
            t.setImage(bmp)
            val raw = t.utF8Text.orEmpty()
            t.clear()
            tidy(raw)
        }
    }

    /** Drops the junk lines Tesseract invents when it looks at photos, keeping lines that look like words. */
    fun tidy(raw: String): String {
        val lines = raw.lines().map { it.trim() }.filter { line ->
            if (line.length < 3) return@filter false
            val good = line.count { it.isLetterOrDigit() }
            good >= 3 && good.toFloat() / line.length >= 0.55f
        }
        val text = lines.joinToString("\n").replace(Regex("\n{2,}"), "\n")
        return if (text.count { it.isLetterOrDigit() } >= 6) text else ""
    }

    fun isDone(item: Item): Boolean = item.extra.contains("\"ocr\"")

    private fun markDone(extra: String): String =
        runCatching { (if (extra.isBlank()) JSONObject() else JSONObject(extra)).put("ocr", 1).toString() }.getOrDefault("{\"ocr\":1}")

    /** Reads one saved picture and stores what it says, so search can find it. */
    suspend fun process(id: Long) {
        val item = Store.get(id) ?: return
        val isPdf = item.type == ItemType.FILE && item.mime == "application/pdf"
        if ((item.type != ItemType.IMAGE && !isPdf) || item.file.isEmpty()) return
        val file = File(Store.mediaDir, item.file)
        val text = runCatching {
            if (!isPdf) read(file)
            else {
                val out = StringBuilder()
                for (page in 0 until minOf(Pdfs.pageCount(file), 6)) {
                    Pdfs.render(file, page, 1500)?.let { out.append(readBitmap(it)).append('\n') }
                }
                out.toString().trim()
            }
        }.getOrDefault("")
        Store.modifyNow(id) { cur -> cur.copy(text = if (cur.text.isEmpty()) text else cur.text, extra = markDone(cur.extra)) }
    }

    /** Reads every saved picture that has not been read yet, one after another, with a progress notification. */
    suspend fun backfill() {
        val pending = Store.items.filter { (it.type == ItemType.IMAGE || (it.type == ItemType.FILE && it.mime == "application/pdf")) && it.deleted == 0L && !isDone(it) }
        if (pending.isEmpty()) return
        pending.forEachIndexed { n, item ->
            if (pending.size > 2) Notifier.progress(Notifier.ID_OCR, "Reading text in your pictures", "${n + 1} of ${pending.size}", n, pending.size)
            process(item.id)
        }
        if (pending.size > 2) Notifier.finish(Notifier.ID_OCR, "Pictures are searchable", "${pending.size} pictures read")
    }
}
