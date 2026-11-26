package app.lodestone.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import app.lodestone.data.SmartRule
import app.lodestone.data.SmartRules
import app.lodestone.data.Store
import app.lodestone.ui.*
import app.lodestone.ui.theme.Ink
import app.lodestone.ui.theme.T

private val typeChoices = listOf("LINK" to "Links", "IMAGE" to "Images", "VIDEO" to "Videos", "NOTE" to "Notes", "FILE" to "Files")
private val ageChoices = listOf(0 to "Any time", 7 to "Last week", 30 to "Last month", 180 to "Last 6 months", 365 to "Last year")

/** Builds or edits a Smart Space: a collection that fills itself from rules. */
@Composable
fun SmartBuilderScreen(editId: Long) {
    val c = T.c
    val existing = remember(editId) { Store.spaces.firstOrNull { it.id == editId } }
    val start = remember(editId) { SmartRule.parse(existing?.rule.orEmpty()) }
    var name by remember(editId) { mutableStateOf(existing?.name.orEmpty()) }
    var types by remember(editId) { mutableStateOf(start.types) }
    var kinds by remember(editId) { mutableStateOf(start.kinds) }
    var words by remember(editId) { mutableStateOf(start.words.joinToString(" ")) }
    var tags by remember(editId) { mutableStateOf(start.tags.joinToString(", ")) }
    var days by remember(editId) { mutableIntStateOf(start.days) }
    var confirmDelete by remember { mutableStateOf(false) }

    val rule = SmartRule(
        types, kinds, tags.split(',', ' ').map { it.trim().trimStart('#') }.filter { it.isNotEmpty() },
        words.split(' ', ',').map { it.trim() }.filter { it.isNotEmpty() }, days,
    )
    // Counted against the real library as you change the rules.
    val matches by produceState(emptyList(), rule, Store.live) { value = SmartRules.items(rule, Store.live) }
    val platformKinds = remember(Store.live) { Store.live.map { it.kind }.filter { it.isNotEmpty() }.distinct().sorted() }

    Column(Modifier.fillMaxSize().screenPadding()) {
        ScreenHeader(if (existing == null) "New Smart Space" else "Edit Smart Space", onBack = { Nav.back() })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("It fills itself. Anything you save that fits every rule you set shows up here.", style = MaterialTheme.typography.bodyMedium, color = c.sec, modifier = Modifier.padding(horizontal = 4.dp))
            OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Name") }, shape = RoundedCornerShape(20.dp))

            SectionTitle("Only these kinds")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                typeChoices.forEach { (k, l) -> FilterPill(l, k in types, { types = if (k in types) types - k else types + k }) }
            }
            if (platformKinds.isNotEmpty()) {
                SectionTitle("From")
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    platformKinds.forEach { k ->
                        val label = runCatching { app.lodestone.data.Platform.valueOf(k.uppercase()).label }.getOrDefault(k.replaceFirstChar { it.uppercase() })
                        FilterPill(label, k in kinds, { kinds = if (k in kinds) kinds - k else kinds + k })
                    }
                }
            }
            SectionTitle("Contains the words")
            OutlinedTextField(words, { words = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("For example: pasta lemon") }, shape = RoundedCornerShape(20.dp))
            Text("Looks in titles, notes, saved page text and the text inside pictures.", style = MaterialTheme.typography.bodySmall, color = c.sec, modifier = Modifier.padding(horizontal = 4.dp))
            SectionTitle("Tagged")
            OutlinedTextField(tags, { tags = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Tags, separated by commas") }, shape = RoundedCornerShape(20.dp))
            SectionTitle("Saved")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ageChoices.forEach { (d, l) -> FilterPill(l, days == d, { days = d }) }
            }

            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(c.card).padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column {
                    Text(if (rule.isEmpty) "Add at least one rule" else "${matches.size} ${if (matches.size == 1) "item matches" else "items match"} now", style = MaterialTheme.typography.titleSmall, color = c.tx)
                    Text("Updates as you save", style = MaterialTheme.typography.bodySmall, color = c.sec)
                }
            }
        }
        Row(Modifier.windowInsetsPadding(WindowInsets.navigationBars).padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (existing != null) IconCircle(Icons.Rounded.Delete, "Delete this space", { confirmDelete = true }, size = 64.dp, shape = RoundedCornerShape(24.dp), container = c.x[3], tint = Ink)
            PillButton(
                if (existing == null) "Create Smart Space" else "Save changes", {
                    if (existing == null) Store.addSpace(name.trim(), Store.spaces.size % 5, rule.toJson()) { Nav.back() }
                    else { Store.saveSpace(existing.copy(name = name.trim(), rule = rule.toJson())); Nav.back() }
                },
                Modifier.weight(1f), icon = Icons.Rounded.Check, enabled = name.isNotBlank() && !rule.isEmpty,
            )
        }
    }
    if (confirmDelete && existing != null) ConfirmDialog("Remove \"${existing.name}\"?", "Your items stay in the library. Only this collection goes away.", "Remove",
        { Store.deleteSpace(existing.id); confirmDelete = false; Nav.back() }, { confirmDelete = false })
}

