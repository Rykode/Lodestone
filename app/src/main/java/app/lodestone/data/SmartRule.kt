package app.lodestone.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * What a Smart Space collects. Every part that is filled in must match (types, kinds, tags, words, age).
 * Nothing is copied: the space is simply the library seen through these rules, so it fills itself as you save.
 */
data class SmartRule(
    val types: Set<String> = emptySet(),
    val kinds: Set<String> = emptySet(),
    val tags: List<String> = emptyList(),
    val words: List<String> = emptyList(),
    /** Only things saved within this many days; 0 means any time. */
    val days: Int = 0,
) {
    val isEmpty: Boolean get() = types.isEmpty() && kinds.isEmpty() && tags.isEmpty() && words.isEmpty() && days == 0

    fun toJson(): String = JSONObject()
        .put("types", JSONArray(types.toList())).put("kinds", JSONArray(kinds.toList()))
        .put("tags", JSONArray(tags)).put("words", JSONArray(words)).put("days", days).toString()

    /** [wordHits] says which items contain each word in their saved text (from the database). */
    fun matches(item: Item, wordHits: Map<String, Set<Long>>, now: Long = System.currentTimeMillis()): Boolean {
        if (item.deleted != 0L) return false
        if (types.isNotEmpty() && item.type.name !in types) return false
        if (kinds.isNotEmpty() && item.kind !in kinds) return false
        if (days > 0 && item.created < now - days * 86_400_000L) return false
        for (t in tags) {
            val n = SearchText.norm(t.trim().trimStart('#'))
            if (n.isNotEmpty() && item.tags.none { SearchText.norm(it) == n }) return false
        }
        for (w in words) {
            val n = SearchText.norm(w.trim())
            if (n.isNotEmpty() && !item.key.contains(n) && item.id !in (wordHits[n] ?: emptySet())) return false
        }
        return true
    }

    companion object {
        fun parse(json: String): SmartRule {
            if (json.isBlank()) return SmartRule()
            return runCatching {
                val o = JSONObject(json)
                fun set(k: String) = o.optJSONArray(k)?.let { a -> (0 until a.length()).map { a.getString(it) }.toSet() } ?: emptySet()
                fun list(k: String) = o.optJSONArray(k)?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList()
                SmartRule(set("types"), set("kinds"), list("tags"), list("words"), o.optInt("days"))
            }.getOrDefault(SmartRule())
        }
    }
}

object SmartRules {
    fun isSmart(space: Space) = space.rule.isNotBlank()

    /** The items that currently belong in a Smart Space, newest first (the library is already in that order). */
    suspend fun items(space: Space, from: List<Item>): List<Item> = items(SmartRule.parse(space.rule), from)

    suspend fun items(rule: SmartRule, from: List<Item>): List<Item> {
        if (rule.isEmpty) return emptyList()
        val hits = rule.words.map { SearchText.norm(it.trim()) }.filter { it.isNotEmpty() }.associateWith { Store.idsWithText(it) }
        return from.filter { rule.matches(it, hits) }
    }
}
