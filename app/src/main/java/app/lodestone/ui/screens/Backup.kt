package app.lodestone.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import app.lodestone.data.Backup
import app.lodestone.data.Store
import app.lodestone.ui.*
import app.lodestone.ui.theme.Ink
import app.lodestone.ui.theme.T
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BackupScreen() {
    val c = T.c
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var problem by remember { mutableStateOf(false) }

    val askNotifications = rememberNotificationAsk()
    fun run(block: suspend () -> Backup.Report, isBackup: Boolean = false, done: (Backup.Report) -> String) {
        askNotifications()
        busy = true; message = null
        scope.launch {
            val r = block()
            busy = false; problem = r.problem != null
            message = r.problem ?: done(r)
            app.lodestone.data.Notifier.finish(app.lodestone.data.Notifier.ID_BACKUP, if (r.problem != null) "That didn't work" else "Lodestone is done", message ?: "")
            if (isBackup && r.problem == null) Store.prefs.edit().putLong("lastBackup", System.currentTimeMillis()).apply()
        }
    }

    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) run({ Backup.export(uri) }, true) { "Backed up ${it.added} items and ${it.spaces} spaces." }
    }
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) run({ Backup.restore(uri) }) { "Restored ${it.added} items (${it.skipped} already here, ${it.spaces} new spaces)." }
    }
    val bookmarks = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) run({ Backup.importBookmarks(uri) }) { "Imported ${it.added} links (${it.skipped} already here, ${it.spaces} new spaces). Previews are fetched when you're online." }
    }

    val myMindFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) run({ app.lodestone.data.MyMind.import(uri, true) }) { "Imported ${it.added} items from mymind into a space called From mymind (${it.skipped} skipped). Link previews are fetched when you're online." }
    }
    val myMindFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) run({ app.lodestone.data.MyMind.import(uri, false) }) { "Imported ${it.added} items from mymind into a space called From mymind (${it.skipped} skipped). Link previews are fetched when you're online." }
    }

    val last = Store.prefs.getLong("lastBackup", 0L)
    Column(Modifier.fillMaxSize().screenPadding()) {
        ScreenHeader("Back up & import", onBack = { Nav.back() })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(40.dp, 40.dp, 40.dp, 12.dp)).background(c.x[1]).padding(22.dp)) {
                Text("Last backup", style = MaterialTheme.typography.labelMedium, color = Ink.copy(alpha = .8f))
                Text(
                    if (last == 0L) "Never" else java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM).format(Date(last)),
                    style = MaterialTheme.typography.displaySmall, color = Ink,
                )
                Text("${Store.items.size} items on this phone", style = MaterialTheme.typography.bodyMedium, color = Ink.copy(alpha = .85f))
            }
            PillButton("Back up now", {
                export.launch("lodestone-" + SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) + ".zip")
            }, Modifier.fillMaxWidth(), icon = Icons.Rounded.Download, enabled = !busy)
            Text(
                "Your library lives only on this phone. A backup is one file with everything inside: items, images, videos, files and spaces. Keep it somewhere safe, like a cloud drive or your computer.",
                style = MaterialTheme.typography.bodySmall, color = c.sec, modifier = Modifier.padding(horizontal = 6.dp),
            )

            SectionTitle("Bring things in")
            SettingRow("Restore from a backup", "Merges into your library, never overwrites", Icons.Rounded.Restore, c.x[0], { if (!busy) restore.launch(arrayOf("*/*")) })
            SettingRow("Browser bookmarks", "A bookmarks.html file. Folders become spaces", Icons.Rounded.Bookmark, c.x[2], { if (!busy) bookmarks.launch(arrayOf("text/html", "*/*")) })

            SectionTitle("From mymind")
            Text(
                "On a computer, open your mind in Chrome or Edge, click the cog, then Export my mind, and pick a folder. Move that folder to this phone, or zip it and move the zip. " +
                    "Notes, quotes, links and highlights come in from cards.csv, and every picture and PDF comes in as a card. Everything goes into a space called From mymind, so you can sort it afterwards.",
                style = MaterialTheme.typography.bodySmall, color = c.sec, modifier = Modifier.padding(horizontal = 6.dp),
            )
            SettingRow("mymind folder", "The folder mymind exported", Icons.Rounded.FolderOpen, c.x[3], { if (!busy) myMindFolder.launch(null) })
            SettingRow("mymind zip or cards.csv", "If you zipped the export, or only have the csv", Icons.Rounded.FolderZip, c.x[4], { if (!busy) myMindFile.launch(arrayOf("application/zip", "text/csv", "text/comma-separated-values", "*/*")) })

            if (busy) Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                LoadingIndicator(Modifier.size(56.dp))
                Text("Working… keep Lodestone open", style = MaterialTheme.typography.bodyMedium, color = c.sec, modifier = Modifier.padding(start = 12.dp))
            }
            message?.let {
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(if (problem) c.x[3] else c.card).padding(18.dp)) {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = if (problem) Ink else c.tx)
                }
            }
        }
    }
}
