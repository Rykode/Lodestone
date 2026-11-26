package app.lodestone.data

/** The orders the Library can use. Pinned items always stay first. */
object Sorting {
    const val NEWEST = 0
    const val OLDEST = 1
    const val TITLE = 2
    const val SITE = 3

    val names = listOf("Newest first", "Oldest first", "Title A to Z", "Site A to Z")

    private fun keyTitle(i: Item) = i.title.trim().lowercase().ifEmpty { i.note.trim().lowercase() }
    private fun keySite(i: Item) = i.domain.trim().lowercase().ifEmpty { "~" }

    fun apply(items: List<Item>, mode: Int): List<Item> {
        val order: Comparator<Item> = when (mode) {
            OLDEST -> compareBy<Item> { it.created }
            TITLE -> compareBy<Item> { keyTitle(it) }.thenByDescending { it.created }
            SITE -> compareBy<Item> { keySite(it) }.thenBy { keyTitle(it) }
            else -> compareByDescending<Item> { it.created }
        }
        // sortedWith is stable, so equal items keep the order they came in.
        return items.sortedWith(compareByDescending<Item> { it.pinned }.then(order))
    }
}
