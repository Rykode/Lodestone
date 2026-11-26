package app.lodestone.data

import androidx.compose.runtime.Immutable

enum class ItemType { LINK, IMAGE, VIDEO, NOTE, FILE }

/** Immutable so Compose can skip redrawing a card whose item did not change. */
@Immutable
data class Item(
    val id: Long = 0,
    val type: ItemType,
    val title: String = "",
    val note: String = "",
    val url: String = "",
    /** File name inside filesDir/media, for images, videos and files. */
    val file: String = "",
    /** File name inside filesDir/thumbs. */
    val thumb: String = "",
    val domain: String = "",
    val tags: List<String> = emptyList(),
    val spaceId: Long = 0,
    val pinned: Boolean = false,
    val created: Long = System.currentTimeMillis(),
    /** 0 when not deleted, otherwise the time it moved to Recently deleted. */
    val deleted: Long = 0,
    /** Dominant colour (ARGB) of the image or preview, 0 when unknown. */
    val color: Int = 0,
    /** Readable page text, or text read from an image, kept for offline reading and search. */
    val text: String = "",
    val mime: String = "",
    /** True once the page preview has been fetched (or does not apply). */
    val enriched: Boolean = true,
    /** Height divided by width of the preview, used for the masonry grid. */
    val aspect: Float = 0f,
    /** What the card is: "youtube", "instagram", ... for embeds, or "article", "product", "book", "recipe", "pdf". */
    val kind: String = "",
    /** Small JSON object with facts found for the card, for example {"author":"..","price":".."}. */
    val extra: String = "",
    /** Visual fingerprint used to find things that look alike. */
    val feat: String = "",
    /** Lower-case, accent-free text of the title, note, address and tags, for fast search. Never stored by callers. */
    val key: String = "",
)

@Immutable
data class Space(
    val id: Long = 0,
    val name: String,
    val colorIdx: Int = 0,
    val created: Long = System.currentTimeMillis(),
    /** Empty for a normal space. For a Smart Space, a JSON object of rules that fills it automatically. */
    val rule: String = "",
)
