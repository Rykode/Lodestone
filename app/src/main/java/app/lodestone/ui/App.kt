package app.lodestone.ui

import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlin.coroutines.cancellation.CancellationException
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.lodestone.data.Store
import app.lodestone.ui.screens.*
import app.lodestone.ui.theme.LodestoneTheme
import app.lodestone.ui.theme.T

@Composable
fun LodestoneApp() {
    LodestoneTheme {
        Box(Modifier.fillMaxSize().background(T.c.bg)) {
            if (!Store.onboarded) {
                Onboarding()
            } else {
                // Predictive back: while the finger drags from the edge the screen shrinks and slides, and it is committed on release.
                var backProgress by remember { mutableFloatStateOf(0f) }
                var backFromRight by remember { mutableStateOf(false) }
                PredictiveBackHandler(enabled = !Nav.isRoot) { progress ->
                    try {
                        progress.collect { ev -> backProgress = ev.progress; backFromRight = ev.swipeEdge == BackEventCompat.EDGE_RIGHT }
                        Nav.popInstant()
                        backProgress = 0f
                    } catch (e: CancellationException) {
                        backProgress = 0f
                        throw e
                    }
                }
                val move = spring<IntOffset>(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)
                AnimatedContent(
                    modifier = Modifier.graphicsLayer {
                        val p = backProgress
                        val s = 1f - 0.1f * p
                        scaleX = s; scaleY = s
                        translationX = (if (backFromRight) -1 else 1) * p * size.width * 0.04f
                        alpha = 1f - 0.2f * p
                        shape = RoundedCornerShape((28 * p).dp)
                        clip = p > 0f
                    },
                    targetState = Nav.current,
                    transitionSpec = {
                        // Expressive motion: a springy slide that overshoots a touch and settles. Reduced motion is a plain fade.
                        val spatial = spring<IntOffset>(dampingRatio = 0.78f, stiffness = 420f)
                        fun forward() = (slideInHorizontally(spatial) { it } + fadeIn(tween(200))) togetherWith (slideOutHorizontally(spatial) { -it / 3 } + fadeOut(tween(160)))
                        fun backward() = (slideInHorizontally(spatial) { -it / 3 } + fadeIn(tween(200))) togetherWith (slideOutHorizontally(spatial) { it } + fadeOut(tween(160)))
                        if (Store.reduceMotion && Nav.direction != 2) {
                            fadeIn(tween(140)) togetherWith fadeOut(tween(100))
                        } else when (Nav.direction) {
                            2 -> EnterTransition.None togetherWith ExitTransition.None
                            1, 3 -> forward().apply { targetContentZIndex = 1f }
                            -1, 4 -> backward().apply { targetContentZIndex = -1f }
                            else -> (fadeIn(tween(220)) + scaleIn(spring(stiffness = Spring.StiffnessMediumLow), initialScale = 0.96f)) togetherWith fadeOut(tween(120))
                        }
                    },
                    label = "screens",
                ) { r ->
                when (r) {
                    Route.Library -> LibraryScreen(title = null, spaceId = null)
                    is Route.Space -> LibraryScreen(title = if (r.id == 0L) "Unsorted" else Store.spaces.firstOrNull { it.id == r.id }?.name ?: "Space", spaceId = r.id)
                    Route.Spaces -> SpacesScreen()
                    Route.Search -> SearchScreen()
                    Route.Settings -> SettingsScreen()
                    Route.Appearance -> AppearanceScreen()
                    Route.AppIcon -> AppIconScreen()
                    Route.Trash -> TrashScreen()
                    Route.Backup -> BackupScreen()
                    Route.Resurface -> ResurfaceScreen()
                    Route.About -> AboutScreen()
                    is Route.Detail -> DetailScreen(r.id)
                    is Route.SmartBuilder -> SmartBuilderScreen(r.id)
                    is Route.Vibe -> VibeScreen(r.id)
                }
                }
                androidx.compose.animation.AnimatedVisibility(
                    visible = Nav.isRoot && Nav.current in Nav.tabs && !Nav.selecting,
                    modifier = Modifier.align(androidx.compose.ui.Alignment.BottomCenter),
                    enter = androidx.compose.animation.slideInVertically(spring(dampingRatio = 0.8f, stiffness = 400f)) { it } + fadeIn(),
                    exit = androidx.compose.animation.slideOutVertically(spring(dampingRatio = 0.9f, stiffness = 500f)) { it } + fadeOut(),
                ) { MainToolbar(Nav.current) }
                val toolbarShown = Nav.isRoot && Nav.current in Nav.tabs && !Nav.selecting
                androidx.compose.animation.AnimatedVisibility(
                    visible = Undo.pending != null,
                    modifier = Modifier.align(androidx.compose.ui.Alignment.BottomCenter),
                    enter = androidx.compose.animation.slideInVertically(spring(dampingRatio = 0.8f, stiffness = 500f)) { it } + fadeIn(),
                    exit = androidx.compose.animation.slideOutVertically { it } + fadeOut(),
                ) {
                    UndoBar(Modifier.windowInsetsPadding(WindowInsets.navigationBars).padding(bottom = if (toolbarShown) 104.dp else 16.dp))
                }
                if (Nav.showCapture || Store.pending != null) CaptureSheet()
            }
        }
    }
}
