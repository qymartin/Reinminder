package io.github.qymartin.reinminder.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.qymartin.reinminder.R
import io.github.qymartin.reinminder.alarm.Repo
import io.github.qymartin.reinminder.data.Event
import io.github.qymartin.reinminder.data.EventStore
import kotlin.math.roundToInt

/**
 * Long-press a task and drag it onto another task: the dragged one becomes the other's subtask.
 * Rows register where they are; a floating copy follows the finger; the row under it is outlined.
 */
class NestDragState {
    var dragging by mutableStateOf<Event?>(null)
    var pointer by mutableStateOf(Offset.Zero)
    var targetKey by mutableStateOf<String?>(null)
    var screenTopLeft = Offset.Zero
    /** Row key -> (task id, bounds on screen). A repeating task can have several rows. */
    val rows = HashMap<String, Pair<Long, Rect>>()

    fun targetId(): Long? = targetKey?.let { rows[it]?.first }

    fun findTarget() {
        val drag = dragging ?: return
        targetKey = rows.entries.firstOrNull { (_, v) -> v.first != drag.id && v.second.contains(pointer) }?.key
    }
}

@Composable
fun rememberNestDrag(): NestDragState = remember { NestDragState() }

/** Makes a row draggable onto other rows. [key] must be unique per row on the screen. */
@Composable
fun Modifier.nestDrag(state: NestDragState, key: String, e: Event, enabled: Boolean = true): Modifier {
    if (!enabled) return this
    val ctx = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val msgNoNest = androidx.compose.ui.res.stringResource(R.string.cannot_nest_subtask)
    val msgHasChildren = androidx.compose.ui.res.stringResource(R.string.cannot_nest_parent)
    val msgDone = androidx.compose.ui.res.stringResource(R.string.nested_into)
    androidx.compose.runtime.DisposableEffect(key) { onDispose { state.rows.remove(key) } }
    val isTarget = state.targetKey == key
    val isDragged = state.dragging?.id == e.id
    return this
        .onGloballyPositioned { state.rows[key] = e.id to it.boundsInRoot() }
        .then(if (isTarget) Modifier.border(2.dp, Accent, RoundedCornerShape(12.dp)) else Modifier)
        .then(if (isDragged) Modifier.alpha(0.4f) else Modifier)
        .pointerInput(key, e.id) {
            detectDragGesturesAfterLongPress(
                onDragStart = { offset ->
                    val topLeft = state.rows[key]?.second?.topLeft ?: Offset.Zero
                    state.dragging = e
                    state.pointer = topLeft + offset
                    state.targetKey = null
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                },
                onDrag = { change, amount ->
                    change.consume()
                    state.pointer += amount
                    state.findTarget()
                },
                onDragEnd = {
                    val child = state.dragging
                    val parentId = state.targetId()
                    state.dragging = null
                    state.targetKey = null
                    if (child != null && parentId != null) {
                        val store = EventStore.get(ctx)
                        val parent = store.get(parentId)
                        when {
                            parent == null || parent.id == child.id || child.parentId == parent.id -> Unit
                            parent.parentId != null -> Toast.makeText(ctx, msgNoNest, Toast.LENGTH_SHORT).show()
                            store.all().any { it.parentId == child.id } -> Toast.makeText(ctx, msgHasChildren, Toast.LENGTH_SHORT).show()
                            else -> {
                                Repo.makeSubtask(ctx, child.id, parent.id)
                                Toast.makeText(ctx, String.format(msgDone, parent.title), Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                },
                onDragCancel = {
                    state.dragging = null
                    state.targetKey = null
                },
            )
        }
}

/** Put on the screen's outer Box: remembers where the screen is, so the floating copy lines up. */
fun Modifier.nestDragArea(state: NestDragState): Modifier =
    onGloballyPositioned { state.screenTopLeft = it.boundsInRoot().topLeft }

/** The floating copy of the dragged task. Call inside the screen's outer Box, after the content. */
@Composable
fun BoxScope.NestDragGhost(state: NestDragState) {
    val e = state.dragging ?: return
    val density = LocalDensity.current
    val p = state.pointer - state.screenTopLeft
    val dx = with(density) { 24.dp.toPx() }
    val dy = with(density) { 56.dp.toPx() }
    Box(
        Modifier
            .offset { IntOffset((p.x - dx).roundToInt(), (p.y - dy).roundToInt()) }
            .shadow(8.dp, RoundedCornerShape(12.dp))
            .background(SelectedBg, RoundedCornerShape(12.dp))
            .border(1.dp, Accent, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .widthIn(max = 260.dp),
    ) {
        Text(e.title, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Text1, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
