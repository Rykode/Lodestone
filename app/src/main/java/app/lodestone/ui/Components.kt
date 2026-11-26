package app.lodestone.ui

import android.graphics.BitmapFactory
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.ui.text.style.TextOverflow
import app.lodestone.data.Store
import app.lodestone.ui.theme.T
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

fun Modifier.tap(onClick: () -> Unit, label: String? = null, role: Role = Role.Button): Modifier =
    this.clickable(role = role, onClick = onClick).let { if (label != null) it.semantics { contentDescription = label } else it }

@Composable
fun IconCircle(
    icon: ImageVector, desc: String, onClick: () -> Unit, modifier: Modifier = Modifier,
    container: Color = T.c.hi, tint: Color = T.c.tx, size: Dp = 48.dp, shape: Shape = CircleShape,
) {
    Box(
        modifier.size(size).clip(shape).background(container).clickable(role = Role.Button, onClick = onClick).semantics { contentDescription = desc },
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp)) }
}

@Composable
fun BackButton(onClick: () -> Unit) = IconCircle(Icons.AutoMirrored.Rounded.ArrowBack, "Back", onClick)

@Composable
fun ScreenHeader(title: String, onBack: (() -> Unit)?, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 8.dp, bottom = 8.dp).heightIn(min = 56.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (onBack != null) BackButton(onBack)
        Text(title, style = MaterialTheme.typography.headlineMedium, color = T.c.tx, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
        trailing()
    }
}

@Composable
fun PillButton(
    text: String, onClick: () -> Unit, modifier: Modifier = Modifier, container: Color = T.c.fab, content: Color = T.c.onFab,
    icon: ImageVector? = null, height: Dp = 64.dp, shape: Shape = RoundedCornerShape(50), enabled: Boolean = true,
) {
    Row(
        modifier.height(height).clip(shape).background(if (enabled) container else container.copy(alpha = .4f))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick).padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) Icon(icon, null, tint = content, modifier = Modifier.size(22.dp))
        Text(text, style = MaterialTheme.typography.titleMedium, color = content, maxLines = 1)
    }
}

/** Chip whose corners round off to a pill when selected, as in Material 3 Expressive. */
@Composable
fun FilterPill(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val radius by animateDpAsState(if (selected) 14.dp else 22.dp, spring(dampingRatio = .6f, stiffness = 500f), label = "pill")
    val shape = RoundedCornerShape(radius)
    Box(
        modifier.height(44.dp).clip(shape).background(if (selected) T.c.chip else Color.Transparent)
            .then(if (selected) Modifier else Modifier.border(1.5.dp, T.c.ol1, shape))
            .clickable(role = Role.Tab, onClick = onClick).padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = if (selected) T.c.onChip else T.c.tx, maxLines = 1)
    }
}

/** Material 3 Expressive switch: the thumb carries a check or a cross. */
@Composable
fun ExpressiveSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    val pad by animateDpAsState(if (checked) 24.dp else 4.dp, spring(stiffness = 600f), label = "thumb")
    val thumb by animateDpAsState(if (checked) 24.dp else 20.dp, spring(stiffness = 600f), label = "thumbSize")
    Box(
        Modifier.size(52.dp, 32.dp).clip(CircleShape).background(if (checked) T.c.fab else T.c.hi)
            .then(if (checked) Modifier else Modifier.border(2.dp, T.c.ol3, CircleShape))
            .clickable(role = Role.Switch) { onChange(!checked) },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier.padding(start = pad - (if (checked) 0.dp else 0.dp)).size(thumb).clip(CircleShape).background(if (checked) T.c.knob else T.c.sec),
            contentAlignment = Alignment.Center,
        ) {
            Icon(if (checked) Icons.Rounded.Check else Icons.Rounded.Close, null, tint = if (checked) T.c.fab else T.c.hi, modifier = Modifier.size(if (checked) 14.dp else 11.dp))
        }
    }
}

@Composable
fun SettingRow(
    title: String, subtitle: String? = null, icon: ImageVector, iconBg: Color, onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 72.dp).clip(RoundedCornerShape(24.dp)).background(T.c.card)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier).padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(16.dp)).background(T.c.hi), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = T.c.tx, modifier = Modifier.size(22.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = T.c.tx)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = T.c.sec)
        }
        if (trailing != null) trailing() else if (onClick != null) Icon(Icons.Rounded.ChevronRight, null, tint = T.c.sec)
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = T.c.tx, modifier = modifier.padding(horizontal = 4.dp))
}

@Composable
fun Modifier.screenPadding(): Modifier = this.windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))

// ---------- image loading from local files ----------

private val cache = object : android.util.LruCache<String, ImageBitmap>(48 * 1024 * 1024) {
    override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4
}

@Composable
fun rememberLocalImage(dir: File, name: String, maxDim: Int = 900): ImageBitmap? {
    if (name.isEmpty()) return null
    val key = "${dir.name}/$name@$maxDim"
    var bmp by remember(key) { mutableStateOf(cache.get(key)) }
    LaunchedEffect(key) {
        if (bmp == null) bmp = withContext(Dispatchers.IO) {
            val f = File(dir, name)
            if (!f.exists()) return@withContext null
            val b = app.lodestone.data.Ingest.decodeSampled(f, maxDim) ?: return@withContext null
            b.asImageBitmap().also { cache.put(key, it) }
        }
    }
    return bmp
}

@Composable
fun LocalPicture(dir: File, name: String, modifier: Modifier = Modifier, scale: ContentScale = ContentScale.Crop, maxDim: Int = 900) {
    val img = rememberLocalImage(dir, name, maxDim)
    if (img != null) Image(img, null, modifier, contentScale = scale)
}

/** Returns a function that asks for permission to show progress notifications, once, on Android 13 and newer. */
@Composable
fun rememberNotificationAsk(): () -> Unit {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { }
    return {
        if (app.lodestone.data.Notifier.needsPermissionAsk(ctx)) {
            app.lodestone.data.Notifier.markAsked()
            launcher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

fun Int.toColor(): Color = Color(this)

val thumbDir: File get() = Store.thumbDir
val mediaDir: File get() = Store.mediaDir
