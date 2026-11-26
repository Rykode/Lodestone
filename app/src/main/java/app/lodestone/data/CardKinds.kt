package app.lodestone.data

import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

/**
 * Works out what a saved page is (recipe, product, book, article) from the facts the page itself declares
 * (schema.org data and Open Graph tags). No guessing and no AI: if a page does not say, the card stays a plain link.
 */
object CardKinds {
    data class Found(val kind: String, val facts: Map<String, String>, val keywords: List<String>)

    private val ldScript = Regex("<script[^>]+type=[\"']application/ld\\+json[\"'][^>]*>(.*?)</script>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
    private val articleTypes = setOf("article", "newsarticle", "blogposting", "report", "scholarlyarticle", "techarticle")

    fun detect(html: String): Found? {
        val objects = ldScript.findAll(html).flatMap { m ->
            runCatching { flatten(JSONTokener(m.groupValues[1].trim()).nextValue()) }.getOrDefault(emptyList())
        }.toList()

        fun typesOf(o: JSONObject): Set<String> = when (val t = o.opt("@type")) {
            is String -> setOf(t.lowercase())
            is JSONArray -> (0 until t.length()).map { t.optString(it).lowercase() }.toSet()
            else -> emptySet()
        }
        val keywords = keywordsFrom(objects, html)

        objects.firstOrNull { "recipe" in typesOf(it) }?.let { o ->
            val facts = linkedMapOf<String, String>()
            minutes(o.optString("totalTime").ifBlank { o.optString("cookTime") }.ifBlank { o.optString("prepTime") })?.let { facts["time"] = it }
            yieldOf(o.opt("recipeYield"))?.let { facts["servings"] = it }
            o.optJSONArray("recipeIngredient")?.length()?.takeIf { it > 0 }?.let { facts["ingredients"] = it.toString() }
            authorOf(o)?.let { facts["author"] = it }
            return Found("recipe", facts, keywords)
        }
        objects.firstOrNull { "product" in typesOf(it) }?.let { o ->
            val facts = linkedMapOf<String, String>()
            priceOf(o.opt("offers"))?.let { facts["price"] = it }
            (o.opt("brand") as? JSONObject)?.optString("name")?.takeIf { it.isNotBlank() }?.let { facts["brand"] = it }
                ?: (o.opt("brand") as? String)?.takeIf { it.isNotBlank() }?.let { facts["brand"] = it }
            return Found("product", facts, keywords)
        }
        objects.firstOrNull { "book" in typesOf(it) }?.let { o ->
            val facts = linkedMapOf<String, String>()
            authorOf(o)?.let { facts["author"] = it }
            o.optString("numberOfPages").takeIf { it.isNotBlank() }?.let { facts["pages"] = it }
            return Found("book", facts, keywords)
        }
        // Open Graph fallbacks
        val og = ogType(html)
        if (og == "product" || og == "og:product") {
            val facts = linkedMapOf<String, String>()
            val amount = meta(html, "product:price:amount") ?: meta(html, "og:price:amount")
            val cur = meta(html, "product:price:currency") ?: meta(html, "og:price:currency")
            if (amount != null) facts["price"] = money(amount, cur)
            return Found("product", facts, keywords)
        }
        if (og == "book") return Found("book", linkedMapOf<String, String>().also { f -> meta(html, "book:author")?.let { f["author"] = it } }, keywords)
        objects.firstOrNull { typesOf(it).any { t -> t in articleTypes } }?.let { o ->
            val facts = linkedMapOf<String, String>()
            authorOf(o)?.let { facts["author"] = it }
            return Found("article", facts, keywords)
        }
        if (og == "article") return Found("article", linkedMapOf<String, String>().also { f -> (meta(html, "author") ?: meta(html, "article:author"))?.takeIf { !it.startsWith("http") }?.let { f["author"] = it } }, keywords)
        return null
    }

    private fun flatten(v: Any?, depth: Int = 0): List<JSONObject> {
        if (depth > 4) return emptyList()
        return when (v) {
            is JSONObject -> listOf(v) + (v.optJSONArray("@graph")?.let { g -> (0 until g.length()).flatMap { flatten(g.opt(it), depth + 1) } } ?: emptyList())
            is JSONArray -> (0 until v.length()).flatMap { flatten(v.opt(it), depth + 1) }
            else -> emptyList()
        }
    }

    private fun authorOf(o: JSONObject): String? = when (val a = o.opt("author")) {
        is String -> a
        is JSONObject -> a.optString("name")
        is JSONArray -> (0 until a.length()).mapNotNull { (a.opt(it) as? JSONObject)?.optString("name") ?: a.optString(it) }.filter { it.isNotBlank() }.take(2).joinToString(", ")
        else -> null
    }?.trim()?.takeIf { it.isNotBlank() && !it.startsWith("http") && !it.contains("contributors", ignoreCase = true) }

    /** "PT1H20M" becomes "1 h 20 min". */
    internal fun minutes(iso: String): String? {
        val m = Regex("^P(?:\\d+D)?T?(?:(\\d+)H)?(?:(\\d+)M)?", RegexOption.IGNORE_CASE).find(iso.trim()) ?: return null
        val h = m.groupValues[1].toIntOrNull() ?: 0
        val min = m.groupValues[2].toIntOrNull() ?: 0
        if (h == 0 && min == 0) return null
        return buildString { if (h > 0) append("$h h"); if (h > 0 && min > 0) append(' '); if (min > 0) append("$min min") }
    }

    private fun yieldOf(v: Any?): String? = when (v) {
        is String -> v
        is Number -> v.toString()
        is JSONArray -> if (v.length() > 0) v.optString(0) else null
        else -> null
    }?.trim()?.takeIf { it.isNotBlank() && it.length < 40 }

    private fun priceOf(offers: Any?): String? {
        val o = when (offers) { is JSONObject -> offers; is JSONArray -> offers.optJSONObject(0); else -> null } ?: return null
        val price = o.opt("price")?.toString()?.takeIf { it.isNotBlank() } ?: o.optJSONObject("priceSpecification")?.opt("price")?.toString() ?: return null
        return money(price, o.optString("priceCurrency").ifBlank { null })
    }

    internal fun money(amount: String, currency: String?): String {
        val clean = amount.trim().removeSuffix(".00")
        val sym = when (currency?.uppercase()) { "USD" -> "$"; "EUR" -> "€"; "GBP" -> "£"; "INR" -> "₹"; "JPY" -> "¥"; null, "" -> ""; else -> currency.uppercase() + " " }
        return sym + clean
    }

    private fun meta(html: String, key: String): String? {
        val k = Regex.escape(key)
        val a = Regex("<meta[^>]+(?:property|name)=[\"']$k[\"'][^>]*content=[\"']([^\"']*)[\"']", RegexOption.IGNORE_CASE).find(html)
        val b = Regex("<meta[^>]+content=[\"']([^\"']*)[\"'][^>]*(?:property|name)=[\"']$k[\"']", RegexOption.IGNORE_CASE).find(html)
        return (a ?: b)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotBlank() }
    }

    private fun ogType(html: String): String? = meta(html, "og:type")?.lowercase()

    private fun keywordsFrom(objects: List<JSONObject>, html: String): List<String> {
        val raw = mutableListOf<String>()
        for (o in objects) when (val k = o.opt("keywords")) {
            is String -> raw += k.split(',', ';')
            is JSONArray -> for (i in 0 until k.length()) raw += k.optString(i)
        }
        Regex("<meta[^>]+(?:property|name)=[\"']article:tag[\"'][^>]*content=[\"']([^\"']*)[\"']", RegexOption.IGNORE_CASE).findAll(html).forEach { raw += it.groupValues[1] }
        meta(html, "keywords")?.let { raw += it.split(',') }
        return raw.map { it.trim().lowercase() }.filter { it.length in 2..24 && !it.contains("http") }.distinct().take(5)
    }
}

/** The short line of real facts under a card's title, built from what the page declared. Empty when there is nothing to say. */
object Facts {
    fun line(item: Item): String {
        val o = runCatching { org.json.JSONObject(item.extra.ifBlank { "{}" }) }.getOrDefault(org.json.JSONObject())
        fun s(k: String) = o.optString(k).takeIf { it.isNotBlank() }
        val parts = when (item.kind) {
            "recipe" -> listOfNotNull("Recipe", s("time"), s("ingredients")?.let { "$it ingredients" })
            "product" -> listOfNotNull(s("price"), s("brand"))
            "book" -> listOfNotNull("Book", s("author"))
            "article" -> listOfNotNull(s("author"), o.optInt("read").takeIf { it > 0 }?.let { "$it min read" })
            "pdf" -> listOfNotNull("PDF", o.optInt("pages").takeIf { it > 0 }?.let { if (it == 1) "1 page" else "$it pages" })
            else -> listOfNotNull(o.optInt("read").takeIf { it > 0 }?.let { "$it min read" })
        }
        return parts.joinToString(" · ")
    }
}
