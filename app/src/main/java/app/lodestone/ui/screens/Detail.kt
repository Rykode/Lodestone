package app.lodestone.ui.screens

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.MediaController
import android.widget.Toast
import android.widget.VideoView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import app.lodestone.data.Embeds
import app.lodestone.data.Ingest
import app.lodestone.data.Facts
import app.lodestone.data.Item
import app.lodestone.data.Related
import app.lodestone.data.Summarizer
import androidx.compose.foundation.horizontalScroll
import app.lodestone.data.Ocr
import androidx.compose.foundation.text.selection.SelectionContainer
import app.lodestone.data.ItemType
import app.lodestone.data.Store
import app.lodestone.ui.*
import app.lodestone.ui.theme.Ink
import app.lodestone.ui.theme.T
import kotlinx.coroutines.launch
import java.io.File
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DetailScreen(id: Long) {
    val item = Store.items.firstOrNull { it.id == id }
    val c = T.c
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    if (item == null) { LaunchedEffect(Unit) { Nav.back() }; return }

    var editing by remember(id) { mutableStateOf(false) }
    var title by remember(id) { mutableStateOf(item.title) }
    var note by remember(id) { mutableStateOf(item.note) }
    var tags by remember(id) { mutableStateOf(item.tags.joinToString(", ")) }
    var textVersion by remember(id) { mutableIntStateOf(0) }
    val pageText by produceState("", item.id, item.enriched, textVersion) { value = Store.textOf(item.id) }
    var showMove by remember { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }
    val pickShot = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.launch {
            val ok = Ingest.attachScreenshot(item.id, uri)
            if (!ok) Toast.makeText(ctx, "Couldn't use that picture", Toast.LENGTH_SHORT).show()
        }
    }
    val blocked = item.extra.contains("\"blocked\":true")
    val related = remember(item.id, item.tags, item.title, item.color, Store.live) { Related.top(item, Store.live, 12) }

    Column(Modifier.fillMaxSize().screenPadding()) {
        ScreenHeader(
            "", onBack = { Nav.back() },
            trailing = {
                IconCircle(if (item.pinned) Icons.Rounded.PushPin else Icons.Rounded.PushPin, if (item.pinned) "Unpin" else "Pin", { Store.setPinned(listOf(item.id), !item.pinned) },
                    container = if (item.pinned) c.chip else c.hi, tint = if (item.pinned) c.onChip else c.tx)
                IconCircle(Icons.Rounded.Edit, if (editing) "Done editing" else "Edit", {
                    if (editing) {
                        Store.edit(item.id) { it.copy(title = title.trim(), note = note, tags = tags.split(',', '\n').map { t -> t.trim().trimStart('#') }.filter { t -> t.isNotEmpty() }.distinct()) }
                    }
                    if (!editing) { title = item.title; note = item.note; tags = item.tags.joinToString(", ") }
                    editing = !editing
                }, container = if (editing) c.fab else c.hi, tint = if (editing) c.onFab else c.tx)
            },
        )
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Hero(item)

            if (editing) {
                OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth(), label = { Text("Title") }, shape = RoundedCornerShape(20.dp))
            } else {
                Text(item.title.ifBlank { "Untitled" }, style = MaterialTheme.typography.headlineLarge, color = c.tx, modifier = Modifier.padding(horizontal = 4.dp))
            }
            Text(
                listOfNotNull(
                    item.type.name.lowercase().replaceFirstChar { it.uppercase() },
                    item.domain.takeIf { it.isNotEmpty() },
                    "Saved " + DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(item.created)),
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall, color = c.sec, modifier = Modifier.padding(horizontal = 4.dp),
            )

            val facts = remember(item.extra, item.kind) { Facts.line(item) }
            if (facts.isNotEmpty()) Text(facts, style = MaterialTheme.typography.titleSmall, color = c.fab, modifier = Modifier.padding(horizontal = 4.dp))

            if (blocked && item.type == ItemType.LINK) {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(c.x[3]).padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("This page wouldn't save", style = MaterialTheme.typography.titleMedium, color = Ink)
                    Text("The site asked for a login or turned the app away, so only the address was kept. You can add your own screenshot of the page instead.", style = MaterialTheme.typography.bodyMedium, color = Ink)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        PillButton("Add a screenshot", { pickShot.launch("image/*") }, container = Ink, content = c.x[3], icon = Icons.Rounded.Image, height = 44.dp)
                        PillButton("Try again", { refreshing = true; scope.launch { Ingest.enrich(item.id, force = true); refreshing = false; textVersion++ } }, container = c.bg.copy(alpha = .35f), content = Ink, icon = Icons.Rounded.Refresh, height = 44.dp, enabled = !refreshing)
                        PillButton("Keep the link only", { Ingest.keepLinkOnly(item.id) }, container = c.bg.copy(alpha = .35f), content = Ink, height = 44.dp)
                    }
                }
            }

            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (item.type == ItemType.LINK) PillButton("Open original", { openUrl(ctx, item.url) }, container = c.fab, content = c.onFab, icon = Icons.Rounded.OpenInNew, height = 48.dp)
                else if (item.file.isNotEmpty() && item.type != ItemType.VIDEO) PillButton("Open", { openFile(ctx, item) }, container = c.fab, content = c.onFab, icon = Icons.Rounded.OpenInNew, height = 48.dp)
                if (item.type == ItemType.LINK) PillButton(if (refreshing) "Fetching…" else "Refresh preview", {
                    refreshing = true
                    scope.launch { Ingest.enrich(item.id, force = true); refreshing = false; textVersion++ }
                }, container = c.hi, content = c.tx, icon = Icons.Rounded.Refresh, height = 48.dp, enabled = !refreshing)
                PillButton("Share", { share(ctx, item) }, container = c.hi, content = c.tx, icon = Icons.Rounded.Share, height = 48.dp)
                PillButton("Move", { showMove = true }, container = c.hi, content = c.tx, icon = Icons.Rounded.Folder, height = 48.dp)
                PillButton("Delete", { Undo.trash(listOf(item.id)); Nav.back() }, container = c.x[3], content = Ink, icon = Icons.Rounded.Delete, height = 48.dp)
            }

            val space = Store.spaces.firstOrNull { it.id == item.spaceId }
            Text("In " + (space?.name ?: "Unsorted"), style = MaterialTheme.typography.labelLarge, color = c.sec, modifier = Modifier.padding(horizontal = 4.dp))

            if (editing) {
                OutlinedTextField(note, { note = it }, Modifier.fillMaxWidth().heightIn(min = 120.dp), label = { Text("Your note") }, shape = RoundedCornerShape(20.dp))
                OutlinedTextField(tags, { tags = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Tags, separated by commas") }, shape = RoundedCornerShape(20.dp))
            } else {
                if (item.note.isNotBlank() && item.type != ItemType.NOTE) {
                    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(c.card).padding(18.dp)) {
                        Text(item.note, style = MaterialTheme.typography.bodyLarge, color = c.tx)
                    }
                } else if (item.type == ItemType.NOTE) {
                    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(itemColor(item, c.x)).padding(20.dp)) {
                        Text(item.note, style = MaterialTheme.typography.bodyLarge, color = Ink)
                    }
                }
                if (item.tags.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    item.tags.forEach { t ->
                        Box(Modifier.height(36.dp).clip(RoundedCornerShape(14.dp)).background(c.chip).clickable { Nav.tab(Route.Search); Nav.pendingQuery = t }.padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
                            Text("#$t", style = MaterialTheme.typography.labelLarge, color = c.onChip)
                        }
                    }
                }
            }

            if ((item.type == ItemType.IMAGE || item.kind == "pdf") && pageText.isNotBlank()) {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(c.card).padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (item.kind == "pdf") "Text in this PDF" else "Text in this picture", style = MaterialTheme.typography.titleMedium, color = c.tx)
                    SelectionContainer { Text(pageText, style = MaterialTheme.typography.bodyMedium, color = c.tx) }
                    PillButton("Copy text", {
                        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        cm.setPrimaryClip(android.content.ClipData.newPlainText("Text from picture", pageText))
                        Toast.makeText(ctx, "Copied", Toast.LENGTH_SHORT).show()
                    }, container = c.hi, content = c.tx, icon = Icons.Rounded.ContentCopy, height = 44.dp)
                }
            } else if (item.type == ItemType.IMAGE && !Ocr.isDone(item)) {
                Text("Reading the text in this picture…", style = MaterialTheme.typography.bodySmall, color = c.sec, modifier = Modifier.padding(horizontal = 4.dp))
            }
            if (related.isNotEmpty()) {
                Row(Modifier.fillMaxWidth().clickable { Nav.push(Route.Vibe(item.id)) }.padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Goes with this", style = MaterialTheme.typography.titleMedium, color = c.tx, modifier = Modifier.weight(1f))
                    Text("See all ${related.size}", style = MaterialTheme.typography.labelLarge, color = c.fab)
                }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    related.take(8).forEach { r ->
                        Box(Modifier.size(96.dp, 84.dp).clip(RoundedCornerShape(22.dp)).background(itemColor(r, c.x)).clickable { Nav.push(Route.Detail(r.id)) }) {
                            if (r.thumb.isNotEmpty()) LocalPicture(thumbDir, r.thumb, Modifier.matchParentSize(), ContentScale.Crop, 300)
                            else Text(r.title.ifBlank { "Note" }, style = MaterialTheme.typography.labelSmall, color = Ink, maxLines = 3, modifier = Modifier.padding(10.dp))
                        }
                    }
                }
                Text("Matched by shared tags, topic words and picture colors.", style = MaterialTheme.typography.bodySmall, color = c.sec, modifier = Modifier.padding(horizontal = 4.dp))
            }
            if (item.type == ItemType.LINK) {
                val keySentences = remember(pageText) { Summarizer.summarize(pageText, 3) }
                if (keySentences.isNotEmpty()) {
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(c.x[2]).padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Key sentences", style = MaterialTheme.typography.titleMedium, color = Ink)
                        keySentences.forEach { Text("•  $it", style = MaterialTheme.typography.bodyMedium, color = Ink) }
                        Text("Picked from the page by Lodestone. Nothing was written for you.", style = MaterialTheme.typography.bodySmall, color = Ink.copy(alpha = .75f))
                    }
                }
                Text("Reader", style = MaterialTheme.typography.titleMedium, color = c.tx, modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp))
                if (pageText.isNotBlank()) {
                    Text(pageText, style = MaterialTheme.typography.bodyLarge, color = c.tx, modifier = Modifier.padding(horizontal = 4.dp))
                } else {
                    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(c.card).padding(18.dp)) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                if (item.enriched) "No readable text was found on this page." else "The page copy hasn't been fetched yet. Lodestone tries again when you're online.",
                                style = MaterialTheme.typography.bodyMedium, color = c.sec,
                            )
                            PillButton(if (refreshing) "Fetching…" else "Try now", {
                                refreshing = true
                                scope.launch { Ingest.enrich(item.id); refreshing = false; textVersion++ }
                            }, container = c.hi, content = c.tx, icon = Icons.Rounded.Refresh, height = 44.dp, enabled = !refreshing)
                        }
                    }
                }
            }
        }
    }

    if (showMove) SpacePickerDialog(onPick = { Store.moveToSpace(listOf(item.id), it); showMove = false }, onDismiss = { showMove = false })
}

@Composable
private fun Hero(item: Item) {
    val c = T.c
    val ctx = LocalContext.current
    when (item.type) {
        ItemType.IMAGE -> {
            val img = rememberLocalImage(mediaDir, item.file, 1600)
            val ratio = if (item.aspect > 0f) (1f / item.aspect).coerceIn(0.5f, 2f) else 1f
            Box(Modifier.fillMaxWidth().aspectRatio(ratio).clip(RoundedCornerShape(36.dp, 36.dp, 56.dp, 36.dp)).background(c.hi)) {
                if (img != null) androidx.compose.foundation.Image(img, item.title, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
            }
        }
        ItemType.VIDEO -> {
            var playing by remember(item.id) { mutableStateOf(false) }
            Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(32.dp, 32.dp, 32.dp, 12.dp)).background(c.deep)) {
                if (playing) {
                    AndroidView(
                        factory = { cx ->
                            VideoView(cx).apply {
                                setVideoURI(Uri.fromFile(File(mediaDir, item.file)))
                                setMediaController(MediaController(cx).also { it.setAnchorView(this) })
                                setOnPreparedListener { start() }
                            }
                        },
                        modifier = Modifier.matchParentSize(),
                    )
                } else {
                    LocalPicture(thumbDir, item.thumb, Modifier.matchParentSize(), ContentScale.Crop)
                    Box(Modifier.align(Alignment.Center).size(72.dp).clip(RoundedCornerShape(26.dp)).background(c.x[2]).clickable { playing = true }, contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.PlayArrow, "Play video", tint = Ink, modifier = Modifier.size(36.dp))
                    }
                }
            }
        }
        ItemType.LINK -> {
            val embed = remember(item.url, c.dark) { Embeds.parse(item.url, dark = c.dark) }
            var playing by remember(item.id) { mutableStateOf(false) }
            if (embed != null) {
                // Same height for the thumbnail and the player, so nothing jumps when you tap play.
                val ratio = embed.aspect
                Box(Modifier.fillMaxWidth().aspectRatio(1f / ratio).clip(RoundedCornerShape(36.dp, 36.dp, 56.dp, 36.dp)).background(c.deep)) {
                    if (playing) {
                        EmbedPlayer(embed, Modifier.matchParentSize())
                    } else {
                        if (item.thumb.isNotEmpty()) LocalPicture(thumbDir, item.thumb, Modifier.matchParentSize(), ContentScale.Crop, 1400)
                        Box(Modifier.align(Alignment.Center).size(72.dp).clip(RoundedCornerShape(26.dp)).background(c.fab).clickable { playing = true }, contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.PlayArrow, "Play ${embed.platform.label} embed", tint = c.onFab, modifier = Modifier.size(36.dp))
                        }
                        Box(Modifier.align(Alignment.TopStart).padding(14.dp).clip(RoundedCornerShape(12.dp)).background(c.bg.copy(alpha = .85f)).padding(horizontal = 10.dp, vertical = 5.dp)) {
                            Text(embed.platform.label, style = MaterialTheme.typography.labelMedium, color = c.tx)
                        }
                    }
                }
            } else if (item.thumb.isNotEmpty()) {
                Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(36.dp, 36.dp, 56.dp, 36.dp)).background(c.hi)) {
                    LocalPicture(thumbDir, item.thumb, Modifier.matchParentSize(), ContentScale.Crop, 1400)
                }
            }
        }
        ItemType.FILE -> if (item.thumb.isNotEmpty()) {
            Box(Modifier.fillMaxWidth().aspectRatio(0.78f).clip(RoundedCornerShape(36.dp, 36.dp, 56.dp, 36.dp)).background(androidx.compose.ui.graphics.Color.White)) {
                LocalPicture(thumbDir, item.thumb, Modifier.matchParentSize(), ContentScale.Crop, 1400)
            }
        } else Box(Modifier.fillMaxWidth().height(160.dp).clip(RoundedCornerShape(36.dp, 36.dp, 56.dp, 36.dp)).background(itemColor(item, c.x)), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Description, null, tint = Ink, modifier = Modifier.size(64.dp))
        }
        ItemType.NOTE -> {}
    }
}

fun openUrl(ctx: Context, url: String) {
    try { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    catch (_: ActivityNotFoundException) { Toast.makeText(ctx, "No app can open this link", Toast.LENGTH_SHORT).show() }
}

private fun fileUri(ctx: Context, item: Item): Uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", File(Store.mediaDir, item.file))

fun openFile(ctx: Context, item: Item) {
    try {
        ctx.startActivity(
            Intent(Intent.ACTION_VIEW).setDataAndType(fileUri(ctx, item), item.mime.ifEmpty { "*/*" })
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    } catch (_: ActivityNotFoundException) { Toast.makeText(ctx, "No app can open this file", Toast.LENGTH_SHORT).show() }
}

fun share(ctx: Context, item: Item) {
    val send = Intent(Intent.ACTION_SEND)
    if (item.file.isNotEmpty()) {
        send.type = item.mime.ifEmpty { "*/*" }
        send.putExtra(Intent.EXTRA_STREAM, fileUri(ctx, item)).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    } else {
        send.type = "text/plain"
        send.putExtra(Intent.EXTRA_TEXT, if (item.type == ItemType.LINK) item.url else item.note.ifBlank { item.title })
    }
    ctx.startActivity(Intent.createChooser(send, "Share").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
