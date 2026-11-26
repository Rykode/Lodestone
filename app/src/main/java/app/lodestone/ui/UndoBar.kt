package app.lodestone.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.lodestone.data.Store
import app.lodestone.ui.theme.T
import kotlinx.coroutines.delay

/** Moves things to Recently deleted and offers to take it back for a few seconds. */
object Undo {
    class Pending(val ids: List<Long>, val text: String, val token: Long)

    var pending by mutableStateOf<Pending?>(null)
        private set

    fun trash(ids: Collection<Long>) {
        if (ids.isEmpty()) return
        val list = ids.toList()
        Store.trash(list)
        pending = Pending(list, if (list.size == 1) "Moved to Recently deleted" else "${list.size} items moved to Recently deleted", System.nanoTime())
    }

    fun undo() {
        pending?.let { Store.restore(it.ids) }
        pending = null
    }

    fun dismiss(token: Long) { if (pending?.token == token) pending = null }
}

@Composable
fun UndoBar(modifier: Modifier = Modifier) {
    val c = T.c
    val p = Undo.pending ?: return
    LaunchedEffect(p.token) { delay(7000); Undo.dismiss(p.token) }
    Row(
        modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.tx)
            .semantics { liveRegion = LiveRegionMode.Polite }.padding(start = 20.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(p.text, style = MaterialTheme.typography.bodyMedium, color = c.bg, modifier = Modifier.weight(1f).padding(vertical = 14.dp))
        Box(
            Modifier.heightIn(min = 48.dp).padding(vertical = 4.dp).clip(RoundedCornerShape(16.dp)).background(c.chip)
                .clickable(role = Role.Button) { Undo.undo() }.padding(horizontal = 20.dp),
            contentAlignment = Alignment.Center,
        ) { Text("Undo", style = MaterialTheme.typography.labelLarge, color = c.onChip) }
    }
}
