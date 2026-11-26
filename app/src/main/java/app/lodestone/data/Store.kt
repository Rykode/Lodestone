package app.lodestone.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/** Something shared into Lodestone from another app, waiting to be kept. */
data class Pending(val text: String = "", val uris: List<android.net.Uri> = emptyList())

/** Single source of truth for items, spaces and preferences. Everything lives on this phone. */
object Store {
    lateinit var app: Context
        private set
    private lateinit var db: Db
    lateinit var prefs: SharedPreferences
        private set

    /** A failed background job must never take the app down. */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, e -> Log.w("Lodestone", "background job failed", e) })

    /** Serialises every read-modify-write on a row so concurrent edits never overwrite each other. */
    private val writeLock = Mutex()

    var items by mutableStateOf<List<Item>>(emptyList())
        private set
    /** Items not in Recently deleted, and the ones that are. Computed once per reload, not on every read. */
    var live by mutableStateOf<List<Item>>(emptyList())
        private set
    var trashed by mutableStateOf<List<Item>>(emptyList())
        private set
    private var byId: Map<Long, Item> = emptyMap()
    var spaces by mutableStateOf<List<Space>>(emptyList())
        private set
    var loaded by mutableStateOf(false)
        private set

    // Preferences (observable)
    var schemeIdx by mutableStateOf(0)
    var mode by mutableStateOf(0) // 0 system, 1 light, 2 dark
    var onboarded by mutableStateOf(true)
    /** Pure black backgrounds in dark mode. */
    var oled by mutableStateOf(false)
    /** 0 small, 1 default, 2 large, 3 largest. */
    var textSize by mutableStateOf(1)
    /** 0 sharp, 1 default, 2 round. */
    var corners by mutableStateOf(1)
    /** True: every card has the same corners. False: each card has its own. */
    var uniformCards by mutableStateOf(false)
    /** Columns in the library grid, 1 to 3. */
    var gridCols by mutableStateOf(2)
    var reduceMotion by mutableStateOf(false)
    /** See [Sorting]. */
    var sortMode by mutableStateOf(0)
    /** True: one compact row per item instead of the card grid. */
    var listView by mutableStateOf(false)
    var haptics by mutableStateOf(true)
    var tabLabels by mutableStateOf(false)
    /** True: sharing to Lodestone saves at once and returns to the other app. False: open the Save sheet first. */
    var quickShare by mutableStateOf(true)

    var pending by mutableStateOf<Pending?>(null)

    val mediaDir: File get() = File(app.filesDir, "media").apply { mkdirs() }
    val thumbDir: File get() = File(app.filesDir, "thumbs").apply { mkdirs() }

    fun init(ctx: Context) {
        app = ctx.applicationContext
        db = Db(app)
        prefs = app.getSharedPreferences("lodestone", Context.MODE_PRIVATE)
        schemeIdx = prefs.getInt("scheme", 0)
        mode = prefs.getInt("mode", 0)
        onboarded = prefs.getBoolean("onboarded", false)
        quickShare = prefs.getBoolean("quickShare", true)
        oled = prefs.getBoolean("oled", false)
        textSize = prefs.getInt("textSize", 1).coerceIn(0, 3)
        corners = prefs.getInt("corners", 1).coerceIn(0, 2)
        uniformCards = prefs.getBoolean("uniformCards", false)
        gridCols = prefs.getInt("gridCols", 2).coerceIn(1, 3)
        reduceMotion = prefs.getBoolean("reduceMotion", false)
        sortMode = prefs.getInt("sortMode", 0).coerceIn(0, Sorting.names.lastIndex)
        listView = prefs.getBoolean("listView", false)
        haptics = prefs.getBoolean("haptics", true)
        tabLabels = prefs.getBoolean("tabLabels", false)
        scope.launch {
            purgeOldTrash()
            reload()
            Ingest.retryEnrichment()
            Ocr.backfill()
        }
    }

    fun savePrefs() {
        prefs.edit().putInt("scheme", schemeIdx).putInt("mode", mode).putBoolean("onboarded", onboarded).putBoolean("quickShare", quickShare)
            .putBoolean("oled", oled).putInt("textSize", textSize).putInt("corners", corners).putBoolean("uniformCards", uniformCards)
            .putInt("gridCols", gridCols).putBoolean("reduceMotion", reduceMotion).putInt("sortMode", sortMode).putBoolean("listView", listView).putBoolean("haptics", haptics).putBoolean("tabLabels", tabLabels).apply()
    }

    private val reloadLock = Mutex()

    /** Read and publish under one lock, so a slower earlier reload can never overwrite a newer one. */
    private suspend fun reload() = reloadLock.withLock {
        val i = db.allItems()
        val s = db.allSpaces()
        val l = i.filter { it.deleted == 0L }
        val t = i.filter { it.deleted != 0L }.sortedByDescending { it.deleted }
        val m = i.associateBy { it.id }
        withContext(Dispatchers.Main) { items = i; live = l; trashed = t; byId = m; spaces = s; loaded = true }
    }

    private fun purgeOldTrash() {
        val cutoff = System.currentTimeMillis() - 30L * 24 * 3600 * 1000
        deleteRows(db.allItems().filter { it.deleted in 1 until cutoff })
    }

    /** Removes rows and their files, unless another row still points at the same file. */
    private fun deleteRows(rows: List<Item>) {
        if (rows.isEmpty()) return
        rows.forEach { db.delete(it.id) }
        val rest = db.allItems()
        val files = rest.map { it.file }.toHashSet()
        val thumbs = rest.map { it.thumb }.toHashSet()
        rows.forEach { i ->
            if (i.file.isNotEmpty() && i.file !in files) File(mediaDir, i.file).delete()
            if (i.thumb.isNotEmpty() && i.thumb !in thumbs) File(thumbDir, i.thumb).delete()
        }
    }

    /** True if some item still points at this thumbnail file. */
    internal fun isThumbUsed(name: String): Boolean = db.allItems().any { it.thumb == name }

    fun get(id: Long): Item? = byId[id]

    suspend fun insert(i: Item): Long = withContext(Dispatchers.IO) { writeLock.withLock { db.insert(i) }.also { reload() } }

    /** Page text for the reader, loaded only when an item is opened. */
    suspend fun textOf(id: Long): String = withContext(Dispatchers.IO) { db.textOf(id) }

    suspend fun idsWithText(token: String): Set<Long> = withContext(Dispatchers.IO) { db.idsWithText(token) }

    /** Atomically changes the latest stored version of one row. */
    suspend fun modifyNow(id: Long, change: (Item) -> Item) = withContext(Dispatchers.IO) {
        writeLock.withLock { db.getItem(id)?.let { db.update(change(it)) } }
        reload()
    }

    fun edit(id: Long, change: (Item) -> Item) { scope.launch { modifyNow(id, change) } }

    private fun editAll(ids: Collection<Long>, change: (Item) -> Item) {
        scope.launch {
            writeLock.withLock { ids.forEach { id -> db.getItem(id)?.let { db.update(change(it)) } } }
            reload()
        }
    }

    fun trash(ids: Collection<Long>) { val now = System.currentTimeMillis(); editAll(ids) { it.copy(deleted = now, pinned = false) } }
    fun restore(ids: Collection<Long>) = editAll(ids) { it.copy(deleted = 0) }
    fun setPinned(ids: Collection<Long>, pinned: Boolean) = editAll(ids) { it.copy(pinned = pinned) }
    fun moveToSpace(ids: Collection<Long>, spaceId: Long) = editAll(ids) { it.copy(spaceId = spaceId) }

    fun deleteForever(ids: Collection<Long>) {
        scope.launch {
            writeLock.withLock { deleteRows(ids.mapNotNull { db.getItem(it) }) }
            reload()
        }
    }

    fun addSpace(name: String, colorIdx: Int = 0, rule: String = "", then: (Long) -> Unit = {}) {
        scope.launch { val id = writeLock.withLock { db.insertSpace(Space(name = name.trim(), colorIdx = colorIdx, rule = rule)) }; reload(); withContext(Dispatchers.Main) { then(id) } }
    }

    fun saveSpace(s: Space) { scope.launch { writeLock.withLock { db.updateSpace(s) }; reload() } }

    fun renameSpace(s: Space, name: String) { scope.launch { writeLock.withLock { db.updateSpace(s.copy(name = name.trim())) }; reload() } }

    fun deleteSpace(id: Long) { scope.launch { writeLock.withLock { db.deleteSpace(id) }; reload() } }

    /** Direct database access for backup and import. */
    internal fun database() = db
    internal val lock get() = writeLock
    internal suspend fun reloadNow() = reload()
}
