package app.lodestone.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.lodestone.data.Store
import app.lodestone.ui.*
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.toShape
import app.lodestone.ui.theme.Ink
import app.lodestone.ui.theme.T
import kotlinx.coroutines.launch

private val titles = listOf("Keep what moves you.", "Save in one tap.", "Find it by feel.", "Yours alone.", "Make it yours.")
private val bodies = listOf(
    "Links, images, videos and notes in one calm place. No folders needed.",
    "Share from any app to Lodestone. A copy is kept so it still opens offline.",
    "Search by words, tags or color. Everything is matched on this phone.",
    "Everything stays on this phone. No account, no cloud, no tracking.",
    "Pick a look. You can change it any time in Settings.",
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Onboarding() {
    val c = T.c
    val pager = rememberPagerState(pageCount = { 5 })
    val scope = rememberCoroutineScope()
    val last = pager.currentPage == 4
    fun finish() { Store.onboarded = true; Store.savePrefs() }

    Column(Modifier.fillMaxSize().screenPadding().windowInsetsPadding(WindowInsets.navigationBars)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.End) {
            if (!last) Box(
                Modifier.height(48.dp).clip(RoundedCornerShape(24.dp)).background(c.hi).clickable { scope.launch { pager.animateScrollToPage(4) } }.padding(horizontal = 20.dp),
                contentAlignment = Alignment.Center,
            ) { Text("Skip", style = MaterialTheme.typography.labelLarge, color = c.tx) } else Spacer(Modifier.height(48.dp))
        }
        HorizontalPager(pager, Modifier.weight(1f)) { page ->
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                if (page < 4) {
                    Box(Modifier.padding(top = 12.dp).size(300.dp), contentAlignment = Alignment.Center) {
                        when (page) { 0 -> HeroPull(); 1 -> HeroSave(); 2 -> HeroFind(); else -> HeroPrivate() }
                    }
                }
                Spacer(Modifier.height(if (page < 4) 20.dp else 4.dp))
                Text(titles[page], style = MaterialTheme.typography.displayMedium, color = c.tx, textAlign = TextAlign.Center)
                Text(bodies[page], style = MaterialTheme.typography.bodyLarge, color = c.sec, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 14.dp))
                if (page == 4) {
                    Spacer(Modifier.height(20.dp))
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) { ModePicker(); SchemeGrid() }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
            repeat(5) { i ->
                val w by animateDpAsState(if (i == pager.currentPage) 32.dp else 10.dp, spring(stiffness = 500f), label = "dot")
                Box(Modifier.height(10.dp).width(w).clip(CircleShape).background(if (i == pager.currentPage) c.fab else c.ol3))
            }
        }
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (pager.currentPage > 0) IconCircle(Icons.AutoMirrored.Rounded.ArrowBack, "Back", { scope.launch { pager.animateScrollToPage(pager.currentPage - 1) } }, size = 64.dp, shape = RoundedCornerShape(24.dp))
            PillButton(
                if (last) "Start keeping things" else "Next", { if (last) finish() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } },
                Modifier.weight(1f), icon = if (last) Icons.Rounded.Check else Icons.AutoMirrored.Rounded.ArrowForward,
            )
        }
    }
}

@Composable
private fun loop(ms: Int = 4400): Float {
    val t = rememberInfiniteTransition(label = "hero")
    val v by t.animateFloat(0f, 1f, infiniteRepeatable(tween(ms, easing = LinearEasing)), label = "p")
    return v
}

private fun ease(x: Float) = x * x * (3 - 2 * x)

/** Colored shapes are drawn towards the magnet. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun HeroPull() {
    val c = T.c
    val t = loop()
    val d = LocalDensity.current
    val shapes = listOf(
        Triple(-105f to -95f, 70.dp, MaterialShapes.Cookie6Sided.toShape()), Triple(110f to -80f, 62.dp, MaterialShapes.Flower.toShape()),
        Triple(-110f to 80f, 64.dp, MaterialShapes.Clover4Leaf.toShape()), Triple(105f to 95f, 76.dp, MaterialShapes.Sunny.toShape()),
        Triple(0f to -128f, 48.dp, MaterialShapes.Pill.toShape()),
    )
    shapes.forEachIndexed { i, (pos, size, shape) ->
        val tt = ((t - i * 0.07f) % 1f + 1f) % 1f
        val p = ease((tt / 0.55f).coerceIn(0f, 1f))
        val alpha = when { tt < 0.55f -> 1f; tt < 0.6f -> 1f - (tt - 0.55f) / 0.05f; tt < 0.85f -> 0f; else -> (tt - 0.85f) / 0.15f }
        Box(
            Modifier.size(size).graphicsLayer {
                translationX = with(d) { pos.first.dp.toPx() } * (1 - p); translationY = with(d) { pos.second.dp.toPx() } * (1 - p)
                val s = 1f - 0.75f * p; scaleX = s; scaleY = s; this.alpha = alpha
            }.clip(shape).background(c.x[i % 5]),
        )
    }
    val pulse = (t * 2f) % 1f
    Box(Modifier.size(72.dp).graphicsLayer { val s = 1f + pulse * 0.9f; scaleX = s; scaleY = s; alpha = 0.5f * (1f - pulse) }.clip(CircleShape).background(c.fab))
    Box(Modifier.size(100.dp).clip(app.lodestone.ui.Squircle).background(c.fab), contentAlignment = Alignment.Center) {
        androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(app.lodestone.R.drawable.mascot), null, Modifier.size(64.dp))
    }
}

@Composable
private fun HeroSave() {
    val c = T.c
    val t = loop()
    Box(Modifier.size(240.dp, 200.dp).clip(RoundedCornerShape(36.dp)).background(c.card))
    listOf(c.x[1] to 0.1f, c.x[0] to 0.25f).forEachIndexed { i, (color, start) ->
        val p = ease(((t - start) / 0.2f).coerceIn(0f, 1f))
        val out = if (t > 0.85f) 1f - (t - 0.85f) / 0.15f else 1f
        Box(
            Modifier.offset(y = (-48 + i * 84).dp).size(196.dp, 70.dp).graphicsLayer { translationY = -200f * (1 - p); alpha = p * out }.clip(RoundedCornerShape(24.dp)).background(color),
        )
    }
    val pop = if (t in 0.4f..0.85f) ease(((t - 0.4f) / 0.12f).coerceIn(0f, 1f)) else 0f
    Box(Modifier.offset(90.dp, 80.dp).size(64.dp).graphicsLayer { scaleX = pop; scaleY = pop }.clip(RoundedCornerShape(24.dp, 24.dp, 24.dp, 8.dp)).background(c.fab), contentAlignment = Alignment.Center) {
        Icon(Icons.Rounded.Check, null, tint = c.onFab, modifier = Modifier.size(30.dp))
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun HeroFind() {
    val c = T.c
    val t = loop()
    val step = (t * 4).toInt().coerceAtMost(3)
    val spots = listOf(0 to 0, 1 to 0, 1 to 1, 0 to 1)
    val (gx, gy) = spots[step]
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        repeat(3) { r -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            repeat(3) { col ->
                val shape = listOf(MaterialShapes.Cookie9Sided, MaterialShapes.Flower, MaterialShapes.Clover4Leaf, MaterialShapes.Sunny, MaterialShapes.Pill, MaterialShapes.Cookie6Sided)[(r * 3 + col) % 6].toShape()
                Box(Modifier.size(72.dp).clip(shape).background(c.x[(r * 3 + col) % 5]))
            }
        } }
    }
    val ox by animateDpAsState((-84 + gx * 84).dp, spring(stiffness = 300f), label = "ox")
    val oy by animateDpAsState((-84 + gy * 84).dp, spring(stiffness = 300f), label = "oy")
    Box(Modifier.offset(ox, oy).size(84.dp).border(5.dp, c.tx, RoundedCornerShape(30.dp)))
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun HeroPrivate() {
    val c = T.c
    val t = loop()
    val shackle = ease(((t - 0.15f) / 0.25f).coerceIn(0f, 1f)) * (if (t > 0.85f) 1f - (t - 0.85f) / 0.15f else 1f)
    val bob = kotlin.math.sin(t * 2 * Math.PI.toFloat() * 1.5f) * 6f
    Box(Modifier.size(220.dp).clip(MaterialShapes.Cookie12Sided.toShape()).background(c.chip))
    Box(Modifier.offset(y = bob.dp)) { Mascot(Mood.Sleep, 124.dp) }
    Box(Modifier.offset(x = 62.dp, y = 58.dp).size(60.dp).clip(MaterialShapes.Clover4Leaf.toShape()).background(c.onChip), contentAlignment = Alignment.Center) {
        Icon(androidx.compose.material.icons.Icons.Rounded.Lock, null, tint = c.chip, modifier = Modifier.size(26.dp))
    }
    Box(Modifier.offset(x = 70.dp, y = (-110).dp + bob.dp).height(36.dp).clip(RoundedCornerShape(18.dp)).background(c.x[1]).padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
        Text("On device", style = MaterialTheme.typography.labelMedium, color = Ink)
    }
}
