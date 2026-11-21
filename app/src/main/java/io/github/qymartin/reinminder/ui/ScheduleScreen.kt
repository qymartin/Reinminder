package io.github.qymartin.reinminder.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import io.github.qymartin.reinminder.data.TopicStore
import io.github.qymartin.reinminder.data.Topic
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.collectAsState
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.qymartin.reinminder.R
import io.github.qymartin.reinminder.alarm.Repo
import io.github.qymartin.reinminder.data.Repeat
import io.github.qymartin.reinminder.data.Event
import io.github.qymartin.reinminder.logic.Fmt
import io.github.qymartin.reinminder.logic.isOverdue
import io.github.qymartin.reinminder.logic.occurrences
import io.github.qymartin.reinminder.logic.shownDate
import io.github.qymartin.reinminder.logic.shownTime
import java.time.LocalDate
import java.time.YearMonth

private data class Occ(val event: Event, val date: LocalDate)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(events: List<Event>, onOpen: (Long) -> Unit) {
    val ctx = LocalContext.current
    val en = LocalEn.current
    val now = LocalNow.current
    val today = now.toLocalDate()
    var mode by rememberSaveable { mutableIntStateOf(1) } // 0 = list, 1 = month
    var monthIndex by rememberSaveable { mutableIntStateOf(0) } // months from the current one
    var selectedDay by rememberSaveable { mutableLongStateOf(today.toEpochDay()) }
    var onlyImportant by rememberSaveable { mutableStateOf(false) }
    var topicFilter by rememberSaveable { mutableStateOf<String?>(null) }
    val topics by remember { TopicStore.get(ctx) }.topics.collectAsState()

    val ym = YearMonth.from(today).plusMonths(monthIndex.toLong())
    val monthStart = ym.atDay(1)
    val monthEnd = ym.atEndOfMonth()

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (mode == 1) {
            val occ = occurrencesIn(events, monthStart, monthEnd)
            val byDay = occ.groupBy { it.date }
            val repeating = occ.map { it.event }.distinctBy { it.id }.count { it.repeat != Repeat.NONE }
            item {
                val sub = if (en) "${occ.size} this month · $repeating repeating" else "本月 ${occ.size} 项 · $repeating 项重复"
                ScreenTitle(stringResource(R.string.tab_schedule), sub)
            }
            item { Toolbar(mode, { mode = it }) {} }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    MonthNav(ym, en, { monthIndex-- }, { monthIndex++ }, { monthIndex = 0; selectedDay = today.toEpochDay() })
                }
            }
            item {
                SectionCard(padding = 8, spacing = 2) {
                    Row(Modifier.fillMaxWidth()) {
                        Fmt.weekHeaders(en).forEach {
                            Text(it, fontSize = 12.sp, color = Text3, textAlign = TextAlign.Center, modifier = Modifier.weight(1f).padding(vertical = 4.dp))
                        }
                    }
                    val lead = monthStart.dayOfWeek.value - 1
                    val first = monthStart.minusDays(lead.toLong())
                    for (week in 0 until 6) {
                        Row(Modifier.fillMaxWidth()) {
                            for (dow in 0 until 7) {
                                val d = first.plusDays((week * 7 + dow).toLong())
                                val inMonth = d.month == ym.month
                                val items = if (inMonth) byDay[d].orEmpty() else emptyList()
                                DayCell(
                                    d, inMonth, isToday = d == today, isSelected = inMonth && d.toEpochDay() == selectedDay,
                                    dot = when {
                                        items.isEmpty() -> Color.Transparent
                                        items.any { it.event.isOverdue(now) && it.date == it.event.shownDate() } -> Amber
                                        else -> Color(0xFF7AB8FF)
                                    },
                                    star = items.any { it.event.important },
                                    onClick = { if (inMonth) selectedDay = d.toEpochDay() },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
            }
            val sel = LocalDate.ofEpochDay(selectedDay)
            val list = if (sel.month == ym.month && sel.year == ym.year) byDay[sel].orEmpty() else emptyList()
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    val title = Fmt.date(sel, en, today) + if (sel == today) (if (en) " · Today" else " · 今天") else ""
                    Text(title, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Text1, modifier = Modifier.weight(1f))
                    if (list.isNotEmpty()) Text(if (en) "${list.size}" else "${list.size} 项", fontSize = 12.sp, color = Text3)
                }
            }
            item { OccList(list, en, today, onOpen, emptyText = stringResource(R.string.nothing_on_day)) }
        } else if (mode == 2) {
            // 已完成: everything archived, latest first. Tap the tick to restore.
            val done = events.filter { it.done && !it.purge }
                .sortedWith(compareByDescending<Event> { it.lastDoneOn?.toEpochDay() ?: 0L }.thenByDescending { it.shownDate().toEpochDay() })
            item {
                val sub = if (en) "${done.size} completed" else "已完成 ${done.size} 项"
                ScreenTitle(stringResource(R.string.tab_schedule), sub)
            }
            item { Toolbar(mode, { mode = it }) {} }
            item {
                SectionCard(padding = 4, spacing = 0) {
                    if (done.isEmpty()) {
                        Text(stringResource(R.string.nothing_completed), fontSize = 14.sp, color = Text3, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp))
                    }
                    val topics = LocalTopics.current
                    val titles = LocalTitles.current
                    done.forEachIndexed { i, e ->
                        if (i > 0) RowDivider()
                        val doneOn = e.lastDoneOn
                        EventLine(
                            title = e.title,
                            meta = Fmt.day(e.shownDate(), today, en),
                            topic = topics.nameOf(e.topic),
                            tail = Fmt.metaTail(e, en),
                            time = if (doneOn != null) stringResource(R.string.done_on, Fmt.monthDay(doneOn, en)) else "",
                            done = true,
                            strike = false,
                            ringColor = Muted,
                            timeColor = Muted,
                            checkLabel = stringResource(R.string.restore) + " " + e.title,
                            onCheck = { Repo.restore(ctx, e.id) },
                            onClick = { onOpen(e.id) },
                            important = e.important,
                        parent = titles.parentOf(e),
                        )
                    }
                }
            }
        } else {
            val end = today.plusDays(60)
            // Filters: only important, and/or one topic.
            val shown = events.filter { (!onlyImportant || it.important) && (topicFilter == null || it.topic == topicFilter) }
            val occ = occurrencesIn(shown, today, end)
            val overdue = shown.filter { it.isOverdue(now) }
            item {
                val sub = if (en) "Next 60 days · ${occ.size}" else "未来 60 天 · ${occ.size} 项"
                ScreenTitle(stringResource(R.string.tab_schedule), sub)
            }
            item { Toolbar(mode, { mode = it }) {} }
            item {
                ListFilters(
                    onlyImportant, { onlyImportant = it },
                    topics, topicFilter, { topicFilter = it },
                )
            }
            if (overdue.isNotEmpty()) {
                item { DayHeader(stringResource(R.string.overdue), Amber) }
                item { OccList(overdue.map { Occ(it, it.shownDate()) }, en, today, onOpen, emptyText = "") }
            }
            val groups = occ.groupBy { it.date }.toSortedMap(compareBy<LocalDate> { it.toEpochDay() })
            if (groups.isEmpty()) item {
                Text(stringResource(R.string.nothing_upcoming), color = Text3, fontSize = 14.sp, modifier = Modifier.padding(4.dp))
            }
            groups.forEach { (d, items) ->
                item { DayHeader(Fmt.day(d, today, en), Text1) }
                item { OccList(items, en, today, onOpen, emptyText = "") }
            }
        }
    }
}

private fun occurrencesIn(events: List<Event>, from: LocalDate, to: LocalDate): List<Occ> =
    events.flatMap { e -> e.occurrences(from, to).map { Occ(e, it) } }
        .sortedWith(compareBy<Occ>({ it.date.toEpochDay() }, { !it.event.important }, { timeOf(it) != null }, { timeOf(it) }, { it.event.id }))

private fun timeOf(o: Occ) = if (o.date == o.event.shownDate()) o.event.shownTime() else o.event.time

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Toolbar(mode: Int, onMode: (Int) -> Unit, trailing: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            val labels = listOf(stringResource(R.string.view_list), stringResource(R.string.view_month), stringResource(R.string.view_done))
            labels.forEachIndexed { i, label ->
                SegmentedButton(
                    selected = mode == i,
                    onClick = { onMode(i) },
                    shape = SegmentedButtonDefaults.itemShape(index = i, count = labels.size),
                    icon = {},
                ) { Text(label) }
            }
        }
        trailing()
    }
}

@Composable
private fun MonthNav(ym: YearMonth, en: Boolean, onPrev: () -> Unit, onNext: () -> Unit, onToday: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPrev) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = stringResource(R.string.prev_month), tint = Accent)
        }
        Text(
            Fmt.month(ym, en), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Text1,
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onToday).padding(horizontal = 4.dp, vertical = 8.dp),
        )
        IconButton(onClick = onNext) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = stringResource(R.string.next_month), tint = Accent)
        }
    }
}

@Composable
private fun DayCell(
    d: LocalDate, inMonth: Boolean, isToday: Boolean, isSelected: Boolean, dot: Color, star: Boolean,
    onClick: () -> Unit, modifier: Modifier,
) {
    Column(
        modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(10.dp)).clickable(enabled = inMonth, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        var m = Modifier.size(34.dp).clip(CircleShape)
        if (isSelected) m = m.background(Accent) else if (isToday) m = m.border(1.5.dp, Accent, CircleShape)
        Box(m, contentAlignment = Alignment.Center) {
            Text(
                "${d.dayOfMonth}", fontSize = 15.sp,
                fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal,
                color = when {
                    isSelected -> OnAccent
                    inMonth -> Text1
                    else -> Color(0xFF5A6070)
                },
            )
        }
        Spacer(Modifier.height(if (star) 1.dp else 3.dp))
        // A day with an important task gets the amber star instead of a dot.
        if (star) {
            Icon(Icons.Filled.Star, contentDescription = null, tint = Amber, modifier = Modifier.size(10.dp))
        } else {
            Box(Modifier.size(5.dp).clip(CircleShape).background(dot))
        }
    }
}

@Composable
private fun DayHeader(text: String, color: Color) {
    Text(text, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = color, modifier = Modifier.padding(start = 2.dp, top = 2.dp))
}

@Composable
private fun OccList(list: List<Occ>, en: Boolean, today: LocalDate, onOpen: (Long) -> Unit, emptyText: String) {
    SectionCard(padding = 4, spacing = 0) {
        if (list.isEmpty() && emptyText.isNotEmpty()) {
            Text(emptyText, fontSize = 14.sp, color = Text3, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp))
        }
        list.forEachIndexed { i, o ->
            if (i > 0) RowDivider()
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onOpen(o.event.id) }
                    .heightIn(min = 58.dp).padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.width(4.dp).height(30.dp).clip(RoundedCornerShape(2.dp)).background(barColor(o, today)))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f).padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    TitleText(o.event.title, o.event.important, fontSize = 15, parent = LocalTitles.current.parentOf(o.event))
                    MetaLine(null, LocalTopics.current.nameOf(o.event.topic), Fmt.metaTail(o.event, en))
                }
                Spacer(Modifier.width(8.dp))
                Text(Fmt.timeOrAllDay(timeOf(o), en), fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color(0xFFC9CDD6))
            }
        }
    }
}

private fun barColor(o: Occ, today: LocalDate): Color = when {
    o.date.isBefore(today) -> Amber
    o.event.repeat == Repeat.YEARLY -> Purple
    else -> Accent
}

@Composable
private fun ListFilters(
    onlyImportant: Boolean,
    onImportant: (Boolean) -> Unit,
    topics: List<Topic>,
    topicFilter: String?,
    onTopic: (String?) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val colors = FilterChipDefaults.filterChipColors(selectedContainerColor = TonalBg, selectedLabelColor = Text1)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        FilterChip(
            selected = onlyImportant,
            onClick = { onImportant(!onlyImportant) },
            label = { Text(stringResource(R.string.important)) },
            leadingIcon = { Icon(Icons.Filled.Star, contentDescription = null, tint = Amber, modifier = Modifier.size(16.dp)) },
            colors = colors,
        )
        Box {
            val name = topics.firstOrNull { it.id == topicFilter }?.name
            FilterChip(
                selected = name != null,
                onClick = { open = true },
                label = { Text(name ?: stringResource(R.string.topic)) },
                trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null, modifier = Modifier.size(18.dp)) },
                colors = colors,
            )
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.all_topics)) },
                    onClick = { onTopic(null); open = false },
                )
                topics.forEach { t ->
                    DropdownMenuItem(
                        text = { Text(t.name) },
                        onClick = { onTopic(t.id); open = false },
                    )
                }
            }
        }
    }
}
