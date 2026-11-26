package app.lodestone.data

import org.junit.Assert.assertEquals
import org.junit.Test

class SortingTest {
    private fun item(id: Long, title: String, created: Long, domain: String = "", pinned: Boolean = false) =
        Item(id = id, type = ItemType.LINK, title = title, created = created, domain = domain, pinned = pinned)

    private val items = listOf(
        item(1, "banana", 100, "b.com"),
        item(2, "Apple", 300, "a.com"),
        item(3, "cherry", 200, "", pinned = true),
        item(4, "apricot", 400, "a.com"),
    )

    private fun ids(mode: Int) = Sorting.apply(items, mode).map { it.id }

    @Test fun newestFirstKeepsPinnedOnTop() = assertEquals(listOf(3L, 4L, 2L, 1L), ids(Sorting.NEWEST))
    @Test fun oldestFirstKeepsPinnedOnTop() = assertEquals(listOf(3L, 1L, 2L, 4L), ids(Sorting.OLDEST))
    @Test fun titleIgnoresCase() = assertEquals(listOf(3L, 2L, 4L, 1L), ids(Sorting.TITLE))
    @Test fun siteGroupsByDomainThenTitle() = assertEquals(listOf(3L, 2L, 4L, 1L), ids(Sorting.SITE))
    @Test fun itemsWithoutASiteSortLastInSiteOrder() {
        val rest = Sorting.apply(listOf(item(1, "x", 1), item(2, "y", 2, "z.com")), Sorting.SITE).map { it.id }
        assertEquals(listOf(2L, 1L), rest)
    }
}
