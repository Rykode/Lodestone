package app.lodestone.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.lodestone.data.Embeds
import app.lodestone.data.Facts
import app.lodestone.data.Item
import app.lodestone.data.ItemType
import app.lodestone.data.Store
import app.lodestone.ui.theme.Ink
import app.lodestone.ui.theme.T

/** Corner sizes (top-start, top-end, bottom-end, bottom-start) in dp. Cards differ so the grid feels hand-made. */
private val corners = listOf(
    floatArrayOf(28f, 28f, 28f, 10f),
    floatArrayOf(28f, 28f, 28f, 28f),
    floatArrayOf(28f, 10f, 28f, 28f),
    floatArrayOf(10f, 28f, 28f, 28f),
)

fun itemColor(item: Item, x: List<Color>): Color = x[((item.id.hashCode() % 5) + 5) % 5]

/** One compact row: a small picture or colour, the title, and where it came from. Used by the list view. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ItemRow(item: Item, selecting: Boolean, selected: Boolean, onClick: () -> Unit, onLong: () -> Unit, modifier: Modifier = Modifier) {
    val c = T.c
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier.fillMaxWidth().clip(shape).background(if (selected) c.hi else c.card)
            .then(if (selected) Modifier.border(3.dp, c.chip, shape) else Modifier)
            .combinedClickable(onClick = onClick, onLongClick = { if (Store.haptics) haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress); onLong() })
            .semantics { contentDescription = "${item.type.name.lowercase()}: ${item.title}" }.padding(10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(64.dp).clip(RoundedCornerShape(14.dp)).background(if (item.thumb.isEmpty()) itemColor(item, c.x) else c.hi), contentAlignment = Alignment.Center) {
            if (item.thumb.isNotEmpty()) LocalPicture(thumbDir, item.thumb, Modifier.matchParentSize(), ContentScale.Crop, 200)
            else Text(item.type.name.take(1), style = MaterialTheme.typography.titleLarge, color = Ink)
            if (selecting) Box(Modifier.align(Alignment.TopStart).padding(4.dp).size(22.dp).clip(CircleShape).background(if (selected) c.chip else c.bg.copy(alpha = .6f)), contentAlignment = Alignment.Center) {
                if (selected) Icon(Icons.Rounded.Check, "Selected", tint = c.onChip, modifier = Modifier.size(14.dp))
            }
        }
        Column(Modifier.weight(1f)) {
            Text(item.title.ifBlank { item.note.lineSequence().firstOrNull().orEmpty().ifBlank { item.type.name.lowercase().replaceFirstChar { it.uppercase() } } }, style = MaterialTheme.typography.titleSmall, color = c.tx, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val sub = listOf(item.domain.ifBlank { item.type.name.lowercase().replaceFirstChar { it.uppercase() } }, java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM).format(java.util.Date(item.created))).joinToString(" · ")
            Text(sub, style = MaterialTheme.typography.bodySmall, color = c.sec, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (item.pinned) Icon(Icons.Rounded.PushPin, "Pinned", tint = c.sec, modifier = Modifier.size(18.dp))
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ItemCard(
    item: Item, selecting: Boolean, selected: Boolean, onClick: () -> Unit, onLong: () -> Unit, modifier: Modifier = Modifier,
) {
    // Expressive motion: the card squashes on press and its corners spring to a squarer shape when selected.
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val press by animateFloatAsState(if (pressed && !Store.reduceMotion) 0.95f else 1f, spring(dampingRatio = 0.5f, stiffness = 600f), label = "press")
    val sel by animateFloatAsState(if (selected) 1f else 0f, spring(dampingRatio = 0.6f, stiffness = 500f), label = "select")
    val base = if (Store.uniformCards) floatArrayOf(24f, 24f, 24f, 24f) else corners[((item.id % 4) + 4).toInt() % 4]
    val k = when (Store.corners) { 0 -> 0.35f; 2 -> 1.4f; else -> 1f }
    fun corner(i: Int) = ((base[i] + (20f - base[i]) * sel) * k).coerceAtLeast(0f).dp
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val shape = RoundedCornerShape(corner(0), corner(1), corner(2), corner(3))
    val c = T.c
    val tint = itemColor(item, c.x)
    Box(
        modifier.graphicsLayer { scaleX = press; scaleY = press }.clip(shape)
            .then(if (selected) Modifier.border(3.dp, c.chip, shape) else Modifier)
            .combinedClickable(interactionSource = interaction, indication = null, onClick = onClick, onLongClick = { if (Store.haptics) haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress); onLong() })
            .semantics { contentDescription = "${item.type.name.lowercase()}: ${item.title}" },
    ) {
        when (item.type) {
            ItemType.IMAGE, ItemType.VIDEO -> {
                val ratio = if (item.aspect > 0f) (1f / item.aspect).coerceIn(0.62f, 1.7f) else 1f
                Box(Modifier.fillMaxWidth().aspectRatio(ratio).background(c.hi)) {
                    LocalPicture(thumbDir, item.thumb, Modifier.matchParentSize(), ContentScale.Crop, 520)
                    if (item.type == ItemType.VIDEO) {
                        Box(Modifier.align(Alignment.Center).size(52.dp).clip(RoundedCornerShape(18.dp)).background(c.x[2]), contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.PlayArrow, null, tint = Ink)
                        }
                    }
                }
            }
            ItemType.NOTE -> Column(Modifier.fillMaxWidth().background(tint).padding(18.dp, if (selecting) 46.dp else 18.dp, 18.dp, 18.dp)) {
                Text(item.title.ifBlank { "Note" }, style = MaterialTheme.typography.titleLarge, color = Ink, maxLines = 6, overflow = TextOverflow.Ellipsis)
                val rest = item.note.removePrefix(item.title).trim()
                if (rest.isNotEmpty()) Text(rest, style = MaterialTheme.typography.bodySmall, color = Ink.copy(alpha = .8f), maxLines = 4, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
            }
            ItemType.LINK -> Column(Modifier.fillMaxWidth().background(c.card)) {
                if (item.thumb.isNotEmpty()) {
                    val ratio = if (item.aspect > 0f) (1f / item.aspect).coerceIn(1.0f, 1.9f) else 1.6f
                    Box(Modifier.fillMaxWidth().aspectRatio(ratio).background(c.hi)) { LocalPicture(thumbDir, item.thumb, Modifier.matchParentSize(), ContentScale.Crop, 520) }
                } else Box(Modifier.fillMaxWidth().height(64.dp).background(tint))
                val platform = remember(item.url) { if (item.kind.isNotEmpty()) Embeds.parse(item.url)?.platform?.label else null }
                Column(Modifier.padding(14.dp, 12.dp, 14.dp, 14.dp)) {
                    Text(item.title, style = MaterialTheme.typography.titleSmall, color = c.tx, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    val facts = remember(item.extra, item.kind) { Facts.line(item) }
                    if (facts.isNotEmpty()) Text(facts, style = MaterialTheme.typography.labelMedium, color = c.fab, maxLines = 2, modifier = Modifier.padding(top = 4.dp))
                    Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            if (!item.enriched) item.domain + " · fetching preview" else platform ?: item.domain,
                            style = MaterialTheme.typography.bodySmall, color = c.sec, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                        )
                        if (!item.enriched) LoadingIndicator(Modifier.size(22.dp))
                    }
                }
            }
            ItemType.FILE -> Column(Modifier.fillMaxWidth().background(tint)) {
                // A PDF shows its first page.
                if (item.thumb.isNotEmpty()) Box(Modifier.fillMaxWidth().aspectRatio(0.8f).background(Color.White)) {
                    LocalPicture(thumbDir, item.thumb, Modifier.matchParentSize(), ContentScale.Crop, 700)
                }
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = .7f)), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Description, null, tint = Ink) }
                    Column {
                        val facts = remember(item.extra, item.kind) { Facts.line(item) }
                        Text(facts.ifEmpty { item.mime.substringAfterLast('/').uppercase().take(8) }, style = MaterialTheme.typography.labelSmall, color = Ink.copy(alpha = .75f))
                        Text(item.title, style = MaterialTheme.typography.titleSmall, color = Ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        if (item.pinned && !selecting) {
            Box(Modifier.align(Alignment.TopEnd).padding(8.dp).size(28.dp).clip(CircleShape).background(c.bg.copy(alpha = .8f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.PushPin, "Pinned", tint = c.tx, modifier = Modifier.size(16.dp))
            }
        }
        if (selecting) {
            Box(
                Modifier.align(Alignment.TopStart).padding(10.dp).size(32.dp).clip(RoundedCornerShape(if (selected) 11.dp else 16.dp))
                    .background(if (selected) c.chip else c.bg.copy(alpha = .55f))
                    .then(if (selected) Modifier else Modifier.border(2.dp, c.tx.copy(alpha = .8f), CircleShape)),
                contentAlignment = Alignment.Center,
            ) { if (selected) Icon(Icons.Rounded.Check, "Selected", tint = c.onChip, modifier = Modifier.size(18.dp)) }
        }
    }
}
