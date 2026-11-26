package app.lodestone.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.lodestone.BuildConfig
import app.lodestone.data.Ingest
import app.lodestone.data.Store
import app.lodestone.ui.*
import app.lodestone.ui.theme.Ink
import app.lodestone.ui.theme.AvailableSchemes
import app.lodestone.ui.theme.Schemes
import app.lodestone.ui.theme.T
import kotlinx.coroutines.launch

private val modeNames = listOf("System", "Light", "Dark")

@Composable
fun SettingsScreen() {
    val c = T.c
    val scope = rememberCoroutineScope()
    val askNotifications = rememberNotificationAsk()
    val pendingPreviews = Store.live.count { it.type == app.lodestone.data.ItemType.LINK && !it.enriched }
    Column(Modifier.fillMaxSize().screenPadding()) {
        ScreenHeader("Settings", onBack = { Nav.back() })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            StorageCard()
            SettingRow(
                "Share sheet", if (Store.quickShare) "Sharing to Lodestone saves instantly" else "Sharing to Lodestone asks first",
                Icons.Rounded.Share, c.x[1], onClick = { Store.quickShare = !Store.quickShare; Store.savePrefs() },
                trailing = { ExpressiveSwitch(Store.quickShare) { Store.quickShare = it; Store.savePrefs() } },
            )
            SettingRow("Appearance", "${AvailableSchemes.getOrElse(Store.schemeIdx) { Schemes[0] }.name} · ${modeNames[Store.mode]}", Icons.Rounded.Palette, c.x[3], { Nav.push(Route.Appearance) })
            SettingRow("App icon", app.lodestone.data.AppIcons.current(androidx.compose.ui.platform.LocalContext.current).name, Icons.Rounded.Apps, c.x[4], { Nav.push(Route.AppIcon) })
            SettingRow("Back up & import", "Export everything, restore, bring in bookmarks", Icons.Rounded.Download, c.x[0], { Nav.push(Route.Backup) })
            SettingRow("Resurface", "Look at something you saved a while ago", Icons.Rounded.History, c.x[1], { Nav.push(Route.Resurface) })
            SettingRow("Recently deleted", "${Store.trashed.size} ${if (Store.trashed.size == 1) "item" else "items"} · cleared after 30 days", Icons.Rounded.Delete, c.x[2], { Nav.push(Route.Trash) })
            if (pendingPreviews > 0) SettingRow(
                "Fetch missing previews", "$pendingPreviews links saved without a preview", Icons.Rounded.Refresh, c.x[4],
                { askNotifications(); scope.launch { Ingest.enrichAll() } },
            )
            SettingRow("About Lodestone", "Version ${BuildConfig.VERSION_NAME} · privacy · licences", Icons.Rounded.Info, c.hi, { Nav.push(Route.About) })
            Text(
                "No account. No cloud. Lodestone goes online only to fetch pages and post details for links you saved or imported.",
                style = MaterialTheme.typography.bodySmall, color = c.sec, modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            )
        }
    }
}

@Composable
private fun StorageCard() {
    val c = T.c
    val bytes by produceState(0L, Store.items.size) { value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { dirSize(Store.mediaDir) + dirSize(Store.thumbDir) } }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(40.dp, 40.dp, 40.dp, 12.dp)).background(c.deep).padding(22.dp)) {
        Text("Stored on this phone", style = MaterialTheme.typography.labelMedium, color = androidx.compose.ui.graphics.Color(0xFFC9C6BC))
        Text(humanSize(bytes), style = MaterialTheme.typography.displayMedium, color = androidx.compose.ui.graphics.Color(0xFFF4F2ED))
        Text("${Store.live.size} ${if (Store.live.size == 1) "item" else "items"}", style = MaterialTheme.typography.bodyMedium, color = androidx.compose.ui.graphics.Color(0xFFE9E6DE))
    }
}

private fun dirSize(d: java.io.File): Long = d.listFiles()?.sumOf { it.length() } ?: 0L

fun humanSize(b: Long): String = when {
    b >= 1L shl 30 -> "%.1f GB".format(b / (1L shl 30).toDouble())
    b >= 1L shl 20 -> "%.1f MB".format(b / (1L shl 20).toDouble())
    b >= 1L shl 10 -> "%.0f KB".format(b / 1024.0)
    else -> "$b B"
}

@Composable
fun AppearanceScreen() {
    val c = T.c
    Column(Modifier.fillMaxSize().screenPadding()) {
        ScreenHeader("Appearance", onBack = { Nav.back() })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            ModePicker()
            SettingRow(
                "OLED black", if (Store.oled) "Dark mode uses pure black" else "Dark mode uses soft black",
                Icons.Rounded.DarkMode, c.x[3], onClick = { Store.oled = !Store.oled; Store.savePrefs() },
                trailing = { ExpressiveSwitch(Store.oled) { Store.oled = it; Store.savePrefs() } },
            )
            SectionTitle("Color scheme")
            SchemeGrid()
            AppearanceExtras()
            SectionTitle("Preview")
            Row(Modifier.fillMaxWidth().height(72.dp).clip(RoundedCornerShape(36.dp)).background(c.bar).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("All", c.chip, c.onChip)
                Chip("Links", c.x[0], Ink)
                Chip("Notes", c.x[1], Ink)
                Spacer(Modifier.weight(1f))
                Box(Modifier.size(52.dp).clip(RoundedCornerShape(20.dp)).background(c.fab), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Add, null, tint = c.onFab) }
            }
        }
    }
}

@Composable
private fun Chip(text: String, bg: androidx.compose.ui.graphics.Color, fg: androidx.compose.ui.graphics.Color) {
    Box(Modifier.height(40.dp).clip(RoundedCornerShape(20.dp)).background(bg).padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = fg)
    }
}

@Composable
private fun Icon(icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String?, tint: androidx.compose.ui.graphics.Color) =
    androidx.compose.material3.Icon(icon, desc, tint = tint)

/** Connected button group: the chosen mode is a full pill, the others stay rounded rectangles. */
@Composable
fun ModePicker() {
    val c = T.c
    Row(Modifier.fillMaxWidth().height(56.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        modeNames.forEachIndexed { i, name ->
            val on = Store.mode == i
            Box(
                Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(if (on) 28.dp else 14.dp)).background(if (on) c.chip else c.hi)
                    .clickable(role = Role.RadioButton) { Store.mode = i; Store.savePrefs() },
                contentAlignment = Alignment.Center,
            ) { Text(name, style = MaterialTheme.typography.labelLarge, color = if (on) c.onChip else c.tx) }
        }
    }
}

@Composable
fun SchemeGrid() {
    val c = T.c
    val dark = c.dark
    AvailableSchemes.chunked(3).forEach { row ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            row.forEach { s ->
                val idx = Schemes.indexOf(s)
                val on = Store.schemeIdx == idx
                val t = if (dark) s.dark else s.light
                val shape = if (on) RoundedCornerShape(28.dp, 28.dp, 28.dp, 8.dp) else RoundedCornerShape(20.dp)
                Column(
                    Modifier.weight(1f).height(88.dp).clip(shape).background(c.card).border(if (on) 3.dp else 1.dp, if (on) t.fab else c.ol1, shape)
                        .clickable(role = Role.RadioButton) { Store.schemeIdx = idx; Store.savePrefs() }.padding(12.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row {
                        Box(Modifier.size(26.dp).clip(androidx.compose.foundation.shape.CircleShape).background(t.fab))
                        Box(Modifier.offset((-6).dp).size(26.dp).clip(RoundedCornerShape(9.dp)).background(t.x[0]))
                        Box(Modifier.offset((-12).dp).size(26.dp).clip(androidx.compose.foundation.shape.CircleShape).background(t.x[1]))
                    }
                    Text(s.name, style = MaterialTheme.typography.labelMedium, color = c.tx, maxLines = 1)
                }
            }
        }
    }
}

@Composable
fun TrashScreen() {
    val c = T.c
    val items = Store.trashed
    var confirmEmpty by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().screenPadding()) {
            ScreenHeader("Recently deleted", onBack = { Nav.back() })
            Text("Items stay here for 30 days, then they are removed from this phone for good.", style = MaterialTheme.typography.bodyMedium, color = c.sec, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
            if (items.isEmpty()) {
                Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    ExpressiveHero(Mood.Sleep)
                    Text("Nothing here", color = c.sec, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp))
                }
            } else LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 120.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(items, key = { it.id }) { i ->
                    val days = (30 - (System.currentTimeMillis() - i.deleted) / 86_400_000L).coerceAtLeast(0)
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(c.card).padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.size(64.dp).clip(RoundedCornerShape(18.dp)).background(c.hi)) { LocalPicture(thumbDir, i.thumb, Modifier.matchParentSize(), maxDim = 200) }
                        Column(Modifier.weight(1f)) {
                            Text(i.title.ifBlank { "Untitled" }, style = MaterialTheme.typography.titleSmall, color = c.tx, maxLines = 2)
                            Text("${i.type.name.lowercase().replaceFirstChar { it.uppercase() }} · $days days left", style = MaterialTheme.typography.bodySmall, color = c.sec)
                        }
                        Box(Modifier.height(44.dp).clip(RoundedCornerShape(22.dp)).background(c.chip).clickable(role = Role.Button) { Store.restore(listOf(i.id)) }.padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
                            Text("Restore", style = MaterialTheme.typography.labelLarge, color = c.onChip)
                        }
                    }
                }
            }
        }
        if (items.isNotEmpty()) Row(
            Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.navigationBars).padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            PillButton("Restore all", { Store.restore(items.map { it.id }) }, Modifier.weight(1f))
            PillButton("Empty now", { confirmEmpty = true }, Modifier.weight(1f), container = c.x[3], content = Ink)
        }
    }
    if (confirmEmpty) ConfirmDialog("Delete ${items.size} items for good?", "This can't be undone.", "Delete forever", { Store.deleteForever(items.map { it.id }); confirmEmpty = false }, { confirmEmpty = false })
}

@Composable
fun AboutScreen() {
    val c = T.c
    val ctx = LocalContext.current
    val license = remember { runCatching { ctx.assets.open("licenses/RobotoFlex-OFL.txt").bufferedReader().readText() }.getOrDefault("") }
    Column(Modifier.fillMaxSize().screenPadding()) {
        ScreenHeader("About", onBack = { Nav.back() })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Mascot(Mood.Wink, 64.dp)
                Text("Lodestone ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.headlineSmall, color = c.tx)
            }
            Text(
                "Lodestone keeps links, images, videos, files and notes on this phone. There is no account, no cloud, no analytics and no ads. " +
                    "The only network use is fetching a saved link's page (its title, text and preview image) and, for posts on platforms such as X, Bluesky, Vimeo, Instagram and Threads, asking that platform for the post's details. Requests go straight to those sites with no Lodestone server in between. Lodestone also retries links that haven't loaded yet when it starts.",
                style = MaterialTheme.typography.bodyLarge, color = c.tx,
            )
            Text(
                "Text inside pictures and PDFs is read by Tesseract, an open-source reader that runs on this phone and sends nothing anywhere. Posts you play inside Lodestone (YouTube, Instagram and others) start playing from that platform only when you tap play. Your library is stored in the app's private storage. Uninstalling the app deletes it, so use Back up regularly and keep the file somewhere safe.",
                style = MaterialTheme.typography.bodyLarge, color = c.tx,
            )
            SectionTitle("Not built yet")
            Text(
                "Reading handwriting, links between notes, summaries written by an AI and app lock are not part of this version. The key sentences shown for articles are picked from the page, not written.",
                style = MaterialTheme.typography.bodyMedium, color = c.sec,
            )
            SectionTitle("Licences")
            Text("Tesseract OCR and its English language data, Apache License 2.0", style = MaterialTheme.typography.titleSmall, color = c.tx)
            Text("Roboto Flex, SIL Open Font License 1.1", style = MaterialTheme.typography.titleSmall, color = c.tx)
            Text(license, style = MaterialTheme.typography.bodySmall, color = c.sec)
        }
    }
}
