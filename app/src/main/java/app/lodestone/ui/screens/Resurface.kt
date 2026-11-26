package app.lodestone.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.lodestone.data.ItemType
import app.lodestone.data.Store
import app.lodestone.ui.*
import app.lodestone.ui.theme.Ink
import app.lodestone.ui.theme.T

/** A few older items, one at a time. Real data only: nothing is shown if the library is empty. */
@Composable
fun ResurfaceScreen() {
    val c = T.c
    val queue = remember { Store.live.sortedBy { it.created }.take(40).shuffled().take(5) }
    var index by remember { mutableIntStateOf(0) }
    val item = queue.getOrNull(index)

    Column(Modifier.fillMaxSize().screenPadding()) {
        Row(Modifier.fillMaxWidth().padding(16.dp, 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconCircle(Icons.Rounded.Close, "Close", { Nav.back() })
            Spacer(Modifier.weight(1f))
            if (queue.isNotEmpty()) Box(Modifier.height(40.dp).clip(RoundedCornerShape(20.dp)).background(c.hi).padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
                Text("${(index + 1).coerceAtMost(queue.size)} of ${queue.size}", style = MaterialTheme.typography.labelMedium, color = c.tx)
            }
        }
        if (item == null) {
            Column(Modifier.weight(1f).padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (queue.isEmpty()) "Nothing to look back on yet" else "All caught up", style = MaterialTheme.typography.displaySmall, color = c.tx)
                Text(if (queue.isEmpty()) "Save a few things and they'll come back to you here." else "Come back another day for more.", style = MaterialTheme.typography.bodyLarge, color = c.sec, modifier = Modifier.padding(top = 8.dp))
            }
        } else {
            Text("Remember this?", style = MaterialTheme.typography.displaySmall, color = c.tx, modifier = Modifier.padding(horizontal = 20.dp))
            val days = ((System.currentTimeMillis() - item.created) / 86_400_000L).toInt()
            Text(
                "Saved " + when { days < 1 -> "today"; days == 1 -> "yesterday"; days < 60 -> "$days days ago"; else -> "${days / 30} months ago" },
                style = MaterialTheme.typography.bodyMedium, color = c.sec, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
            Box(
                Modifier.weight(1f).fillMaxWidth().padding(16.dp).clip(RoundedCornerShape(44.dp, 44.dp, 44.dp, 12.dp)).background(itemColor(item, c.x))
                    .clickable { Nav.push(Route.Detail(item.id)) },
            ) {
                if (item.thumb.isNotEmpty() && item.type != ItemType.NOTE) LocalPicture(thumbDir, item.thumb, Modifier.matchParentSize(), ContentScale.Crop, 1200)
                Column(
                    Modifier.align(Alignment.BottomStart).fillMaxWidth().then(if (item.thumb.isNotEmpty() && item.type != ItemType.NOTE) Modifier.background(c.bg.copy(alpha = .78f)) else Modifier).padding(24.dp),
                ) {
                    val fg = if (item.thumb.isNotEmpty() && item.type != ItemType.NOTE) c.tx else Ink
                    Text(item.type.name, style = MaterialTheme.typography.labelSmall, color = fg.copy(alpha = .8f))
                    Text(item.title.ifBlank { "Untitled" }, style = MaterialTheme.typography.headlineMedium, color = fg, maxLines = 5, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        Row(Modifier.windowInsetsPadding(WindowInsets.navigationBars).padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (item != null) {
                IconCircle(Icons.Rounded.Delete, "Delete", { Undo.trash(listOf(item.id)); index++ }, size = 64.dp, shape = RoundedCornerShape(24.dp))
                PillButton("Keep it around", { index++ }, Modifier.weight(1f))
                IconCircle(Icons.Rounded.Schedule, "Show later", { index++ }, size = 64.dp, shape = RoundedCornerShape(24.dp))
            } else PillButton("Back to library", { Nav.back() }, Modifier.fillMaxWidth())
        }
    }
}
