package app.lodestone.data

import kotlin.math.sqrt

/**
 * Finds things in your library that go with an item. It uses only what Lodestone really knows:
 * shared tags, the same kind of card, words in the title, and how close the main colors of the pictures are.
 */
object Related {
    private fun words(s: String): Set<String> = SearchText.norm(s).split(Regex("[^a-z0-9]+")).filter { it.length > 3 }.toSet()

    private fun colorSimilarity(a: Int, b: Int): Double {
        if (a == 0 || b == 0) return 0.0
        val dr = ((a shr 16) and 0xFF) - ((b shr 16) and 0xFF)
        val dg = ((a shr 8) and 0xFF) - ((b shr 8) and 0xFF)
        val db = (a and 0xFF) - (b and 0xFF)
        return 1.0 - sqrt((dr * dr + dg * dg + db * db).toDouble()) / 441.7
    }

    fun score(base: Item, other: Item): Double {
        val baseTags = base.tags.map { SearchText.norm(it) }.toSet()
        val shared = other.tags.map { SearchText.norm(it) }.count { it in baseTags }
        val a = words(base.title); val b = words(other.title)
        val jaccard = if (a.isEmpty() || b.isEmpty()) 0.0 else a.intersect(b).size.toDouble() / a.union(b).size
        var s = shared * 3.0 + jaccard * 3.0
        if (base.kind.isNotEmpty() && base.kind == other.kind) s += 1.0
        if (base.type == other.type) s += 0.3
        // Color only counts between pictures that are properly close, so unrelated photos do not pair up.
        val cs = colorSimilarity(base.color, other.color)
        if (cs > 0.82) s += (cs - 0.82) * 10.0
        return s
    }

    /** Best matches first. Anything that does not really go with the item is left out. */
    fun top(base: Item, all: List<Item>, n: Int, minScore: Double = 1.2): List<Item> =
        all.asSequence().filter { it.id != base.id && it.deleted == 0L }
            .map { it to score(base, it) }.filter { it.second >= minScore }
            .sortedByDescending { it.second }.take(n).map { it.first }.toList()
}
