package app.lodestone.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.lodestone.R

/** Roboto Flex is a variable font: weight (wght) and width (wdth) are dialled per style, the Expressive way. */
@OptIn(ExperimentalTextApi::class)
private fun flex(weight: Int, width: Float): FontFamily = FontFamily(
    Font(
        R.font.roboto_flex,
        weight = FontWeight(weight),
        variationSettings = FontVariation.Settings(FontVariation.weight(weight), FontVariation.width(width)),
    )
)

object Flex {
    val body = flex(400, 100f)
    val label = flex(600, 100f)
    val title = flex(750, 105f)
    val headline = flex(850, 105f)
    val display = flex(900, 125f)
}

private fun style(f: FontFamily, size: TextUnit, line: TextUnit, track: TextUnit = 0.sp, w: Int = 400) =
    TextStyle(fontFamily = f, fontSize = size, lineHeight = line, letterSpacing = track, fontWeight = FontWeight(w))

val LodestoneTypography = Typography(
    displayLarge = style(Flex.display, 52.sp, 52.sp, (-0.03).em, 900),
    displayMedium = style(Flex.display, 40.sp, 41.sp, (-0.03).em, 900),
    displaySmall = style(Flex.display, 34.sp, 36.sp, (-0.025).em, 900),
    headlineLarge = style(Flex.headline, 32.sp, 34.sp, (-0.025).em, 850),
    headlineMedium = style(Flex.headline, 28.sp, 30.sp, (-0.02).em, 850),
    headlineSmall = style(Flex.headline, 24.sp, 27.sp, (-0.015).em, 850),
    titleLarge = style(Flex.title, 22.sp, 26.sp, (-0.01).em, 750),
    titleMedium = style(Flex.title, 18.sp, 22.sp, 0.sp, 750),
    titleSmall = style(Flex.label, 15.sp, 20.sp, 0.sp, 650),
    bodyLarge = style(Flex.body, 17.sp, 26.sp),
    bodyMedium = style(Flex.body, 15.sp, 22.sp),
    bodySmall = style(Flex.body, 13.sp, 18.sp),
    labelLarge = style(Flex.label, 15.sp, 20.sp, 0.sp, 650),
    labelMedium = style(Flex.label, 13.sp, 18.sp, 0.sp, 650),
    labelSmall = style(Flex.label, 12.sp, 16.sp, 0.02.em, 650),
)
