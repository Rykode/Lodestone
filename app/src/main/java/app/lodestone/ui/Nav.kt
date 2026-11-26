package app.lodestone.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

sealed interface Route {
    data object Library : Route
    data object Spaces : Route
    data object Search : Route
    data object Settings : Route
    data object Appearance : Route
    data object AppIcon : Route
    data object Trash : Route
    data object Backup : Route
    data object Resurface : Route
    data object About : Route
    data class Detail(val id: Long) : Route
    data class Space(val id: Long) : Route
    /** Create (id 0) or edit a Smart Space. */
    data class SmartBuilder(val id: Long) : Route
    /** Things that go with one item. */
    data class Vibe(val id: Long) : Route
}

/** A tiny back stack. Root tabs replace each other; everything else stacks on top. */
object Nav {
    val stack = mutableStateListOf<Route>(Route.Library)
    var showCapture by mutableStateOf(false)
    /** Space the capture sheet should save into, 0 for none. */
    var captureSpace by mutableStateOf(0L)
    /** Search text handed over from another screen, such as tapping a tag. */
    var pendingQuery by mutableStateOf<String?>(null)

    // Screen state that survives opening an item and coming back.
    var searchQuery by mutableStateOf("")
    var searchType by mutableStateOf<String?>(null)
    var searchSwatch by mutableStateOf(-1)
    var libFilter by mutableStateOf<String?>(null)
    val libGrid = androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState()

    val current: Route get() = stack.last()
    val isRoot: Boolean get() = stack.size == 1

    /** Direction of the last navigation, used to pick the screen transition: 1 forward, -1 back, 0 tab switch. */
    var direction = 0

    fun push(r: Route) { if (current != r) { direction = 1; stack.add(r) } }

    fun back(): Boolean {
        if (stack.size > 1) { direction = -1; stack.removeAt(stack.lastIndex); return true }
        return false
    }

    /** Goes back without a transition, because the predictive back gesture has just animated the screen away. */
    fun popInstant(): Boolean {
        if (stack.size > 1) { direction = 2; stack.removeAt(stack.lastIndex); return true }
        return false
    }

    /** The bottom toolbar's destinations, left to right. Moving between them slides the way you are going. */
    val tabs: List<Route> = listOf(Route.Library, Route.Spaces, Route.Search)

    /** True while the library is in multi-select, when the toolbar steps out of the way. */
    var selecting by mutableStateOf(false)

    fun tab(r: Route) {
        val from = tabs.indexOf(current); val to = tabs.indexOf(r)
        direction = if (from < 0 || to < 0 || from == to) 0 else if (to > from) 3 else 4
        stack.clear(); stack.add(r)
    }
}
