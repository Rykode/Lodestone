package app.lodestone.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.lodestone.data.Store
import app.lodestone.ui.*
import app.lodestone.ui.theme.T

/** A title and a row of choices. The chosen one is a full pill, the others stay rounded rectangles. */
@Composable
fun ChoiceRow(title: String, options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val c = T.c
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = c.tx, modifier = Modifier.padding(horizontal = 6.dp))
        Row(Modifier.fillMaxWidth().height(52.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            options.forEachIndexed { i, name ->
                val on = selected == i
                Box(
                    Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(if (on) 26.dp else 14.dp)).background(if (on) c.chip else c.hi)
                        .clickable(role = Role.RadioButton) { onSelect(i); Store.savePrefs() },
                    contentAlignment = Alignment.Center,
                ) { Text(name, style = MaterialTheme.typography.labelLarge, color = if (on) c.onChip else c.tx, maxLines = 1) }
            }
        }
    }
}

/** The settings that change how the whole app looks and feels. */
@Composable
fun AppearanceExtras() {
    val c = T.c
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SectionTitle("Text and shapes")
        ChoiceRow("Text size", listOf("Small", "Default", "Large", "Largest"), Store.textSize) { Store.textSize = it }
        ChoiceRow("Corner roundness", listOf("Sharp", "Default", "Round"), Store.corners) { Store.corners = it }

        SectionTitle("Library")
        ChoiceRow("Columns", listOf("1", "2", "3"), Store.gridCols - 1) { Store.gridCols = it + 1 }
        ChoiceRow("Card corners", listOf("Varied", "Even"), if (Store.uniformCards) 1 else 0) { Store.uniformCards = it == 1 }

        SectionTitle("Motion and feel")
        ChoiceRow("Animations", listOf("Expressive", "Reduced"), if (Store.reduceMotion) 1 else 0) { Store.reduceMotion = it == 1 }
        ChoiceRow("Tab names", listOf("Selected only", "Always"), if (Store.tabLabels) 1 else 0) { Store.tabLabels = it == 1 }
        SettingRow(
            "Haptic feedback", if (Store.haptics) "A small tap when you press and hold, and between tabs" else "Off",
            Icons.Rounded.Vibration, c.x[3], onClick = { Store.haptics = !Store.haptics; Store.savePrefs() },
            trailing = { ExpressiveSwitch(Store.haptics) { Store.haptics = it; Store.savePrefs() } },
        )
    }
}
