package app.lodestone

import app.lodestone.data.SearchText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchTextTest {
    @Test
    fun lowercaseQueryMatchesCapitalAccentedText() {
        assertTrue(SearchText.contains("Über die Brücke", "über"))
    }

    @Test
    fun plainAsciiQueryMatchesAccentedText() {
        assertTrue(SearchText.contains("Über die Brücke", "uber"))
        assertTrue(SearchText.contains("Café Crème", "cafe creme"))
        assertTrue(SearchText.contains("Zażółć gęślą jaźń", "zazolc"))
    }

    @Test
    fun accentedQueryMatchesPlainText() {
        assertTrue(SearchText.contains("Uber the bridge", "über"))
    }

    @Test
    fun foldsLettersThatDoNotDecompose() {
        assertEquals("strasse", SearchText.norm("Straße"))
        assertEquals("oslo", SearchText.norm("Øslo"))
    }

    @Test
    fun unrelatedWordsStillDoNotMatch() {
        assertFalse(SearchText.contains("Über die Brücke", "bruder"))
        assertFalse(SearchText.contains("Hello", "über"))
    }
}
