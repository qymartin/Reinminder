package io.github.qymartin.reinminder.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.qymartin.reinminder.R
import io.github.qymartin.reinminder.alarm.Repo
import io.github.qymartin.reinminder.data.Event
import io.github.qymartin.reinminder.data.Repeat
import io.github.qymartin.reinminder.data.TopicStore
import io.github.qymartin.reinminder.logic.ALL_DAY_REF
import io.github.qymartin.reinminder.logic.Fmt
import io.github.qymartin.reinminder.logic.QuickParser
import io.github.qymartin.reinminder.logic.shownDate
import io.github.qymartin.reinminder.logic.shownTime
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

private val PRESETS = listOf(1440, 720, 60)
private const val CUSTOM = -1

/**
 * New / edit event, one page, top to bottom: content, time (date, time, repeat), reminder
 * (24 h / 12 h / 1 h before, or a custom date and time), topic, important.
 * Field state is saveable, so rotating or unfolding keeps unsaved edits.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditScreen(initial: Event, isNew: Boolean, onClose: () -> Unit, onDeleted: () -> Unit) {
    val ctx = LocalContext.current
    val en = LocalEn.current
    val now = LocalNow.current
    val today = now.toLocalDate()
    val topicStore = remember { TopicStore.get(ctx) }
    val topics by topicStore.topics.collectAsState()

    val startTime = initial.shownTime() ?: ALL_DAY_REF
    val startMoment = initial.shownDate().atTime(startTime)
    val firstOffset = initial.reminders.maxOrNull() ?: 60

    var title by rememberSaveable { mutableStateOf(initial.title) }
    var dateDay by rememberSaveable { mutableStateOf(initial.shownDate().toEpochDay()) }
    var timeText by rememberSaveable { mutableStateOf(startTime.toString()) }
    var repeatKey by rememberSaveable { mutableStateOf(initial.repeat.key) }
    var reminderMode by rememberSaveable { mutableStateOf(if (firstOffset in PRESETS) firstOffset else CUSTOM) }
    var customText by rememberSaveable { mutableStateOf(startMoment.minusMinutes(firstOffset.toLong()).toString()) }
    var topicId by rememberSaveable { mutableStateOf(initial.topic) }
    var important by rememberSaveable { mutableStateOf(initial.important) }
    var askScope by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    val date = LocalDate.ofEpochDay(dateDay)
    val time = LocalTime.parse(timeText)
    val moment = date.atTime(time)
    val repeat = Repeat.of(repeatKey)
    val customAt = LocalDateTime.parse(customText)
    val offset = if (reminderMode == CUSTOM) Duration.between(customAt, moment).toMinutes().toInt() else reminderMode
    val customAfterEvent = reminderMode == CUSTOM && offset < 0
    // Words like "周五下午3点" or "#健康" in the content can fill the fields with one tap.
    val parsed = remember(title, now) { QuickParser.parse(title, now) }
    val hasSuggestion = parsed.title != title.trim() &&
        (parsed.date != null || parsed.time != null || parsed.repeat != null || parsed.topic != null || parsed.important)

    fun built(newDate: LocalDate, newTime: LocalTime) = initial.copy(
        title = title.trim(),
        topic = topicId,
        anchor = if (repeat == Repeat.NONE || newDate != initial.date || repeat != initial.repeat) newDate else initial.anchor,
        date = newDate,
        time = newTime,
        repeat = repeat,
        reminders = listOf(offset),
        important = important,
        postponedTo = null,
        done = if (newDate != initial.date || newTime != initial.time) false else initial.done,
    )

    val whenChanged = date != initial.shownDate() || time != initial.shownTime()

    fun save() {
        if (!isNew && initial.repeat != Repeat.NONE && repeat != Repeat.NONE && whenChanged) {
            askScope = true
            return
        }
        val e = if (!isNew && !whenChanged) {
            // Only other fields changed: keep the series date and any postponed occurrence as they were.
            built(initial.date, initial.time ?: time).copy(postponedTo = initial.postponedTo, done = initial.done)
        } else {
            built(date, time)
        }
        Repo.save(ctx, e)
        onClose()
    }

    fun applySuggestion() {
        parsed.date?.let { dateDay = it.toEpochDay() }
        parsed.time?.let { timeText = it.toString() }
        parsed.repeat?.let { repeatKey = it.key }
        parsed.topic?.let { name -> topicId = topicStore.add(name).id }
        if (parsed.important) important = true
        title = parsed.title
    }

    fun pickDate(d: LocalDate, onPick: (LocalDate) -> Unit) {
        DatePickerDialog(ctx, { _, y, m, day -> onPick(LocalDate.of(y, m + 1, day)) }, d.year, d.monthValue - 1, d.dayOfMonth).show()
    }

    fun pickTime(t: LocalTime, onPick: (LocalTime) -> Unit) {
        TimePickerDialog(ctx, { _, h, m -> onPick(LocalTime.of(h, m)) }, t.hour, t.minute, !en).show()
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.cancel), tint = Text1) }
            Text(
                stringResource(if (isNew) R.string.new_event else R.string.edit_event),
                fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Text1, modifier = Modifier.weight(1f),
            )
        }

        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 1. Content
            OutlinedTextField(
                value = title, onValueChange = { title = it },
                label = { Text(stringResource(R.string.content)) },
                placeholder = { Text(stringResource(R.string.content_hint)) },
                modifier = Modifier.fillMaxWidth(),
            )
            if (hasSuggestion) {
                val importantText = stringResource(R.string.important)
                val parts = buildList {
                    if (parsed.date != null || parsed.time != null) {
                        val d = parsed.date ?: date
                        add(Fmt.day(d, today, en) + (parsed.time?.let { " " + Fmt.time(it, en) } ?: ""))
                    }
                    parsed.repeat?.let { r -> Fmt.repeatShort(r, en)?.let { add(it) } }
                    parsed.topic?.let { add(it) }
                    if (parsed.important) add(importantText)
                }
                AssistChip(
                    onClick = { applySuggestion() },
                    label = { Text(stringResource(R.string.apply_detected, parts.joinToString(" · "))) },
                )
            }

            // 2. Time
            SectionCard {
                LabelTitle(stringResource(R.string.time))
                KeyValueRow(stringResource(R.string.date), Fmt.date(date, en, today), valueColor = Accent) {
                    pickDate(date) { dateDay = it.toEpochDay() }
                }
                KeyValueRow(stringResource(R.string.time_of_day), Fmt.time(time, en), valueColor = Accent) {
                    pickTime(time) { timeText = it.toString() }
                }
                Text(stringResource(R.string.repeat), fontSize = 15.sp, color = Text1)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Repeat.entries.forEach { r ->
                        FilterChip(
                            selected = r == repeat, onClick = { repeatKey = r.key },
                            label = { Text(Fmt.repeatShort(r, en) ?: Fmt.repeat(Repeat.NONE, date, en)) },
                            colors = chipColors(),
                        )
                    }
                }
                if (repeat != Repeat.NONE) Text(Fmt.repeat(repeat, date, en), fontSize = 12.sp, color = Text3)
            }

            // 3. Reminder
            SectionCard {
                LabelTitle(stringResource(R.string.reminder))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PRESETS.forEach { m ->
                        FilterChip(
                            selected = reminderMode == m, onClick = { reminderMode = m },
                            label = { Text(Fmt.reminder(m, false, en)) }, colors = chipColors(),
                        )
                    }
                    FilterChip(
                        selected = reminderMode == CUSTOM,
                        onClick = {
                            if (reminderMode != CUSTOM) {
                                // Start from the current choice, so the custom time is close to what was set.
                                customText = moment.minusMinutes(reminderMode.toLong()).toString()
                                reminderMode = CUSTOM
                            }
                        },
                        label = { Text(stringResource(R.string.custom)) }, colors = chipColors(),
                    )
                }
                if (reminderMode == CUSTOM) {
                    KeyValueRow(stringResource(R.string.remind_date), Fmt.date(customAt.toLocalDate(), en, today), valueColor = Accent) {
                        pickDate(customAt.toLocalDate()) { customText = it.atTime(customAt.toLocalTime()).toString() }
                    }
                    KeyValueRow(stringResource(R.string.remind_time), Fmt.time(customAt.toLocalTime(), en), valueColor = Accent) {
                        pickTime(customAt.toLocalTime()) { customText = customAt.toLocalDate().atTime(it).toString() }
                    }
                    if (customAfterEvent) {
                        Text(stringResource(R.string.custom_after_event), fontSize = 13.sp, color = Amber)
                    }
                } else {
                    val at = moment.minusMinutes(reminderMode.toLong())
                    Text(
                        stringResource(R.string.reminds_at, Fmt.day(at.toLocalDate(), today, en) + " " + Fmt.time(at.toLocalTime(), en)),
                        fontSize = 12.sp, color = Text3,
                    )
                }
            }

            // 4. Topic
            SectionCard(spacing = 6) {
                LabelTitle(stringResource(R.string.topic))
                TopicPicker(topics, topicId) { topicId = it }
            }

            // 5. Important
            SectionCard {
                SwitchRow(stringResource(R.string.important), stringResource(R.string.important_note), important, onChange = { important = it })
            }
            Spacer(Modifier.height(8.dp))
        }

        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (!isNew) {
                OutlinedButton(onClick = { confirmDelete = true }, modifier = Modifier.weight(1f).height(48.dp)) { Text(stringResource(R.string.delete)) }
            }
            Button(
                onClick = { save() },
                enabled = title.isNotBlank() && !customAfterEvent,
                modifier = Modifier.weight(2f).height(48.dp),
            ) {
                Text(stringResource(R.string.save), fontWeight = FontWeight.Bold)
            }
        }
    }

    if (askScope) {
        ScopeDialog(
            onDismiss = { askScope = false },
            onOnlyThis = {
                askScope = false
                // Keep the series as it was; move only this occurrence.
                val series = built(initial.date, initial.time ?: time)
                Repo.save(ctx, series.copy(postponedTo = moment))
                onClose()
            },
            onAll = {
                askScope = false
                Repo.save(ctx, built(date, time).copy(anchor = date))
                onClose()
            },
        )
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = stringResource(R.string.delete_title),
            text = stringResource(R.string.delete_text, initial.title),
            confirm = stringResource(R.string.delete),
            onDismiss = { confirmDelete = false },
            onConfirm = {
                confirmDelete = false
                Repo.delete(ctx, initial.id)
                onDeleted()
            },
        )
    }
}

@Composable
private fun chipColors() = FilterChipDefaults.filterChipColors(selectedContainerColor = TonalBg, selectedLabelColor = Text1)

@Composable
private fun ScopeDialog(onDismiss: () -> Unit, onOnlyThis: () -> Unit, onAll: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardBg,
        title = { Text(stringResource(R.string.scope_title)) },
        text = { Text(stringResource(R.string.scope_text)) },
        confirmButton = {
            Column(horizontalAlignment = Alignment.End) {
                TextButton(onClick = onAll) { Text(stringResource(R.string.scope_all)) }
                TextButton(onClick = onOnlyThis) { Text(stringResource(R.string.scope_this)) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        },
    )
}
