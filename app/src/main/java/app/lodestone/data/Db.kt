package app.lodestone.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class Db(ctx: Context) : SQLiteOpenHelper(ctx, "lodestone.db", null, 3) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE items(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                type TEXT NOT NULL, title TEXT, note TEXT, url TEXT, file TEXT, thumb TEXT,
                domain TEXT, tags TEXT, space_id INTEGER DEFAULT 0, pinned INTEGER DEFAULT 0,
                created INTEGER, deleted INTEGER DEFAULT 0, color INTEGER DEFAULT 0,
                text TEXT, mime TEXT, enriched INTEGER DEFAULT 1, aspect REAL DEFAULT 0, text_norm TEXT,
                kind TEXT DEFAULT '', extra TEXT DEFAULT '', feat TEXT DEFAULT '', meta_norm TEXT DEFAULT '')"""
        )
        db.execSQL("CREATE TABLE spaces(id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, color_idx INTEGER DEFAULT 0, created INTEGER, rule TEXT DEFAULT '')")
        db.execSQL("CREATE INDEX idx_items_deleted ON items(deleted, pinned DESC, created DESC)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            // Version 2: a lower-case, accent-free copy of the page text, so "uber" finds "Über".
            db.execSQL("ALTER TABLE items ADD COLUMN text_norm TEXT")
            db.beginTransaction()
            try {
                val rows = db.rawQuery("SELECT id, text FROM items WHERE text IS NOT NULL AND text != ''", null).use { c ->
                    buildList { while (c.moveToNext()) add(c.getLong(0) to c.getString(1)) }
                }
                for ((id, text) in rows) db.execSQL("UPDATE items SET text_norm=? WHERE id=?", arrayOf<Any?>(SearchText.norm(text), id))
                db.setTransactionSuccessful()
            } finally { db.endTransaction() }
        }
        if (oldVersion < 3) {
            // Version 3: card kinds, card facts, visual fingerprints, fast search keys, smart-space rules and an index.
            db.execSQL("ALTER TABLE items ADD COLUMN kind TEXT DEFAULT ''")
            db.execSQL("ALTER TABLE items ADD COLUMN extra TEXT DEFAULT ''")
            db.execSQL("ALTER TABLE items ADD COLUMN feat TEXT DEFAULT ''")
            db.execSQL("ALTER TABLE items ADD COLUMN meta_norm TEXT DEFAULT ''")
            db.execSQL("ALTER TABLE spaces ADD COLUMN rule TEXT DEFAULT ''")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_items_deleted ON items(deleted, pinned DESC, created DESC)")
            db.beginTransaction()
            try {
                val rows = db.rawQuery("SELECT id,title,note,url,tags,domain FROM items", null).use { c ->
                    buildList { while (c.moveToNext()) add(arrayOf<Any?>(c.getLong(0), c.getString(1), c.getString(2), c.getString(3), c.getString(4), c.getString(5))) }
                }
                for (r in rows) {
                    val key = SearchText.norm(listOf(r[1], r[2], r[3], (r[4] as String?)?.replace('\n', ' '), r[5]).joinToString(" ") { (it as String?).orEmpty() })
                    db.execSQL("UPDATE items SET meta_norm=? WHERE id=?", arrayOf<Any?>(key, r[0]))
                }
                db.setTransactionSuccessful()
            } finally { db.endTransaction() }
        }
    }

    private fun values(i: Item) = ContentValues().apply {
        put("type", i.type.name); put("title", i.title); put("note", i.note); put("url", i.url)
        put("file", i.file); put("thumb", i.thumb); put("domain", i.domain)
        put("tags", i.tags.joinToString("\n")); put("space_id", i.spaceId)
        put("pinned", if (i.pinned) 1 else 0); put("created", i.created); put("deleted", i.deleted)
        put("color", i.color); put("text", i.text); put("text_norm", SearchText.norm(i.text)); put("mime", i.mime)
        put("enriched", if (i.enriched) 1 else 0); put("aspect", i.aspect)
        put("kind", i.kind); put("extra", i.extra); put("feat", i.feat)
        put("meta_norm", SearchText.norm(listOf(i.title, i.note, i.url, i.tags.joinToString(" "), i.domain, i.kind).joinToString(" ")))
    }

    private fun Cursor.col(name: String) = getColumnIndexOrThrow(name)

    private fun Cursor.toItem(): Item {
        val keyCol = getColumnIndex("mkey").takeIf { it >= 0 } ?: getColumnIndex("meta_norm")
        return Item(
            id = getLong(col("id")),
            type = runCatching { ItemType.valueOf(getString(col("type"))) }.getOrDefault(ItemType.NOTE),
            title = getString(col("title")).orEmpty(),
            note = getString(col("note")).orEmpty(),
            url = getString(col("url")).orEmpty(),
            file = getString(col("file")).orEmpty(),
            thumb = getString(col("thumb")).orEmpty(),
            domain = getString(col("domain")).orEmpty(),
            tags = getString(col("tags")).orEmpty().split("\n").filter { it.isNotBlank() },
            spaceId = getLong(col("space_id")),
            pinned = getInt(col("pinned")) == 1,
            created = getLong(col("created")),
            deleted = getLong(col("deleted")),
            color = getInt(col("color")),
            text = getString(col("text")).orEmpty(),
            mime = getString(col("mime")).orEmpty(),
            enriched = getInt(col("enriched")) == 1,
            aspect = getFloat(col("aspect")),
            kind = getString(col("kind")).orEmpty(),
            extra = getString(col("extra")).orEmpty(),
            feat = getString(col("feat")).orEmpty(),
            key = if (keyCol >= 0) getString(keyCol).orEmpty() else "",
        )
    }

    /** Every item without its (large) page text, which is loaded on demand with [getItem] or [textOf]. */
    fun allItems(): List<Item> = readableDatabase.rawQuery(
        "SELECT id,type,title,note,url,file,thumb,domain,tags,space_id,pinned,created,deleted,color,'' AS text,mime,enriched,aspect,kind,extra,feat,meta_norm AS mkey " +
            "FROM items ORDER BY pinned DESC, created DESC", null
    ).use { c -> buildList(c.count) { while (c.moveToNext()) add(c.toItem()) } }

    /** Full row including page text. */
    fun getItem(id: Long): Item? = readableDatabase.rawQuery("SELECT * FROM items WHERE id=?", arrayOf(id.toString())).use { c -> if (c.moveToFirst()) c.toItem() else null }

    fun textOf(id: Long): String = readableDatabase.rawQuery("SELECT text FROM items WHERE id=?", arrayOf(id.toString())).use { c -> if (c.moveToFirst()) c.getString(0).orEmpty() else "" }

    /** Ids of live items whose saved text contains [token], ignoring case and accents. */
    fun idsWithText(token: String): Set<Long> {
        val like = "%" + SearchText.norm(token).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"
        return readableDatabase.rawQuery("SELECT id FROM items WHERE deleted=0 AND text_norm LIKE ? ESCAPE '\\'", arrayOf(like)).use { c ->
            buildSet { while (c.moveToNext()) add(c.getLong(0)) }
        }
    }

    fun insert(i: Item): Long = writableDatabase.insert("items", null, values(i))

    fun update(i: Item) {
        writableDatabase.update("items", values(i), "id=?", arrayOf(i.id.toString()))
    }

    fun delete(id: Long) {
        writableDatabase.delete("items", "id=?", arrayOf(id.toString()))
    }

    fun allSpaces(): List<Space> = readableDatabase.rawQuery("SELECT * FROM spaces ORDER BY created ASC", null).use { c ->
        buildList {
            while (c.moveToNext()) add(
                Space(
                    c.getLong(c.getColumnIndexOrThrow("id")),
                    c.getString(c.getColumnIndexOrThrow("name")),
                    c.getInt(c.getColumnIndexOrThrow("color_idx")),
                    c.getLong(c.getColumnIndexOrThrow("created")),
                    c.getString(c.getColumnIndexOrThrow("rule")).orEmpty(),
                )
            )
        }
    }

    fun insertSpace(s: Space): Long = writableDatabase.insert("spaces", null, ContentValues().apply {
        put("name", s.name); put("color_idx", s.colorIdx); put("created", s.created); put("rule", s.rule)
    })

    fun updateSpace(s: Space) {
        writableDatabase.update("spaces", ContentValues().apply { put("name", s.name); put("color_idx", s.colorIdx); put("rule", s.rule) }, "id=?", arrayOf(s.id.toString()))
    }

    fun deleteSpace(id: Long) {
        writableDatabase.delete("spaces", "id=?", arrayOf(id.toString()))
        writableDatabase.execSQL("UPDATE items SET space_id=0 WHERE space_id=?", arrayOf<Any?>(id))
    }
}
