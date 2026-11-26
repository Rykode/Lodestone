package app.lodestone.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.lodestone.data.Related
import app.lodestone.data.Store
import app.lodestone.ui.*
import app.lodestone.ui.theme.T

/** Everything in the library that goes with one item, best match first. */
@Composable
fun VibeScreen(id: Long) {
    val c = T.c
    val item = Store.get(id)
    if (item == null) { LaunchedEffect(Unit) { Nav.back() }; return }
    val related = remember(id, Store.live) { Related.top(item, Store.live, 40) }
    Column(Modifier.fillMaxSize().screenPadding()) {
        ScreenHeader("Goes with this", onBack = { Nav.back() })
        Text(
            "Matched by shared tags, words in the title, the kind of card and the main colors of pictures. Nothing leaves your phone.",
            style = MaterialTheme.typography.bodyMedium, color = c.sec, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
        )
        if (related.isEmpty()) {
            Text("Nothing in your library goes with this yet.", style = MaterialTheme.typography.bodyLarge, color = c.sec, modifier = Modifier.padding(24.dp))
        } else LazyVerticalStaggeredGrid(
            StaggeredGridCells.Fixed(app.lodestone.data.Store.gridCols), Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 40.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalItemSpacing = 12.dp,
        ) {
            items(related, key = { it.id }) { r -> ItemCard(r, false, false, { Nav.push(Route.Detail(r.id)) }, {}) }
        }
    }
}
