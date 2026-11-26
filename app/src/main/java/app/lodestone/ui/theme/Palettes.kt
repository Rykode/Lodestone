package app.lodestone.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

/** Every colour the app uses. Ported from the design canvas, where contrast was checked for each pair. */
data class Tokens(
    val bg: Color, val card: Color, val hi: Color, val bar: Color, val deep: Color,
    val ol1: Color, val ol2: Color, val ol3: Color, val ol4: Color,
    val tx: Color, val sec: Color, val knob: Color,
    val chip: Color, val onChip: Color, val fab: Color, val onFab: Color,
    val p: Color, val onP: Color, val pc: Color, val onPc: Color,
    val sc: Color, val onSc: Color, val tc: Color, val onTc: Color,
    val x: List<Color>,
    val good: Color,
    val dark: Boolean,
)

data class Scheme(val name: String, val dark: Tokens, val light: Tokens)

/** Text on the pastel item colours. */
val Ink = Color(0xFF17181C)
/** Icons on the dark floating toolbar. */
val BarIcon = Color(0xFFF4F2ED)

private fun hex(h: String): Color = Color(("FF" + h.removePrefix("#")).toLong(16))

private fun mix(a: Color, b: Color, t: Float): Color {
    fun ch(x: Float, y: Float) = (x + (y - x) * t)
    return Color(ch(a.red, b.red), ch(a.green, b.green), ch(a.blue, b.blue))
}

private val W = Color.White
private val K = Color.Black

private class Src(
    val name: String,
    val dp: String, val donP: String, val dpc: String, val donPc: String, val dsc: String, val donSc: String, val dtc: String, val donTc: String, val dbg: String, val dcard: String,
    val lp: String, val lonP: String, val lpc: String, val lonPc: String, val lsc: String, val lonSc: String, val ltc: String, val lonTc: String, val lbg: String, val lcard: String, val lhi: String,
)

private val sources = listOf(
    Src("Indigo", "#B9B4FF", "#1A1070", "#6155F2", "#FFFFFF", "#F0C48A", "#3A2A0C", "#9FDDB6", "#0F3322", "#121214", "#1C1C20",
        "#4B3FE8", "#FFFFFF", "#D9D6FF", "#1A1070", "#FFD9B3", "#3A2A0C", "#C6F1D3", "#0F3322", "#F4F2ED", "#FFFFFF", "#E9E6DE"),
    Src("Teal", "#7DD8CF", "#00352F", "#0F7A71", "#FFFFFF", "#F2B8A0", "#3C1C0E", "#B9C8F5", "#1A2850", "#101514", "#1A2120",
        "#0F7A71", "#FFFFFF", "#BDEFE9", "#00201D", "#FFDBCB", "#3C1C0E", "#DCE2FF", "#1A2850", "#F2F6F5", "#FFFFFF", "#E1E9E7"),
    Src("Rose", "#FFB1C4", "#5B0F2B", "#B72E5B", "#FFFFFF", "#FFC9A3", "#3E1D07", "#C4D98A", "#2A3300", "#151112", "#201A1B",
        "#B72E5B", "#FFFFFF", "#FFD9E1", "#3E0019", "#FFDCC6", "#3E1D07", "#DEEDAA", "#2A3300", "#FBF4F5", "#FFFFFF", "#EEE2E4"),
    Src("Amber", "#F6BE6A", "#432C00", "#8A5A00", "#FFFFFF", "#A7D8B5", "#0C3320", "#BDC3FF", "#1D2260", "#14120E", "#1F1C17",
        "#8A5A00", "#FFFFFF", "#FFDDB0", "#2B1800", "#C8EDD3", "#0C3320", "#E0E0FF", "#1D2260", "#FBF6EE", "#FFFFFF", "#EEE5D6"),
    Src("Moss", "#A5D88A", "#123800", "#3E7A25", "#FFFFFF", "#F0B8C8", "#44111F", "#9CCBF2", "#0B3454", "#111410", "#1B1F19",
        "#3E7A25", "#FFFFFF", "#C4EFA8", "#0A2100", "#FFD9E2", "#44111F", "#CCE5FF", "#0B3454", "#F4F7F0", "#FFFFFF", "#E3E9DC"),
    Src("From wallpaper", "#A8C8FF", "#00315E", "#2F63B0", "#FFFFFF", "#D3BEF2", "#2F1A4D", "#F2B8D2", "#4A1230", "#111318", "#1B1D23",
        "#2F63B0", "#FFFFFF", "#D5E3FF", "#001B3E", "#EBDCFF", "#2F1A4D", "#FFD8E8", "#4A1230", "#F6F7FB", "#FFFFFF", "#E4E6EE"),
)

private fun build(s: Src, index: Int): Scheme {
    val indigo = index == 0
    val dbg = hex(s.dbg); val dcard = hex(s.dcard)
    val dp = hex(s.dp); val dpc = hex(s.dpc); val dsc = hex(s.dsc); val dtc = hex(s.dtc)
    val dark = Tokens(
        bg = dbg, card = dcard,
        hi = if (indigo) hex("#2A2A30") else mix(dcard, W, .06f),
        bar = if (indigo) hex("#2E2E36") else mix(dcard, W, .12f),
        deep = if (indigo) hex("#25252B") else mix(dbg, W, .05f),
        ol1 = if (indigo) hex("#3F3F48") else mix(dcard, W, .14f),
        ol2 = if (indigo) hex("#46464F") else mix(dcard, W, .17f),
        ol3 = if (indigo) hex("#55555D") else mix(dcard, W, .22f),
        ol4 = if (indigo) hex("#6A6A74") else mix(dcard, W, .32f),
        tx = hex("#F1EFEA"), sec = hex("#B4B2AA"), knob = hex("#F1EFEA"),
        chip = dp, onChip = hex(s.donP), fab = dpc, onFab = hex(s.donPc),
        p = dp, onP = hex(s.donP), pc = dpc, onPc = hex(s.donPc),
        sc = dsc, onSc = hex(s.donSc), tc = dtc, onTc = hex(s.donTc),
        x = listOf(
            dsc, dtc,
            if (indigo) hex("#D2E873") else dp,
            if (indigo) hex("#F2A9BB") else mix(dsc, hex("#F2A9BB"), .5f),
            if (indigo) hex("#9CC7F2") else mix(dtc, hex("#9CC7F2"), .5f),
        ),
        good = hex("#7FD6A4"), dark = true,
    )
    val lbg = hex(s.lbg); val lhi = hex(s.lhi)
    val lp = hex(s.lp); val lpc = hex(s.lpc); val lsc = hex(s.lsc); val ltc = hex(s.ltc)
    val light = Tokens(
        bg = lbg, card = hex(s.lcard), hi = lhi,
        bar = if (indigo) hex("#17181C") else dark.bar,
        deep = if (indigo) hex("#17181C") else dark.deep,
        ol1 = if (indigo) hex("#CFCBC0") else mix(lhi, K, .10f),
        ol2 = hex("#46464F"),
        ol3 = if (indigo) hex("#A8A498") else mix(lhi, K, .30f),
        ol4 = if (indigo) hex("#8A877D") else mix(lhi, K, .45f),
        tx = hex("#17181C"), sec = hex("#5A5C66"), knob = hex("#FFFFFF"),
        chip = lpc, onChip = hex(s.lonPc), fab = lp, onFab = hex(s.lonP),
        p = lp, onP = hex(s.lonP), pc = lpc, onPc = hex(s.lonPc),
        sc = lsc, onSc = hex(s.lonSc), tc = ltc, onTc = hex(s.lonTc),
        x = listOf(
            lsc, ltc,
            if (indigo) hex("#E3F58A") else lpc,
            if (indigo) hex("#FFD3DC") else mix(lsc, hex("#FFC2D0"), .5f),
            if (indigo) hex("#CFE6FF") else mix(ltc, hex("#BFDDFB"), .5f),
        ),
        good = hex("#1F6B45"), dark = false,
    )
    return Scheme(s.name, dark, light)
}

val Schemes: List<Scheme> = sources.mapIndexed { i, s -> build(s, i) }

/** Index of the scheme that follows the wallpaper. */
const val WALLPAPER_SCHEME = 5

/** "From wallpaper" needs Android 12 or newer, so older phones are not offered it. */
val AvailableSchemes: List<Scheme> get() = if (android.os.Build.VERSION.SDK_INT >= 31) Schemes else Schemes.dropLast(1)

/** Colours taken from the wallpaper by Android itself (Material You). Only call on Android 12+. */
@androidx.annotation.RequiresApi(31)
fun wallpaperScheme(ctx: android.content.Context): Scheme {
    val d = androidx.compose.material3.dynamicDarkColorScheme(ctx)
    val l = androidx.compose.material3.dynamicLightColorScheme(ctx)
    fun h(c: Color) = c.toHex()
    return build(
        Src(
            "From wallpaper",
            h(d.primary), h(d.onPrimary), h(d.primaryContainer), h(d.onPrimaryContainer),
            h(d.secondary), h(d.onSecondary), h(d.tertiary), h(d.onTertiary), h(d.surface), h(d.surfaceContainer),
            h(l.primary), h(l.onPrimary), h(l.primaryContainer), h(l.onPrimaryContainer),
            h(l.secondaryContainer), h(l.onSecondaryContainer), h(l.tertiaryContainer), h(l.onTertiaryContainer),
            h(l.surface), h(l.surfaceContainerLowest), h(l.surfaceContainerHigh),
        ),
        WALLPAPER_SCHEME,
    )
}

fun Color.toHex(): String = "#%06X".format(toArgb() and 0xFFFFFF)
