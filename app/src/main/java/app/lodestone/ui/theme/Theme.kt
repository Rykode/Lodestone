package app.lodestone.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import app.lodestone.data.Store

val LocalTokens = staticCompositionLocalOf<Tokens> { Schemes[0].dark }

/** Shortcut: `T.fab`, `T.card` and so on inside any composable. */
object T {
    val c: Tokens @Composable get() = LocalTokens.current
}

private fun Tokens.toMaterial(): ColorScheme {
    val base = if (dark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = p, onPrimary = onP, primaryContainer = pc, onPrimaryContainer = onPc,
        secondary = sc, onSecondary = onSc, secondaryContainer = sc, onSecondaryContainer = onSc,
        tertiary = tc, onTertiary = onTc, tertiaryContainer = tc, onTertiaryContainer = onTc,
        background = bg, onBackground = tx, surface = bg, onSurface = tx,
        surfaceVariant = hi, onSurfaceVariant = sec, outline = ol3, outlineVariant = ol1,
        surfaceContainer = card, surfaceContainerHigh = hi, surfaceContainerHighest = hi,
        surfaceContainerLow = card, surfaceContainerLowest = card,
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LodestoneTheme(content: @Composable () -> Unit) {
    val system = isSystemInDarkTheme()
    val dark = when (Store.mode) { 1 -> false; 2 -> true; else -> system }
    val ctx = LocalContext.current
    val idx = Store.schemeIdx.coerceIn(0, AvailableSchemes.lastIndex)
    val scheme = if (idx == WALLPAPER_SCHEME && android.os.Build.VERSION.SDK_INT >= 31) wallpaperScheme(ctx) else Schemes[idx]
    val base = if (dark) scheme.dark else scheme.light
    // OLED: real black behind everything, with surfaces just above it so cards still read.
    val tokens = if (dark && Store.oled) base.copy(bg = Color.Black, card = Color(0xFF0B0B0D), hi = Color(0xFF17171A), bar = Color(0xFF111114)) else base
    val round = when (Store.corners) { 0 -> 0.35f; 2 -> 1.4f; else -> 1f }
    val density = LocalDensity.current
    val textScale = when (Store.textSize) { 0 -> 0.88f; 2 -> 1.15f; 3 -> 1.3f; else -> 1f }
    // Status and navigation bar icons follow the app's own theme, not just the system's.
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !dark
            controller.isAppearanceLightNavigationBars = !dark
        }
    }
    CompositionLocalProvider(LocalTokens provides tokens, LocalDensity provides Density(density.density, density.fontScale * textScale)) {
        MaterialExpressiveTheme(
            colorScheme = tokens.toMaterial(),
            typography = LodestoneTypography,
            shapes = Shapes(
                extraSmall = RoundedCornerShape((8 * round).dp), small = RoundedCornerShape((14 * round).dp), medium = RoundedCornerShape((20 * round).dp),
                large = RoundedCornerShape((28 * round).dp), extraLarge = RoundedCornerShape((36 * round).dp),
            ),
            content = content,
        )
    }
}
