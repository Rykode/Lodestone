package app.lodestone

import app.lodestone.data.CardKinds
import app.lodestone.data.Item
import app.lodestone.data.ItemType
import app.lodestone.data.Related
import app.lodestone.data.Summarizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CardKindsTest {
    private fun page(ld: String, head: String = "") = "<html><head>$head<script type=\"application/ld+json\">$ld</script></head><body></body></html>"

    @Test fun recipeFromSchemaOrg() {
        val f = CardKinds.detect(page("""{"@context":"https://schema.org","@type":"Recipe","name":"Pasta","totalTime":"PT1H20M","recipeYield":"4 servings","recipeIngredient":["a","b","c"],"author":{"@type":"Person","name":"Ana"},"keywords":"pasta, dinner, quick"}"""))!!
        assertEquals("recipe", f.kind)
        assertEquals("1 h 20 min", f.facts["time"])
        assertEquals("3", f.facts["ingredients"])
        assertEquals("Ana", f.facts["author"])
        assertEquals(listOf("pasta", "dinner", "quick"), f.keywords)
    }

    @Test fun productPriceInsideGraph() {
        val f = CardKinds.detect(page("""{"@graph":[{"@type":"WebSite"},{"@type":"Product","name":"Lamp","brand":{"@type":"Brand","name":"Lumo"},"offers":{"@type":"Offer","price":"148.00","priceCurrency":"USD"}}]}"""))!!
        assertEquals("product", f.kind)
        assertEquals("$148", f.facts["price"])
        assertEquals("Lumo", f.facts["brand"])
    }

    @Test fun bookAndArticleAndOpenGraph() {
        assertEquals("book", CardKinds.detect(page("""{"@type":"Book","author":"M. Okafor"}"""))!!.kind)
        assertEquals("article", CardKinds.detect(page("""{"@type":["NewsArticle"],"author":[{"name":"Sam"}]}"""))!!.facts.let { "article" })
        val og = CardKinds.detect("<meta property=\"og:type\" content=\"product\"><meta property=\"product:price:amount\" content=\"19.99\"><meta property=\"product:price:currency\" content=\"EUR\">")!!
        assertEquals("€19.99", og.facts["price"])
    }

    @Test fun pagesThatDoNotSayStayPlainAndBrokenDataIsSafe() {
        assertNull(CardKinds.detect("<html><body>hello</body></html>"))
        assertNull(CardKinds.detect(page("{ this is not json")))
    }

    private fun item(id: Long, title: String, tags: List<String> = emptyList(), kind: String = "", color: Int = 0) =
        Item(id = id, type = ItemType.LINK, title = title, tags = tags, kind = kind, color = color)

    @Test fun relatedItemsUseRealSignalsAndSkipUnrelated() {
        val base = item(1, "Lemon pasta with garlic", listOf("dinner"), "recipe")
        val all = listOf(base, item(2, "Garlic pasta salad", listOf("dinner"), "recipe"), item(3, "Tax forms 2026", listOf("admin")), item(4, "Quick dinner ideas", listOf("dinner")))
        val top = Related.top(base, all, 5)
        assertEquals(listOf(2L, 4L), top.map { it.id })
        assertTrue(Related.top(item(9, "Unique thing"), all, 5).isEmpty())
    }

    @Test fun closeColorsOnlyHelpWhenVeryClose() {
        val warm = 0xFFE08040.toInt(); val warm2 = 0xFFE28444.toInt(); val blue = 0xFF2040E0.toInt()
        assertTrue(Related.score(item(1, "aaa", color = warm), item(2, "bbb", color = warm2)) > Related.score(item(1, "aaa", color = warm), item(3, "bbb", color = blue)))
    }

    @Test fun summaryPicksRealSentencesInOrder() {
        val text = (1..10).joinToString(" ") { i -> if (i % 3 == 0) "The magnet attracts iron and the magnet field shapes every compass needle around it $i." else "Weather reports said the afternoon would stay mild with light cloud over the valley number $i." }
        val s = Summarizer.summarize(text, 3)
        assertEquals(3, s.size)
        assertTrue(s.all { text.contains(it) })
        assertTrue(Summarizer.summarize("Too short. Only two. Sentences here.", 3).isEmpty())
        assertNotNull(s)
    }
}
