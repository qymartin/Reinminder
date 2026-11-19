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
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import io.github.qymartin.reinminder.data.Category
import io.github.qymartin.reinminder.data.Event
import io.github.qymartin.reinminder.data.Prefs
import io.github.qymartin.reinminder.data.Repeat
import io.github.qymartin.reinminder.logic.ALL_DAY_REF
import io.github.qymartin.reinminder.logic.Fmt
import io.github.qymartin.reinminder.logic.shownDate
import io.github.qymartin.reinminder.logic.shownTime
import java.time.LocalDate
import java.time.LocalTime

/** Create or change an event. Field state is saveable, so rotating or unfolding keeps unsaved edits. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditScreen(initial: Event, isNew: Boolean, onClose: () -> Unit, onDeleted: () -> Unit) {
    val ctx = LocalContext.current
    val en = LocalEn.current
    val today = LocalNow.current.toLocalDate()
    val prefs = Prefs(ctx)

    var title by rememberSaveable { mutableStateOf(initial.title) }
    var categoryKey by rememberSaveable { mutableStateOf(initial.category?.key) }
    var dateDay by rememberSaveable { mutableStateOf(initial.shownDate().toEpochDay()) }
    var timeText by rememberSaveable { mutableStateOf(initial.shownTime()?.toString()) }
    var repeatKey by rememberSaveable { mutableStateOf(initial.repeat.key) }
    var remindersText by rememberSaveable { mutableStateOf(initial.reminders.joinToString(",")) }
    var important by rememberSaveable { mutableStateOf(initial.important) }
    var alarmStyle by rememberSaveable { mutableStateOf(initial.alarmStyle) }
    var notes by rememberSaveable { mutableStateOf(initial.notes) }
    var showReminders by rememberSaveable { mutableStateOf(false) }
    var askScope by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    val date = LocalDate.ofEpochDay(dateDay)
    val time = timeText?.let { LocalTime.parse(it) }
    val repeat = Repeat.of(repeatKey)
    val category = Category.of(categoryKey)
    val reminders = remindersText.split(',').mapNotNull { it.trim().toIntOrNull() }
    val allDay = time == null

    fun built(newDate: LocalDate, newTime: LocalTime?) = initial.copy(
        title = title.trim(),
        category = category,
        anchor = if (repeat == Repeat.NONE || newDate != initial.date || repeat != initial.repeat) newDate else initial.anchor,
        date = newDate,
        time = newTime,
        repeat = repeat,
        reminders = reminders.sortedDescending(),
        important = important,
        alarmStyle = alarmStyle,
        notes = notes.trim(),
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
            built(initial.date, initial.time).copy(postponedTo = initial.postponedTo, done = initial.done)
        } else {
            built(date, time)
        }
        Repo.save(ctx, e)
        onClose()
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
            OutlinedTextField(
                value = title, onValueChange = { title = it }, singleLine = true,
                label = { Text(stringResource(R.string.title)) }, modifier = Modifier.fillMaxWidth(),
            )

            SectionCard {
                LabelTitle(stringResource(R.string.time))
                KeyValueRow(stringResource(R.string.date), Fmt.date(date, en, today), valueColor = Accent) {
                    DatePickerDialog(ctx, { _, y, m, d -> dateDay = LocalDate.of(y, m + 1, d).toEpochDay() }, date.year, date.monthValue - 1, date.dayOfMonth).show()
                }
                SwitchRow(stringResource(R.string.all_day), null, allDay, onChange = { on ->
                    timeText = if (on) null else (initial.time ?: LocalTime.of(9, 0)).toString()
                    remindersText = (if (on) prefs.allDayDefaults else prefs.timedDefaults).joinToString(",")
                })
                if (!allDay) {
                    val t = time ?: ALL_DAY_REF
                    KeyValueRow(stringResource(R.string.time_of_day), Fmt.time(t, en), valueColor = Accent) {
                        TimePickerDialog(ctx, { _, h, m -> timeText = LocalTime.of(h, m).toString() }, t.hour, t.minute, !en).show()
                    }
                }
                LabelTitle(stringResource(R.string.repeat))
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

            SectionCard {
                LabelTitle(stringResource(R.string.reminders))
                Text(
                    Fmt.reminders(reminders, allDay, en) ?: stringResource(R.string.no_reminders),
                    fontSize = 15.sp, color = Text1,
                )
                OutlinedButton(onClick = { showReminders = true }) { Text(stringResource(R.string.change_reminders)) }
                SwitchRow(stringResource(R.string.alarm_style), stringResource(R.string.alarm_style_note), alarmStyle, onChange = { alarmStyle = it })
            }

            SectionCard {
                LabelTitle(stringResource(R.string.category))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = category == null, onClick = { categoryKey = null }, label = { Text(stringResource(R.string.none)) }, colors = chipColors())
                    Category.entries.forEach { c ->
                        FilterChip(
                            selected = c == category, onClick = { categoryKey = c.key },
                            label = { Text(Fmt.category(c, en)) }, colors = chipColors(),
                        )
                    }
                }
                SwitchRow(stringResource(R.string.important), null, important, onChange = { important = it })
            }

            OutlinedTextField(
                value = notes, onValueChange = { notes = it }, minLines = 3,
                label = { Text(stringResource(R.string.notes)) }, modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
        }

        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (!isNew) {
                OutlinedButton(onClick = { confirmDelete = true }, modifier = Modifier.weight(1f).height(48.dp)) { Text(stringResource(R.string.delete)) }
            }
            Button(onClick = { save() }, enabled = title.isNotBlank(), modifier = Modifier.weight(2f).height(48.dp)) {
                Text(stringResource(R.string.save), fontWeight = FontWeight.Bold)
            }
        }
    }

    if (showReminders) {
        ReminderDialog(allDay, reminders, onDismiss = { showReminders = false }, onConfirm = {
            remindersText = it.joinToString(",")
            showReminders = false
        })
    }

    if (askScope) {
        ScopeDialog(
            onDismiss = { askScope = false },
            onOnlyThis = {
                askScope = false
                // Keep the series as it was; move only this occurrence.
                val series = built(initial.date, initial.time)
                Repo.save(ctx, series.copy(postponedTo = date.atTime(time ?: ALL_DAY_REF)))
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
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardBg,
        title = { Text(stringResource(R.string.scope_title)) },
        text = { Text(stringResource(R.string.scope_text)) },
        confirmButton = {
            Column(horizontalAlignment = Alignment.End) {
                androidx.compose.material3.TextButton(onClick = onAll) { Text(stringResource(R.string.scope_all)) }
                androidx.compose.material3.TextButton(onClick = onOnlyThis) { Text(stringResource(R.string.scope_this)) }
                androidx.compose.material3.TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        },
    )
}
