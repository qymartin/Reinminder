package io.github.qymartin.reinminder.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.LocalDate

@Composable
fun TodayScreen(events: List<Event>, onOpen: (Long) -> Unit, selectedId: Long, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val en = LocalEn.current
    val now = LocalNow.current
    val today = now.toLocalDate()
    val topics = LocalTopics.current
    val titles = LocalTitles.current

    // Tick menu, and tasks just archived: they show ticked for a moment, then move to 已完成.
    var menuFor by remember { mutableStateOf<Event?>(null) }
    var archiving by remember { mutableStateOf(setOf<Long>()) }
    var showDone by rememberSaveable { mutableStateOf(false) }

    val overdue = events.filter { it.isOverdue(now) }.sortedWith(eventOrder)
    val pending = events.filter { !it.done && !it.isOverdue(now) && it.shownDate() == today }.sortedWith(eventOrder)
    // "完成并删除" today: struck through until midnight.
    val struck = events.filter { it.done && it.purge && it.lastDoneOn == today }.sortedWith(eventOrder)
    // Everything "完成并存档", latest first, in the folded 已完成 list.
    val archived = events.filter { it.done && !it.purge }
        .sortedWith(compareByDescending<Event> { it.lastDoneOn?.toEpochDay() ?: 0L }.thenByDescending { it.shownDate().toEpochDay() })
    val left = pending.size + overdue.size

    val tomorrow = today.plusDays(1)
    val upcoming = events
        .flatMap { e -> e.occurrences(tomorrow, today.plusDays(7)).map { d -> e to d } }
        .sortedWith(compareBy<Pair<Event, LocalDate>>({ it.second.toEpochDay() }, { !it.first.important }, { it.first.time }, { it.first.id }))
        .take(6)

    archiving.forEach { id ->
        key(id) {
            LaunchedEffect(id) {
                delay(600)
                Repo.archive(ctx, id)
                archiving = archiving - id
            }
        }
    }

    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            val sub = if (en) "${Fmt.date(today, true)} · $left left" else "${Fmt.date(today, false)} · 还剩 $left 项"
            ScreenTitle(stringResource(R.string.tab_today), sub)
        }

        if (overdue.isNotEmpty()) item {
            SectionCard(background = AmberBg, border = AmberBorder, padding = 4, spacing = 0) {
                Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.overdue_n, overdue.size),
                        fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Amber, modifier = Modifier.weight(1f),
                    )
                }
                overdue.forEachIndexed { i, e ->
                    if (i > 0) RowDivider()
                    EventLine(
                        title = e.title,
                        meta = Fmt.meta(e, en, Fmt.day(e.shownDate(), today, en), topics.nameOf(e.topic)),
                        time = Fmt.timeOrAllDay(e.shownTime(), en),
                        done = e.id in archiving,
                        strike = false,
                        ringColor = Amber,
                        timeColor = Amber,
                        checkLabel = stringResource(R.string.mark_done) + " " + e.title,
                        onCheck = { menuFor = e },
                        onClick = { onOpen(e.id) },
                        selected = e.id == selectedId,
                        important = e.important,
                        parent = titles.parentOf(e),
                    )
                }
            }
        }

        item {
            SectionCard(padding = 4, spacing = 0) {
                if (pending.isEmpty() && struck.isEmpty()) {
                    Text(
                        stringResource(R.string.nothing_today), fontSize = 14.sp, color = Text3,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp, horizontal = 14.dp),
                    )
                }
                pending.forEachIndexed { i, e ->
                    if (i > 0) RowDivider()
                    val time = e.shownTime()
                    val soon = time != null && Duration.between(now, today.atTime(time)).toMinutes() in 0..60
                    EventLine(
                        title = e.title,
                        meta = Fmt.meta(e, en, topic = topics.nameOf(e.topic)),
                        time = Fmt.timeOrAllDay(time, en),
                        done = e.id in archiving,
                        strike = false,
                        ringColor = if (soon) Amber else Muted,
                        timeColor = if (soon) Amber else Color(0xFFC9CDD6),
                        checkLabel = stringResource(R.string.mark_done) + " " + e.title,
                        onCheck = { menuFor = e },
                        onClick = { onOpen(e.id) },
                        selected = e.id == selectedId,
                        important = e.important,
                        parent = titles.parentOf(e),
                    )
                }
                struck.forEachIndexed { i, e ->
                    if (i > 0 || pending.isNotEmpty()) RowDivider()
                    EventLine(
                        title = e.title,
                        meta = stringResource(R.string.deleted_at_midnight),
                        time = Fmt.timeOrAllDay(e.lastDoneTime ?: e.shownTime(), en),
                        done = true,
                        strike = true,
                        ringColor = Muted,
                        timeColor = Muted,
                        checkLabel = stringResource(R.string.undo_done) + " " + e.title,
                        onCheck = { Repo.restore(ctx, e.id) },
                        onClick = { onOpen(e.id) },
                        selected = e.id == selectedId,
                        important = e.important,
                        parent = titles.parentOf(e),
                    )
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
                                TitleText(e.title, e.important, fontSize = 15, parent = titles.parentOf(e))
                                val t = if (d == e.shownDate()) e.shownTime() else e.time
                                Text(Fmt.meta(e, en, t?.let { Fmt.time(it, en) }, topics.nameOf(e.topic)), fontSize = 13.sp, color = Text3)
                            }
                        }
                    }
                }
            }
        }

        // 已完成 (all archived), folded by default.
        if (archived.isNotEmpty()) item {
            SectionCard(padding = 4, spacing = 0) {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { showDone = !showDone }
                        .heightIn(min = 52.dp).padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.completed_n, archived.size),
                        fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Text2, modifier = Modifier.weight(1f),
                    )
                    Icon(
                        if (showDone) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                        contentDescription = stringResource(if (showDone) R.string.collapse else R.string.expand),
                        tint = Text2,
                    )
                }
                if (showDone) archived.forEach { e ->
                    RowDivider()
                    EventLine(
                        title = e.title,
                        meta = Fmt.meta(e.copy(reminders = emptyList()), en, Fmt.day(e.shownDate(), today, en), topics.nameOf(e.topic)),
                        time = e.lastDoneOn?.let { stringResource(R.string.done_on, Fmt.day(it, today, en)) } ?: "",
                        done = true,
                        strike = false,
                        ringColor = Muted,
                        timeColor = Muted,
                        checkLabel = stringResource(R.string.restore) + " " + e.title,
                        onCheck = { Repo.restore(ctx, e.id) },
                        onClick = { onOpen(e.id) },
                        selected = e.id == selectedId,
                        important = e.important,
                        parent = titles.parentOf(e),
                    )
                }
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }

    menuFor?.let { e ->
        CompleteDialog(
            title = e.title,
            repeating = e.repeat != Repeat.NONE,
            onDismiss = { menuFor = null },
            onPostpone = { menuFor = null; Repo.postponeToTomorrow(ctx, e.id) },
            onArchive = { menuFor = null; archiving = archiving + e.id },
            onDelete = { menuFor = null; Repo.completeAndDelete(ctx, e.id) },
        )
    }
}
