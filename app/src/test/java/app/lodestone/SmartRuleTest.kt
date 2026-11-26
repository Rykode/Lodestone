package app.lodestone

import app.lodestone.data.Item
import app.lodestone.data.ItemType
import app.lodestone.data.SearchText
import app.lodestone.data.SmartRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartRuleTest {
    private val now = 1_000_000_000_000L
    private fun item(id: Long, type: ItemType = ItemType.LINK, title: String = "", tags: List<String> = emptyList(), kind: String = "", ageDays: Int = 0, deleted: Long = 0) =
        Item(id = id, type = type, title = title, tags = tags, kind = kind, created = now - ageDays * 86_400_000L, deleted = deleted,
            key = SearchText.norm("$title ${tags.joinToString(" ")}"))

    @Test fun emptyRuleIsEmptyAndRoundTrips() {
        assertTrue(SmartRule().isEmpty)
        val r = SmartRule(setOf("LINK", "NOTE"), setOf("recipe"), listOf("dinner"), listOf("pasta"), 180)
        assertEquals(r, SmartRule.parse(r.toJson()))
        assertTrue(SmartRule.parse("not json").isEmpty)
    }

    @Test fun everyPartMustMatch() {
        val r = SmartRule(types = setOf("LINK"), words = listOf("pasta"), days = 30)
        assertTrue(r.matches(item(1, title = "Lemon pasta"), emptyMap(), now))
        assertFalse(r.matches(item(2, ItemType.NOTE, title = "Lemon pasta"), emptyMap(), now))
        assertFalse(r.matches(item(3, title = "Lemon pasta", ageDays = 90), emptyMap(), now))
        assertFalse(r.matches(item(4, title = "Soup"), emptyMap(), now))
    }

    @Test fun wordsAlsoMatchSavedPageText() {
        val r = SmartRule(words = listOf("pasta"))
        assertTrue(r.matches(item(5, title = "Dinner ideas"), mapOf("pasta" to setOf(5L)), now))
        assertFalse(r.matches(item(6, title = "Dinner ideas"), mapOf("pasta" to setOf(5L)), now))
    }

    @Test fun accentsAndCaseAreIgnored() {
        assertTrue(SmartRule(words = listOf("CAFE")).matches(item(7, title = "Le Café"), emptyMap(), now))
        assertTrue(SmartRule(tags = listOf("#Crème")).matches(item(8, tags = listOf("creme")), emptyMap(), now))
    }

    @Test fun deletedItemsNeverMatchAndKindsFilter() {
        assertFalse(SmartRule(types = setOf("LINK")).matches(item(9, deleted = 5), emptyMap(), now))
        assertTrue(SmartRule(kinds = setOf("youtube")).matches(item(10, kind = "youtube"), emptyMap(), now))
        assertFalse(SmartRule(kinds = setOf("youtube")).matches(item(11, kind = "instagram"), emptyMap(), now))
    }
}
