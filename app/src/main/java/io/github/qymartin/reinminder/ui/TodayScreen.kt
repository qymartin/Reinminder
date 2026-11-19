package io.github.qymartin.reinminder.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.qymartin.reinminder.R
import io.github.qymartin.reinminder.alarm.Repo
import io.github.qymartin.reinminder.data.Event
import io.github.qymartin.reinminder.data.Repeat
import io.github.qymartin.reinminder.logic.Fmt
import io.github.qymartin.reinminder.logic.eventOrder
import io.github.qymartin.reinminder.logic.isOverdue
import io.github.qymartin.reinminder.logic.occurrences
import io.github.qymartin.reinminder.logic.shownDate
import io.github.qymartin.reinminder.logic.shownTime
import java.time.Duration
import java.time.LocalDate

private data class TodayRow(val event: Event, val done: Boolean)

@Composable
fun TodayScreen(events: List<Event>, onOpen: (Long) -> Unit, selectedId: Long, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val en = LocalEn.current
    val now = LocalNow.current
    val today = now.toLocalDate()
    val topics = LocalTopics.current

    val overdue = events.filter { it.isOverdue(now) }.sortedWith(eventOrder)
    val pending = events.filter { !it.done && !it.isOverdue(now) && it.shownDate() == today }
        .map { TodayRow(it, false) }
    val finished = events.filter { it.lastDoneOn == today && !(pending.any { p -> p.event.id == it.id }) }
        .map { TodayRow(it, true) }
    val todayRows = (pending + finished).sortedWith(
        compareBy<TodayRow>({ it.done }, { rowTime(it) != null }, { rowTime(it) }, { it.event.id }),
    )
    val left = pending.size + overdue.size

    val tomorrow = today.plusDays(1)
    val upcoming = events
        .flatMap { e -> e.occurrences(tomorrow, today.plusDays(7)).map { d -> e to d } }
        .sortedWith(compareBy<Pair<Event, LocalDate>>({ it.second.toEpochDay() }, { it.first.time }, { it.first.id }))
        .take(6)

    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            val sub = if (en) "${Fmt.date(today, true)} · $left left" else "${Fmt.date(today, false)} · 还剩 $left 项"
            ScreenTitle(stringResource(R.string.tab_today), sub)
        }

        if (overdue.isNotEmpty()) item {
            SectionCard(background = AmberBg, border = AmberBorder, padding = 12, spacing = 8) {
                Row(Modifier.fillMaxWidth().padding(start = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.overdue_n, overdue.size),
                        fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Amber, modifier = Modifier.weight(1f),
                    )
                    Text(stringResource(R.string.swipe_hint), fontSize = 12.sp, color = AmberText)
                }
                overdue.forEach { e ->
                    SwipeRow(onDone = { Repo.complete(ctx, e.id) }, onPostpone = { Repo.postponeToTomorrow(ctx, e.id) }) {
                        Row(
                            Modifier.fillMaxWidth().background(AmberBg).clickable { onOpen(e.id) }.padding(start = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f).padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(e.title, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = Text1)
                                Text(Fmt.meta(e, en, Fmt.moment(e, today, en), topics.nameOf(e.topic)), fontSize = 13.sp, color = AmberText)
                            }
                            Spacer(Modifier.width(10.dp))
                            OutlinedButton(
                                onClick = { Repo.postponeToTomorrow(ctx, e.id) },
                                border = BorderStroke(1.dp, Color(0xFF8A6A45)),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Amber),
                            ) { Text(stringResource(R.string.postpone_tomorrow), fontSize = 14.sp) }
                        }
                    }
                }
            }
        }

        item {
            SectionCard(padding = 4, spacing = 0) {
                if (todayRows.isEmpty()) {
                    Text(
                        stringResource(R.string.nothing_today), fontSize = 14.sp, color = Text3,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp, horizontal = 14.dp),
                    )
                }
                todayRows.forEachIndexed { i, row ->
                    if (i > 0) RowDivider()
                    val e = row.event
                    val time = rowTime(row)
                    val soon = !row.done && time != null &&
                        Duration.between(now, today.atTime(time)).toMinutes() in 0..60
                    val line: @Composable () -> Unit = {
                        EventLine(
                            title = e.title,
                            meta = if (row.done) Fmt.meta(e.copy(reminders = emptyList()), en, topic = topics.nameOf(e.topic)) else Fmt.meta(e, en, topic = topics.nameOf(e.topic)),
                            time = Fmt.timeOrAllDay(time, en),
                            done = row.done,
                            ringColor = if (soon) Amber else Muted,
                            timeColor = if (soon) Amber else Color(0xFFC9CDD6),
                            checkLabel = stringResource(if (row.done) R.string.undo_done else R.string.mark_done) + " " + e.title,
                            onCheck = when {
                                !row.done -> ({ Repo.complete(ctx, e.id) })
                                e.repeat == Repeat.NONE -> ({ Repo.uncomplete(ctx, e.id) })
                                else -> ({})
                            },
                            onClick = { onOpen(e.id) },
                            selected = e.id == selectedId,
                        )
                    }
                    Box(Modifier.background(CardBg)) {
                        if (row.done) line() else SwipeRow(
                            onDone = { Repo.complete(ctx, e.id) },
                            onPostpone = { Repo.postponeToTomorrow(ctx, e.id) },
                        ) { Box(Modifier.background(CardBg)) { line() } }
                    }
                }
            }
        }

        if (upcoming.isNotEmpty()) {
            item {
                Text(
                    stringResource(R.string.coming_up), fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Text1,
                    modifier = Modifier.padding(start = 2.dp, top = 4.dp),
                )
            }
            item {
                SectionCard(padding = 4, spacing = 0) {
                    upcoming.forEachIndexed { i, (e, d) ->
                        if (i > 0) RowDivider()
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onOpen(e.id) }
                                .heightIn(min = 60.dp).padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.width(48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(Fmt.weekday(d, en), fontSize = 12.sp, color = Text3)
                                Text("${d.dayOfMonth}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Text1)
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f).padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(e.title, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Text1)
                                val t = if (d == e.shownDate()) e.shownTime() else e.time
                                Text(Fmt.meta(e, en, t?.let { Fmt.time(it, en) }, topics.nameOf(e.topic)), fontSize = 13.sp, color = Text3)
                            }
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

private fun rowTime(r: TodayRow) = if (r.done) r.event.lastDoneTime else r.event.shownTime()

/** Swipe right = done, swipe left = postpone to tomorrow. The row springs back; the list then updates. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeRow(onDone: () -> Unit, onPostpone: () -> Unit, content: @Composable () -> Unit) {
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { v ->
            when (v) {
                SwipeToDismissBoxValue.StartToEnd -> onDone()
                SwipeToDismissBoxValue.EndToStart -> onPostpone()
                SwipeToDismissBoxValue.Settled -> Unit
            }
            false
        },
    )
    SwipeToDismissBox(
        state = state,
        backgroundContent = {
            val toRight = state.dismissDirection == SwipeToDismissBoxValue.StartToEnd
            Box(
                Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp))
                    .background(if (toRight) SelectedBg else AmberBorder)
                    .padding(horizontal = 18.dp),
                contentAlignment = if (toRight) Alignment.CenterStart else Alignment.CenterEnd,
            ) {
                Text(
                    stringResource(if (toRight) R.string.mark_done else R.string.postpone_tomorrow),
                    color = if (toRight) OnTonal else Text1, fontSize = 14.sp, fontWeight = FontWeight.Medium,
                )
            }
        },
    ) {
        content()
    }
}
