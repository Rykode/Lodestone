package app.lodestone.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.lodestone.data.AppIcons
import app.lodestone.ui.*
import app.lodestone.ui.theme.T

/** Pick the icon Lodestone wears on the home screen and in the app list. */
@Composable
fun AppIconScreen() {
    val c = T.c
    val ctx = LocalContext.current
    var chosen by remember { mutableStateOf(AppIcons.current(ctx)) }
    Column(Modifier.fillMaxSize().screenPadding()) {
        ScreenHeader("App icon", onBack = { Nav.back() })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            AppIcons.all.chunked(3).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { choice ->
                        val on = choice == chosen
                        Column(
                            Modifier.weight(1f).clip(RoundedCornerShape(24.dp)).background(if (on) c.hi else c.card)
                                .then(if (on) Modifier.border(3.dp, c.chip, RoundedCornerShape(24.dp)) else Modifier)
                                .clickable(role = Role.RadioButton) { if (!on) { AppIcons.set(ctx, choice); chosen = choice } }
                                .semantics { contentDescription = if (on) "${choice.name}, selected" else choice.name }
                                .padding(vertical = 14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            IconPreview(choice)
                            Text(choice.name, style = MaterialTheme.typography.labelLarge, color = c.tx)
                        }
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            Text(
                "The icon changes in your app list and on your home screen. Your phone may close Lodestone for a moment while it changes, " +
                    "and if the icon on your home screen disappears, find Lodestone in your app list and add it again. " +
                    "The share sheet and the app info page keep the standard icon.",
                style = MaterialTheme.typography.bodySmall, color = c.sec, modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
            )
        }
    }
}

/** A hand-built preview: the real launcher icons are adaptive XML, which Compose cannot draw directly. */
@Composable
private fun IconPreview(choice: AppIcons.Choice) {
    val size = 76.dp
    val brush = Brush.verticalGradient(listOf(Color(choice.bg), Color(choice.bg2)))
    Box(Modifier.size(size).clip(Squircle).background(brush), contentAlignment = Alignment.Center) {
        // The adaptive foreground layer is 108 units wide and only the middle 72 show, so it is drawn 1.5 times larger and clipped.
        Image(painterResource(AppIcons.foreground(choice.look)), null, Modifier.requiredSize(size * 1.5f))
    }
}
