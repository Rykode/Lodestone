package app.lodestone.ui.screens

import android.graphics.Color as AColor
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.lodestone.data.Item
import app.lodestone.data.SearchText
import app.lodestone.data.Store
import app.lodestone.ui.*
import app.lodestone.ui.theme.Ink
import app.lodestone.ui.theme.T

private data class Swatch(val name: String, val hue: Float, val color: Color)

private val swatches = listOf(
    Swatch("Red", 0f, Color(0xFFE5484D)), Swatch("Orange", 28f, Color(0xFFFF9B6B)), Swatch("Yellow", 52f, Color(0xFFF2D14B)),
    Swatch("Green", 130f, Color(0xFF4FB26E)), Swatch("Teal", 175f, Color(0xFF2BB5A8)), Swatch("Blue", 215f, Color(0xFF4A86F0)),
    Swatch("Purple", 275f, Color(0xFF8A5CF0)), Swatch("Pink", 330f, Color(0xFFE6457A)),
    Swatch("Dark", -1f, Color(0xFF17181C)), Swatch("Light", -2f, Color(0xFFF1EFEA)),
)

private fun matchesColor(item: Item, s: Swatch): Boolean {
    if (item.color == 0) return false
    val hsv = FloatArray(3)
    AColor.colorToHSV(item.color, hsv)
    return when (s.hue) {
        -1f -> hsv[2] < 0.32f
        -2f -> hsv[1] < 0.18f && hsv[2] > 0.78f
        else -> {
            if (hsv[1] < 0.2f || hsv[2] < 0.2f) false
            else {
                val d = Math.abs(hsv[0] - s.hue).let { if (it > 180f) 360f - it else it }
                d <= 26f
            }
        }
    }
}

private fun metaHas(item: Item, token: String): Boolean = item.key.contains(token)

private fun tokenHits(hits: Map<String, Set<Long>>): Set<Long> = hits.values.flatMapTo(HashSet()) { it }

/** The few words around the match, so you can see why a picture or page was found. */
@Composable
private fun MatchSnippet(id: Long, tokens: List<String>) {
    val c = T.c
    val snippet by produceState("", id, tokens.joinToString(" ")) {
        val full = Store.textOf(id)
        val folded = SearchText.norm(full)
        val at = tokens.map { folded.indexOf(it) }.firstOrNull { it >= 0 } ?: -1
        value = if (at < 0 || full.isEmpty()) "" else {
            val from = (at - 28).coerceAtLeast(0).coerceAtMost(full.length)
            val to = (at + 56).coerceAtMost(full.length)
            (if (from > 0) "…" else "") + full.substring(from, to).replace('\n', ' ').trim() + (if (to < full.length) "…" else "")
        }
    }
    if (snippet.isNotEmpty()) Text(snippet, style = MaterialTheme.typography.bodySmall, color = c.sec, maxLines = 3, modifier = Modifier.padding(horizontal = 8.dp))
}

@Composable
fun SearchScreen() {
    val c = T.c
    LaunchedEffect(Unit) { Nav.pendingQuery?.let { Nav.searchQuery = it; Nav.searchType = null; Nav.searchSwatch = -1 }; Nav.pendingQuery = null }
    var query by Nav::searchQuery
    var type by Nav::searchType
    var swatchIdx by Nav::searchSwatch

    val tokens = query.split(' ', ',').filter { it.isNotBlank() }.map { SearchText.norm(it.trimStart('#')) }.filter { it.isNotEmpty() }
    val active = tokens.isNotEmpty() || type != null || swatchIdx >= 0
    // Page text is searched in the database, only for the words typed.
    // It runs again whenever a link finishes loading (its saved text changes), so open results update by themselves.
    val loadedSignature = Store.items.count { it.enriched }
    val textHits by produceState<Map<String, Set<Long>>>(emptyMap(), tokens.joinToString(" "), loadedSignature) { value = tokens.associateWith { Store.idsWithText(it) } }
    val results = if (!active) emptyList() else Store.live.filter { i ->
        tokens.all { t -> metaHas(i, t) || i.id in (textHits[t] ?: emptySet()) } && (type == null || i.type.name == type) && (swatchIdx < 0 || matchesColor(i, swatches[swatchIdx]))
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().screenPadding()) {
            Row(Modifier.fillMaxWidth().padding(16.dp, 8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextField(
                    value = query, onValueChange = { query = it }, singleLine = true, modifier = Modifier.weight(1f),
                    placeholder = { Text("Search words, tags, text in pages") },
                    leadingIcon = { Icon(Icons.Rounded.Search, null) },
                    trailingIcon = { if (query.isNotEmpty()) Icon(Icons.Rounded.Close, "Clear search", Modifier.clip(CircleShape).clickable { query = "" }.padding(4.dp)) },
                    shape = RoundedCornerShape(28.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = c.card, unfocusedContainerColor = c.card, focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                        focusedTextColor = c.tx, unfocusedTextColor = c.tx, focusedPlaceholderColor = c.sec, unfocusedPlaceholderColor = c.sec,
                        focusedLeadingIconColor = c.sec, unfocusedLeadingIconColor = c.sec, focusedTrailingIconColor = c.sec, unfocusedTrailingIconColor = c.sec,
                    ),
                )
            }

            Text("Find by color", style = MaterialTheme.typography.titleMedium, color = c.tx, modifier = Modifier.padding(start = 20.dp, top = 6.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(16.dp, 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                swatches.forEachIndexed { i, s ->
                    val on = swatchIdx == i
                    Box(
                        Modifier.size(48.dp).clip(RoundedCornerShape(if (on) 24.dp else 16.dp)).background(s.color)
                            .then(if (on) Modifier.border(3.dp, c.tx, RoundedCornerShape(24.dp)) else Modifier.border(1.dp, c.ol1, RoundedCornerShape(16.dp)))
                            .clickable { swatchIdx = if (on) -1 else i }.semantics { contentDescription = s.name + if (on) ", selected" else "" },
                    )
                }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("LINK" to "Links", "IMAGE" to "Images", "VIDEO" to "Videos", "NOTE" to "Notes", "FILE" to "Files").forEach { (k, l) ->
                    FilterPill(l, type == k, { type = if (type == k) null else k })
                }
            }

            if (active) {
                Text("${results.size} match${if (results.size == 1) "" else "es"} on this phone", style = MaterialTheme.typography.bodySmall, color = c.sec, modifier = Modifier.padding(start = 20.dp, bottom = 6.dp))
                LazyVerticalStaggeredGrid(
                    StaggeredGridCells.Fixed(app.lodestone.data.Store.gridCols), Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 130.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp), verticalItemSpacing = 12.dp,
                ) {
                    items(results, key = { it.id }) { item ->
                        Column(Modifier.animateItem(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            ItemCard(item, false, false, { Nav.push(Route.Detail(item.id)) }, {})
                            if (tokens.isNotEmpty() && item.id in tokenHits(textHits)) MatchSnippet(item.id, tokens)
                        }
                    }
                }
            } else if (Store.live.isNotEmpty()) {
                Row(
                    Modifier.padding(16.dp).fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(c.x[1]).clickable { Nav.push(Route.Resurface) }.padding(16.dp, 16.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Box(Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)).background(Ink), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.History, null, tint = c.x[1])
                    }
                    Column {
                        Text("Resurface", style = MaterialTheme.typography.titleSmall, color = Ink)
                        Text("Show me something I saved a while ago", style = MaterialTheme.typography.bodySmall, color = Ink.copy(alpha = .8f))
                    }
                }
            }
        }
    }
}
