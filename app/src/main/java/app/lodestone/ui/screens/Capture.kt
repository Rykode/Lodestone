package app.lodestone.ui.screens

import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.lodestone.data.Ingest
import app.lodestone.data.Store
import app.lodestone.ui.*
import app.lodestone.ui.theme.T
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The "Save something" sheet. Also opens pre-filled when something is shared in from another app. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaptureSheet() {
    val ctx = LocalContext.current
    val c = T.c
    val pending = Store.pending
    val first = remember { pending }
    var text by remember { mutableStateOf(pending?.text.orEmpty()) }
    var uris by remember { mutableStateOf(pending?.uris.orEmpty()) }
    // Another share arriving while the sheet is open is added to it, never replaces what is there.
    LaunchedEffect(pending) {
        val p = pending
        if (p != null && p !== first) {
            if (p.text.isNotBlank() && !text.contains(p.text)) text = if (text.isBlank()) p.text else text.trimEnd() + "\n" + p.text
            uris = (uris + p.uris).distinct()
        }
    }
    var tags by remember { mutableStateOf("") }
    var spaceId by remember { mutableLongStateOf(Nav.captureSpace) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current

    fun close() { Store.pending = null; Nav.showCapture = false }

    val pickMedia = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris = uris + it }
    val pickFiles = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris = uris + it }

    ModalBottomSheet(
        onDismissRequest = { close() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.card, contentColor = c.tx,
        shape = RoundedCornerShape(40.dp, 40.dp, 0.dp, 0.dp),
    ) {
        Column(
            Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Save something", style = MaterialTheme.typography.headlineMedium, color = c.tx)

            OutlinedTextField(
                value = text, onValueChange = { text = it }, modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                label = { Text("Paste a link or write a note") }, shape = RoundedCornerShape(24.dp),
            )
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionChip("Paste", Icons.Rounded.ContentPaste) {
                    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(ctx)?.toString().orEmpty()
                    if (clip.isNotBlank()) text = clip else Toast.makeText(ctx, "Clipboard is empty", Toast.LENGTH_SHORT).show()
                }
                ActionChip("Photos & videos", Icons.Rounded.Image) { pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) }
                ActionChip("File", Icons.Rounded.Description) { pickFiles.launch(arrayOf("*/*")) }
            }
            if (uris.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${uris.size} attached", style = MaterialTheme.typography.labelLarge, color = c.sec, modifier = Modifier.align(androidx.compose.ui.Alignment.CenterVertically))
                    TextButton(onClick = { uris = emptyList() }) { Text("Clear") }
                }
            }

            OutlinedTextField(
                value = tags, onValueChange = { tags = it }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                label = { Text("Tags, comma separated") }, shape = RoundedCornerShape(24.dp),
            )

            Text("Space", style = MaterialTheme.typography.labelLarge, color = c.sec)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterPill("Unsorted", spaceId == 0L, { spaceId = 0L })
                Store.spaces.filter { it.rule.isBlank() }.forEach { s -> FilterPill(s.name, spaceId == s.id, { spaceId = s.id }) }
            }

            PillButton(
                text = if (saving) "Keeping…" else "Keep it", icon = Icons.Rounded.Check, modifier = Modifier.fillMaxWidth(),
                enabled = !saving && (text.isNotBlank() || uris.isNotEmpty()),
                onClick = {
                    if (Store.haptics) haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.Confirm)
                    val tagList = tags.split(',', '\n').map { it.trim().trimStart('#') }.filter { it.isNotEmpty() }.distinct()
                    // Runs in the app's own scope and closes the sheet at once, so dismissing it can't cancel a save.
                    val toSave = text
                    val filesToSave = uris
                    close()
                    Store.scope.launch {
                        val r = Ingest.keep(toSave, filesToSave, tagList, spaceId)
                        val msg = app.lodestone.data.SharedIntent.message(r)
                        withContext(Dispatchers.Main) { Toast.makeText(Store.app, msg, Toast.LENGTH_SHORT).show() }
                    }
                },
            )
            Text(
                "Links are saved instantly. Lodestone fetches the page preview and a readable copy when you're online.",
                style = MaterialTheme.typography.bodySmall, color = c.sec,
            )
        }
    }
}

@Composable
private fun ActionChip(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    val c = T.c
    androidx.compose.foundation.layout.Row(
        Modifier.height(44.dp).clip(RoundedCornerShape(22.dp)).background(c.hi).clickable(onClick = onClick).padding(horizontal = 16.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(icon, null, tint = c.tx, modifier = Modifier.size(20.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = c.tx)
    }
}
