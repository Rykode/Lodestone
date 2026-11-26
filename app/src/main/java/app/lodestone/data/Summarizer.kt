package app.lodestone.data

import kotlin.math.sqrt

/**
 * A short extract: the few sentences of a page that use its most repeated important words.
 * It picks sentences that are already there, it does not write anything new, so the app calls it "key sentences".
 */
object Summarizer {
    private val stop = ("the a an and or but if then than so of to in on at by for with from as is are was were be been being it its this that these those " +
        "he she they we you i his her their our your not no do does did have has had will would can could should may might also just more most very about into over after before " +
        "which who whom what when where why how there here one two than such only other some any all each many much own same up out off").split(' ').toSet()

    fun summarize(text: String, sentences: Int = 3): List<String> {
        val parts = text.replace('\n', ' ').split(Regex("(?<=[.!?])\\s+(?=[A-Z0-9\"“'])"))
            .map { it.trim() }.filter { it.length in 40..320 }
        if (parts.size < 6) return emptyList()
        fun tokens(s: String) = s.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.length > 2 && it !in stop }
        val freq = HashMap<String, Int>()
        parts.forEach { p -> tokens(p).forEach { freq[it] = (freq[it] ?: 0) + 1 }}
        val scored = parts.mapIndexed { i, p ->
            val t = tokens(p)
            val score = if (t.isEmpty()) 0.0 else t.sumOf { (freq[it] ?: 0).toDouble() } / sqrt(t.size.toDouble())
            Triple(i, p, score * (if (i == 0) 1.15 else 1.0))
        }
        return scored.sortedByDescending { it.third }.take(sentences).sortedBy { it.first }.map { it.second }
    }
}
