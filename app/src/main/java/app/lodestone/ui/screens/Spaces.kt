package app.lodestone.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.lodestone.data.SmartRules
import app.lodestone.data.Space
import app.lodestone.data.Store
import app.lodestone.ui.*
import app.lodestone.ui.theme.Ink
import app.lodestone.ui.theme.T

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SpacesScreen() {
    val c = T.c
    var creating by remember { mutableStateOf(false) }
    var chooseKind by remember { mutableStateOf(false) }
    var manage by remember { mutableStateOf<Space?>(null) }
    val live = Store.live
    val unsorted = live.count { it.spaceId == 0L }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().screenPadding()) {
            ScreenHeader("Spaces", onBack = null, trailing = {
                PillButton("New", { chooseKind = true }, container = c.tx, content = c.bg, icon = Icons.Rounded.Add, height = 48.dp)
            })
            Text("Rooms for the things you keep. Long-press a space to rename or remove it.", style = MaterialTheme.typography.bodyMedium, color = c.sec, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))

            LazyVerticalGrid(
                GridCells.Fixed(2), Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 130.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(Store.spaces, key = { it.id }, span = { s -> if (Store.spaces.first().id == s.id) GridItemSpan(2) else GridItemSpan(1) }) { s ->
                    val big = Store.spaces.first().id == s.id
                    val smart = s.rule.isNotBlank()
                    val smartCount by produceState(0, s.rule, live) { value = if (smart) SmartRules.items(s, live).size else 0 }
                    val count = if (smart) smartCount else live.count { it.spaceId == s.id }
                    val bg = if (big) c.fab else c.x[s.colorIdx.coerceIn(0, 4)]
                    val fg = if (big) c.onFab else Ink
                    Box(
                        Modifier.fillMaxWidth().height(if (big) 170.dp else 150.dp)
                            .clip(if (big) RoundedCornerShape(44.dp, 44.dp, 44.dp, 12.dp) else RoundedCornerShape(28.dp)).background(bg)
                            .combinedClickable(onClick = { Nav.push(Route.Space(s.id)) }, onLongClick = { manage = s })
                            .semantics { contentDescription = "${s.name}, $count items" }.padding(18.dp),
                    ) {
                        Column(Modifier.align(Alignment.BottomStart)) {
                            Text(s.name, style = if (big) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleLarge, color = fg, maxLines = 2)
                            Text("$count ${if (count == 1) "item" else "items"}" + if (smart) " · smart" else "", style = MaterialTheme.typography.bodySmall, color = fg.copy(alpha = .85f))
                        }
                    }
                }
                item(span = { GridItemSpan(2) }) {
                    Row(
                        Modifier.fillMaxWidth().height(84.dp).clip(RoundedCornerShape(28.dp)).background(c.card).combinedClickable(onClick = { Nav.push(Route.Space(0L)) })
                            .padding(horizontal = 22.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text("Unsorted", style = MaterialTheme.typography.titleSmall, color = c.tx)
                            Text("$unsorted ${if (unsorted == 1) "item" else "items"} waiting for a home", style = MaterialTheme.typography.bodySmall, color = c.sec)
                        }
                    }
                }
            }
        }
    }

    if (chooseKind) AlertDialog(
        onDismissRequest = { chooseKind = false }, containerColor = c.card, titleContentColor = c.tx, textContentColor = c.sec,
        title = { Text("New space") },
        text = { Text("A normal space holds what you put in it. A Smart Space fills itself from rules, like \"recipes saved this month\".") },
        confirmButton = { TextButton(onClick = { chooseKind = false; Nav.push(Route.SmartBuilder(0)) }) { Text("Smart Space") } },
        dismissButton = { TextButton(onClick = { chooseKind = false; creating = true }) { Text("Normal space") } },
    )
    if (creating) NameDialog("New space", "", "Create", onDone = { Store.addSpace(it, Store.spaces.size % 5); creating = false }, onDismiss = { creating = false })
    manage?.let { s ->
        var renaming by remember(s.id) { mutableStateOf(false) }
        var confirm by remember(s.id) { mutableStateOf(false) }
        when {
            renaming -> NameDialog("Rename space", s.name, "Save", onDone = { Store.renameSpace(s, it); manage = null }, onDismiss = { manage = null })
            confirm -> ConfirmDialog("Remove \"${s.name}\"?", "Its items stay in your library as Unsorted.", "Remove", onConfirm = { Store.deleteSpace(s.id); manage = null }, onDismiss = { manage = null })
            else -> AlertDialog(
                onDismissRequest = { manage = null }, containerColor = c.card, titleContentColor = c.tx, title = { Text(s.name) },
                text = {}, confirmButton = { TextButton(onClick = { if (s.rule.isNotBlank()) { manage = null; Nav.push(Route.SmartBuilder(s.id)) } else renaming = true }) { Text(if (s.rule.isNotBlank()) "Edit rules" else "Rename") } },
                dismissButton = { TextButton(onClick = { confirm = true }) { Text("Remove") } },
            )
        }
    }
}

@Composable
fun NameDialog(title: String, initial: String, confirm: String, onDone: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(initial) }
    val c = T.c
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = c.card, titleContentColor = c.tx, title = { Text(title) },
        text = { OutlinedTextField(name, { name = it }, singleLine = true, label = { Text("Name") }) },
        confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { onDone(name) }) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
