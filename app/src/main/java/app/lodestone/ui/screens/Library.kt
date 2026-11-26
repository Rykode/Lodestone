package app.lodestone.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.lodestone.data.Item
import app.lodestone.data.ItemType
import app.lodestone.data.Store
import app.lodestone.ui.*
import app.lodestone.ui.theme.BarIcon
import app.lodestone.ui.theme.Ink
import app.lodestone.ui.theme.T

@Composable
fun LibraryScreen(title: String?, spaceId: Long?) {
    var filter by remember { mutableStateOf(if (spaceId == null) Nav.libFilter else null) }
    LaunchedEffect(filter) { if (spaceId == null) Nav.libFilter = filter }
    val gridState = remember(spaceId) { if (spaceId == null) Nav.libGrid else androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState() }
    var selected by remember { mutableStateOf(setOf<Long>()) }
    var showMove by remember { mutableStateOf(false) }
    val selecting = selected.isNotEmpty()
    val c = T.c

    val space = remember(spaceId, Store.spaces) { spaceId?.let { id -> Store.spaces.firstOrNull { it.id == id } } }
    val smart by produceState<List<Item>?>(null, space?.rule, Store.live) {
        value = if (space != null && space.rule.isNotBlank()) app.lodestone.data.SmartRules.items(space, Store.live) else null
    }
    val inScope = smart ?: Store.live.filter { spaceId == null || it.spaceId == spaceId }
    val shown = inScope.filter { filter == null || it.type.name == filter || (filter?.startsWith("kind:") == true && it.kind == filter?.removePrefix("kind:")) }
    val ordered = remember(shown, Store.sortMode) { app.lodestone.data.Sorting.apply(shown, Store.sortMode) }
    var showOptions by remember { mutableStateOf(false) }
    BackHandler(selecting) { selected = emptySet() }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().screenPadding()) {
            if (selecting) {
                SelectionBar(selected.size, onClose = { selected = emptySet() }, onAll = { selected = ordered.map { it.id }.toSet() })
            } else if (title != null) {
                ScreenHeader(title, onBack = { Nav.back() }, trailing = { IconCircle(Icons.Rounded.Search, "Search", { Nav.tab(Route.Search) }) })
            } else {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(40.dp).clip(app.lodestone.ui.Squircle).background(c.fab), contentAlignment = Alignment.Center) {
                        androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(app.lodestone.R.drawable.mascot), null, Modifier.size(28.dp))
                    }
                    Text("Lodestone", style = MaterialTheme.typography.headlineSmall, color = c.tx, modifier = Modifier.padding(start = 10.dp).weight(1f))
                    IconCircle(Icons.Rounded.Settings, "Settings", { Nav.push(Route.Settings) })
                }
            }

            if (!selecting && title == null && inScope.isNotEmpty()) {
                Text("Things worth keeping", style = MaterialTheme.typography.displayMedium, color = c.tx, modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 6.dp))
                Text(
                    "${inScope.size} saved · all stored on this phone",
                    style = MaterialTheme.typography.bodyMedium, color = c.sec, modifier = Modifier.padding(start = 20.dp, top = 4.dp, bottom = 10.dp),
                )
            }

            if (inScope.isNotEmpty()) {
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box {
                        IconCircle(Icons.Rounded.Sort, "Sort and view", { showOptions = true }, size = 44.dp)
                        androidx.compose.material3.DropdownMenu(showOptions, { showOptions = false }) {
                            Text("Sort by", style = MaterialTheme.typography.labelMedium, color = c.sec, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
                            app.lodestone.data.Sorting.names.forEachIndexed { i, name ->
                                androidx.compose.material3.DropdownMenuItem(
                                    text = { Text(name) },
                                    trailingIcon = { if (Store.sortMode == i) Icon(Icons.Rounded.Check, null) },
                                    onClick = { Store.sortMode = i; Store.savePrefs(); showOptions = false },
                                )
                            }
                            androidx.compose.material3.HorizontalDivider()
                            Text("View as", style = MaterialTheme.typography.labelMedium, color = c.sec, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
                            listOf(false to "Cards", true to "List").forEach { (list, name) ->
                                androidx.compose.material3.DropdownMenuItem(
                                    text = { Text(name) },
                                    trailingIcon = { if (Store.listView == list) Icon(Icons.Rounded.Check, null) },
                                    onClick = { Store.listView = list; Store.savePrefs(); showOptions = false },
                                )
                            }
                        }
                    }
                    FilterPill("All", filter == null, { filter = null })
                    listOf("LINK" to "Links", "IMAGE" to "Images", "VIDEO" to "Videos", "NOTE" to "Notes", "FILE" to "Files").forEach { (k, label) ->
                        if (inScope.any { it.type.name == k }) FilterPill(label, filter == k, { filter = if (filter == k) null else k })
                    }
                    // Cards Lodestone recognised from what the page says about itself.
                    listOf("recipe" to "Recipes", "product" to "Products", "book" to "Books", "article" to "Articles", "pdf" to "PDFs").forEach { (k, label) ->
                        if (inScope.any { it.kind == k }) FilterPill(label, filter == "kind:$k", { filter = if (filter == "kind:$k") null else "kind:$k" })
                    }
                }
            }

            when {
                !Store.loaded -> Spacer(Modifier.weight(1f))
                inScope.isEmpty() -> EmptyLibrary(inSpace = spaceId != null, smart = space?.rule?.isNotBlank() == true)
                shown.isEmpty() -> Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.TopCenter) {
                    Text("Nothing of this type yet.", style = MaterialTheme.typography.bodyLarge, color = c.sec)
                }
                else -> LazyVerticalStaggeredGrid(
                    state = gridState,
                    columns = StaggeredGridCells.Fixed(if (Store.listView) 1 else app.lodestone.data.Store.gridCols),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 130.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalItemSpacing = 12.dp,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(ordered, key = { it.id }) { item ->
                        val open = { if (selecting) selected = toggle(selected, item.id) else Nav.push(Route.Detail(item.id)) }
                        val pick = { selected = toggle(selected, item.id) }
                        if (Store.listView) ItemRow(item, selecting, item.id in selected, open, pick, Modifier.animateItem())
                        else ItemCard(item, selecting, item.id in selected, onClick = open, onLong = pick, modifier = Modifier.animateItem())
                    }
                }
            }
        }

        val slide = spring<IntOffset>(dampingRatio = 0.75f, stiffness = 500f)
        AnimatedVisibility(
            selecting, Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(slide) { it } + fadeIn(), exit = slideOutVertically(slide) { it } + fadeOut(),
        ) {
            SelectActionBar(
                modifier = Modifier,
                onMove = { showMove = true },
                onPin = {
                    val allPinned = selected.all { Store.get(it)?.pinned == true }
                    Store.setPinned(selected, !allPinned); selected = emptySet()
                },
                onDelete = { Undo.trash(selected); selected = emptySet() },
            )
        }
        LaunchedEffect(selecting) { Nav.selecting = selecting }
        DisposableEffect(Unit) { onDispose { Nav.selecting = false } }
        AnimatedVisibility(
            !selecting, Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
            enter = slideInVertically(slide) { it } + fadeIn(), exit = slideOutVertically(slide) { it } + fadeOut(),
        ) {
            if (!Nav.isRoot) {
                Box(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars).padding(16.dp), contentAlignment = Alignment.BottomEnd) {
                    AddFab { Nav.captureSpace = spaceId ?: 0L; Nav.showCapture = true }
                }
            }
        }
    }

    if (showMove) SpacePickerDialog(onPick = { Store.moveToSpace(selected, it); selected = emptySet(); showMove = false }, onDismiss = { showMove = false })
}

private fun toggle(s: Set<Long>, id: Long) = if (id in s) s - id else s + id

@Composable
private fun SelectionBar(count: Int, onClose: () -> Unit, onAll: () -> Unit) {
    val c = T.c
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp).height(64.dp).clip(RoundedCornerShape(32.dp)).background(c.hi).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconCircle(Icons.Rounded.Close, "Cancel selection", onClose, container = c.hi)
        Text("$count selected", style = MaterialTheme.typography.titleLarge, color = c.tx, modifier = Modifier.weight(1f).padding(start = 8.dp))
        IconCircle(Icons.Rounded.SelectAll, "Select all", onAll, container = c.hi)
    }
}

@Composable
private fun SelectActionBar(modifier: Modifier, onMove: () -> Unit, onPin: () -> Unit, onDelete: () -> Unit) {
    val c = T.c
    Row(
        modifier.windowInsetsPadding(WindowInsets.navigationBars).padding(16.dp).fillMaxWidth().height(80.dp).clip(RoundedCornerShape(40.dp))
            .background(c.bar).border(1.dp, c.ol2, RoundedCornerShape(40.dp)).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        BarAction("Move", Icons.Rounded.Folder, onMove, primary = true)
        BarAction("Pin", Icons.Rounded.PushPin, onPin)
        BarAction("Delete", Icons.Rounded.Delete, onDelete)
    }
}

@Composable
private fun BarAction(label: String, icon: ImageVector, onClick: () -> Unit, primary: Boolean = false) {
    val c = T.c
    Column(
        Modifier.height(64.dp).widthIn(min = 76.dp).clip(RoundedCornerShape(28.dp)).background(if (primary) c.chip else androidx.compose.ui.graphics.Color.Transparent)
            .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, null, tint = if (primary) c.onChip else BarIcon, modifier = Modifier.size(22.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = if (primary) c.onChip else BarIcon)
    }
}

@Composable
fun AddFab(onClick: () -> Unit) {
    val c = T.c
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val squash by animateFloatAsState(if (pressed) 0.88f else 1f, spring(dampingRatio = 0.45f, stiffness = 500f), label = "fabScale")
    val round by animateFloatAsState(if (pressed) 36f else 26f, spring(dampingRatio = 0.6f, stiffness = 500f), label = "fabShape")
    val turn by animateFloatAsState(if (pressed) 90f else 0f, spring(dampingRatio = 0.5f, stiffness = 400f), label = "fabTurn")
    Box(
        Modifier.size(72.dp).graphicsLayer { scaleX = squash; scaleY = squash }.clip(RoundedCornerShape(round.dp)).background(c.fab)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick).semantics { contentDescription = "Save something" },
        contentAlignment = Alignment.Center,
    ) { Icon(Icons.Rounded.Add, null, tint = c.onFab, modifier = Modifier.size(32.dp).graphicsLayer { rotationZ = turn }) }
}

/**
 * The Material 3 Expressive floating toolbar: three destinations in a pill with the add button beside it.
 * The chosen destination grows into a labelled pill with a spring, as the expressive guidance describes.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MainToolbar(current: Route, modifier: Modifier = Modifier) {
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    fun go(r: Route) {
        if (Store.haptics) haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.ContextClick)
        Nav.tab(r)
    }
    androidx.compose.material3.HorizontalFloatingToolbar(
        expanded = true,
        modifier = modifier.windowInsetsPadding(WindowInsets.navigationBars).padding(16.dp),
        colors = androidx.compose.material3.FloatingToolbarDefaults.vibrantFloatingToolbarColors(),
        floatingActionButton = {
            androidx.compose.material3.FloatingToolbarDefaults.VibrantFloatingActionButton(
                onClick = { if (Store.haptics) haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.ContextClick); Nav.captureSpace = 0L; Nav.showCapture = true },
            ) { Icon(Icons.Rounded.Add, "Add", modifier = Modifier.size(28.dp)) }
        },
    ) {
        ToolbarTab("Library", Icons.Rounded.GridView, current == Route.Library) { go(Route.Library) }
        ToolbarTab("Spaces", Icons.Rounded.Folder, current == Route.Spaces) { go(Route.Spaces) }
        ToolbarTab("Search", Icons.Rounded.Search, current == Route.Search) { go(Route.Search) }
    }
}

@Composable
private fun ToolbarTab(label: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val bg by androidx.compose.animation.animateColorAsState(if (selected) scheme.primary else androidx.compose.ui.graphics.Color.Transparent, spring(dampingRatio = 0.8f, stiffness = 500f), label = "tab")
    val showLabel = selected || Store.tabLabels
    Row(
        Modifier.height(48.dp).animateContentSize(spring(dampingRatio = 0.7f, stiffness = 500f)).clip(RoundedCornerShape(24.dp)).background(bg)
            .clickable(role = Role.Tab, onClick = onClick).semantics { contentDescription = if (selected) "$label, selected" else label }
            .padding(horizontal = if (showLabel) 16.dp else 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(icon, null, tint = if (selected) scheme.onPrimary else scheme.onPrimaryContainer, modifier = Modifier.size(24.dp))
        if (showLabel) Text(label, style = MaterialTheme.typography.labelLarge, color = if (selected) scheme.onPrimary else scheme.onPrimaryContainer, maxLines = 1)
    }
}

@Composable
fun EmptyLibrary(inSpace: Boolean, smart: Boolean = false) {
    val c = T.c
    Column(Modifier.fillMaxSize().padding(horizontal = 28.dp).padding(bottom = 120.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        ExpressiveHero(Mood.Open)
        Text(if (smart) "Nothing fits yet" else if (inSpace) "This space is empty" else "Nothing saved yet", style = MaterialTheme.typography.displaySmall, color = c.tx, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.padding(top = 22.dp))
        Text(
            if (smart) "Anything you save that matches the rules will appear here"
            else if (inSpace) "Tap + to keep something here, or move items in from your Library"
            else "Share a link from any app, or tap + to add photos and notes. Everything stays on this phone",
            style = MaterialTheme.typography.bodyMedium, color = c.sec, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.padding(top = 10.dp),
        )
    }
}

@Composable
fun SpacePickerDialog(onPick: (Long) -> Unit, onDismiss: () -> Unit) {
    var creating by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    val c = T.c
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = c.card, titleContentColor = c.tx, textContentColor = c.tx,
        title = { Text("Move to space") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (creating) {
                    androidx.compose.material3.OutlinedTextField(name, { name = it }, singleLine = true, label = { Text("Space name") })
                } else {
                    LazyColumn(Modifier.heightIn(max = 320.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        item { PickRow("No space (Unsorted)") { onPick(0L) } }
                        items(Store.spaces.filter { it.rule.isBlank() }, key = { it.id }) { s -> PickRow(s.name) { onPick(s.id) } }
                        item { PickRow("+ New space") { creating = true } }
                    }
                }
            }
        },
        confirmButton = {
            if (creating) TextButton(onClick = { if (name.isNotBlank()) Store.addSpace(name) { onPick(it) } }) { Text("Create and move") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun PickRow(text: String, onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(T.c.hi).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp)) {
        Text(text, style = MaterialTheme.typography.titleSmall, color = T.c.tx)
    }
}

@Composable
fun ConfirmDialog(title: String, body: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val c = T.c
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = c.card, titleContentColor = c.tx, textContentColor = c.sec,
        title = { Text(title) }, text = { Text(body) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
