package app.kinjar.ui

import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import app.kinjar.R
import app.kinjar.ui.theme.LocalColors

/**
 * Small lists only: every row is composed. Rows reorder locally while dragging and [onMove] runs once on drop.
 * The local order resets whenever [items] or [resetKey] change, so a failed save snaps back to the stored order.
 * [actions] carries move up/down accessibility actions and belongs on the row's focusable root.
 * [scroll] is the scrollable parent; the drag consumes pointer events, so it is scrolled while a row is held near a clipped edge.
 */
@Composable
fun <T> ReorderColumn(
    items: List<T>,
    idOf: (T) -> String,
    resetKey: Any?,
    onMove: (List<String>) -> Unit,
    scroll: ScrollableState,
    modifier: Modifier = Modifier,
    spacing: Dp = 0.dp,
    row: @Composable (item: T, index: Int, handle: Modifier, actions: Modifier) -> Unit,
) {
    val order = remember(items, resetKey) { mutableStateOf(items) }
    var dragged by remember { mutableStateOf<String?>(null) }
    var offset by remember { mutableFloatStateOf(0f) }
    val heights = remember { mutableMapOf<String, Int>() }
    var column by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val density = LocalDensity.current
    val spacingPx = with(density) { spacing.toPx() }
    val edgePx = with(density) { 64.dp.toPx() }
    val maxScrollPx = with(density) { 16.dp.toPx() }
    val moveUp = stringResource(R.string.reorder_move_up)
    val moveDown = stringResource(R.string.reorder_move_down)

    fun commit(list: List<T>) {
        order.value = list
        val ids = list.map(idOf)
        if (ids != items.map(idOf)) onMove(ids)
    }

    fun settle(id: String) {
        val list = order.value
        val from = list.indexOfFirst { idOf(it) == id }
        if (from < 0) return
        val (to, rest) = settleDrag(list.map { heights[idOf(it)] ?: 0 }, from, offset, spacingPx)
        if (to != from) {
            order.value = list.toMutableList().apply { add(to, removeAt(from)) }
            offset = rest
        }
    }

    LaunchedEffect(dragged, order) {
        val id = dragged ?: return@LaunchedEffect
        while (true) {
            withFrameNanos { }
            val coords = column?.takeIf { it.isAttached } ?: continue
            val visible = coords.boundsInWindow()
            if (visible.isEmpty) continue
            val list = order.value
            val i = list.indexOfFirst { idOf(it) == id }
            if (i < 0) continue
            val top = (0 until i).sumOf { heights[idOf(list[it])] ?: 0 } + i * spacingPx + offset
            val delta = edgeScroll(
                top = top,
                bottom = top + (heights[id] ?: 0),
                visibleTop = coords.windowToLocal(visible.topLeft).y,
                visibleBottom = coords.windowToLocal(visible.bottomLeft).y,
                height = coords.size.height.toFloat(),
                edge = edgePx,
                max = maxScrollPx,
            )
            if (delta != 0f) {
                // Scrolling moves the row's layout slot under a still finger; the translation absorbs it.
                offset += scroll.scrollBy(delta)
                settle(id)
            }
        }
    }

    Column(modifier.onGloballyPositioned { column = it }, verticalArrangement = Arrangement.spacedBy(spacing)) {
        order.value.forEachIndexed { index, item ->
            val id = idOf(item)
            key(id) {
                val isDragged = dragged == id
                val handle = Modifier.pointerInput(id, order) {
                    try {
                        detectDragGestures(
                            onDragStart = { dragged = id; offset = 0f },
                            onDragEnd = { dragged = null; offset = 0f; commit(order.value) },
                            onDragCancel = { dragged = null; offset = 0f; order.value = items },
                            onDrag = { change, amount ->
                                change.consume()
                                offset += amount.y
                                settle(id)
                            },
                        )
                    } finally {
                        // A reset of the order restarts this block mid-drag without onDragCancel; without this the row stays lifted and scrolling.
                        if (dragged == id) {
                            dragged = null
                            offset = 0f
                        }
                    }
                }
                val actions = Modifier.semantics {
                    customActions = listOfNotNull(
                        if (index > 0) CustomAccessibilityAction(moveUp) { commit(order.value.swapped(index, index - 1)); true } else null,
                        if (index < order.value.lastIndex) CustomAccessibilityAction(moveDown) { commit(order.value.swapped(index, index + 1)); true } else null,
                    )
                }
                Box(
                    Modifier.zIndex(if (isDragged) 1f else 0f)
                        .graphicsLayer { translationY = if (isDragged) offset else 0f }
                        .onSizeChanged { heights[id] = it.height },
                ) { row(item, index, handle, actions) }
            }
        }
    }
}

@Composable
fun DragHandle(modifier: Modifier) {
    Box(modifier.size(48.dp), contentAlignment = Alignment.Center) {
        Icon(Icons.Default.DragIndicator, contentDescription = null, tint = LocalColors.current.muted, modifier = Modifier.size(20.dp))
    }
}

/**
 * Moves the dragged row at [from] past every neighbour its [offset] has crossed and returns the new index and the offset left
 * relative to the new slot. Passing a neighbour needs 60% of that neighbour's step, so the return trip needs the same and a
 * row resting near a boundary does not flip back and forth.
 */
internal fun settleDrag(heights: List<Int>, from: Int, offset: Float, spacing: Float): Pair<Int, Float> {
    var index = from
    var rest = offset
    while (true) {
        val next = if (rest > 0) index + 1 else index - 1
        if (rest == 0f || next !in heights.indices) break
        val step = heights[next] + spacing
        if (kotlin.math.abs(rest) <= step * 0.6f) break
        index = next
        rest -= if (rest > 0) step else -step
    }
    return index to rest
}

/** Scroll delta for a dragged row near an edge of the list's visible part, only toward rows that are clipped away. */
internal fun edgeScroll(top: Float, bottom: Float, visibleTop: Float, visibleBottom: Float, height: Float, edge: Float, max: Float): Float = when {
    visibleTop > 0.5f && top < visibleTop + edge -> -max * ((visibleTop + edge - top) / edge).coerceAtMost(1f)
    visibleBottom < height - 0.5f && bottom > visibleBottom - edge -> max * ((bottom - visibleBottom + edge) / edge).coerceAtMost(1f)
    else -> 0f
}

private fun <T> List<T>.swapped(a: Int, b: Int): List<T> = toMutableList().also { it[a] = this[b]; it[b] = this[a] }
