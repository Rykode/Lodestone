package app.lodestone.data

import java.text.Normalizer

/** Case- and accent-insensitive matching for Latin text: "Über", "über" and "uber" all compare equal. */
object SearchText {
    private val marks = Regex("\\p{Mn}+")

    fun norm(s: String): String {
        if (s.isEmpty()) return s
        val folded = Normalizer.normalize(s, Normalizer.Form.NFD).replace(marks, "").lowercase()
        // Letters that don't decompose into a base letter plus a mark.
        val sb = StringBuilder(folded.length)
        for (ch in folded) when (ch) {
            'ß' -> sb.append("ss")
            'æ' -> sb.append("ae")
            'œ' -> sb.append("oe")
            'ø' -> sb.append('o')
            'đ' -> sb.append('d')
            'ł' -> sb.append('l')
            'ı' -> sb.append('i')
            else -> sb.append(ch)
        }
        return sb.toString()
    }

    /** True if [haystack] contains [token], ignoring case and accents. */
    fun contains(haystack: String, token: String): Boolean = norm(haystack).contains(norm(token))
}
