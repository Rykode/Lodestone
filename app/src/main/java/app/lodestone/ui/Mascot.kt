package app.lodestone.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.lodestone.R
import app.lodestone.data.Store
import app.lodestone.ui.theme.T

/** The cat's faces. The head is the one in the app icon. */
enum class Mood { Wink, Open, Sleep }

@Composable
fun Mascot(mood: Mood, size: Dp, modifier: Modifier = Modifier) {
    val res = when (mood) { Mood.Wink -> R.drawable.mascot; Mood.Open -> R.drawable.mascot_open; Mood.Sleep -> R.drawable.mascot_sleep }
    Image(painterResource(res), null, modifier.size(size))
}

/**
 * The picture for empty screens: Material 3 Expressive shapes in the theme's colours around the cat.
 * The cat bobs gently unless Animations is set to Reduced.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ExpressiveHero(mood: Mood, modifier: Modifier = Modifier) {
    val c = T.c
    val t = rememberInfiniteTransition(label = "hero")
    val bob by t.animateFloat(-5f, 5f, infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Reverse), label = "bob")
    val turn by t.animateFloat(0f, 360f, infiniteRepeatable(tween(24000, easing = LinearEasing)), label = "turn")
    val still = Store.reduceMotion
    Box(modifier.size(260.dp, 220.dp)) {
        Box(Modifier.offset(0.dp, 34.dp).size(132.dp).graphicsLayer { rotationZ = if (still) 0f else turn }.clip(MaterialShapes.Cookie9Sided.toShape()).background(c.x[0]))
        Box(Modifier.offset(156.dp, 0.dp).size(96.dp).graphicsLayer { rotationZ = if (still) 0f else -turn }.clip(MaterialShapes.Flower.toShape()).background(c.x[1]))
        Box(Modifier.offset(150.dp, 112.dp).size(92.dp).clip(MaterialShapes.Clover4Leaf.toShape()).background(c.x[4]))
        Box(Modifier.offset(20.dp, 150.dp).size(48.dp).clip(MaterialShapes.Pill.toShape()).background(c.x[3]))
        Box(Modifier.offset(58.dp, 44.dp).graphicsLayer { translationY = if (still) 0f else bob }) { Mascot(mood, 150.dp) }
    }
}
