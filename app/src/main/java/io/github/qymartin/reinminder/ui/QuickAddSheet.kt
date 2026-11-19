package io.github.qymartin.reinminder.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.qymartin.reinminder.R
import io.github.qymartin.reinminder.data.Category
import io.github.qymartin.reinminder.data.Event
import io.github.qymartin.reinminder.data.EventStore
import io.github.qymartin.reinminder.data.MAX_REMINDERS
import io.github.qymartin.reinminder.data.Prefs
import io.github.qymartin.reinminder.data.Repeat
import io.github.qymartin.reinminder.logic.Fmt
import io.github.qymartin.reinminder.logic.QuickParser
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun QuickAddSheet(onDismiss: () -> Unit, onSave: (Event) -> Unit, onMore: (Event) -> Unit) {
    val ctx = LocalContext.current
    val en = LocalEn.current
    val now = LocalNow.current
    val today = now.toLocalDate()
    val prefs = remember { Prefs(ctx) }
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var text by rememberSaveable { mutableStateOf("") }
    val parsed = remember(text, now) { QuickParser.parse(text, now) }
    val allDay = parsed.time == null
    val defaults = when {
        parsed.category == Category.ANNIVERSARY -> prefs.anniversaryDefaults
        allDay -> prefs.allDayDefaults
        else -> prefs.timedDefaults
    }
    // The user's own pick; reset when the event switches between all-day and timed.
    var custom by remember(allDay) { mutableStateOf<List<Int>?>(null) }
    val reminders = custom ?: defaults
    val canSave = parsed.title.isNotBlank()

    fun build(): Event {
        val date = parsed.date ?: today
        return Event(
            id = EventStore.get(ctx).newId(),
            title = parsed.title.ifBlank { text.trim() },
            category = parsed.category,
            anchor = date,
            date = date,
            time = parsed.time,
            repeat = parsed.repeat ?: Repeat.NONE,
            reminders = reminders.distinct().sortedDescending().take(MAX_REMINDERS),
            important = parsed.important,
            createdAt = System.currentTimeMillis(),
        )
    }

    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        delay(250)
        try {
            focus.requestFocus()
            keyboard?.show()
        } catch (e: IllegalStateException) {
            // Not attached yet; the user can tap the field.
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = CardBg) {
        Column(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(stringResource(R.string.new_event), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Text1)
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(stringResource(R.string.quick_label)) },
                placeholder = { Text(stringResource(R.string.quick_example)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (canSave) onSave(build()) }),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )

            if (text.isNotBlank()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.understood_as), fontSize = 12.sp, color = Text3)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        val d = parsed.date ?: today
                        val whenText = Fmt.day(d, today, en) + " · " + Fmt.timeOrAllDay(parsed.time, en)
                        Chip(whenText)
                        parsed.category?.let { Chip(Fmt.category(it, en)) }
                        parsed.repeat?.let { r -> Chip(Fmt.repeat(r, d, en)) }
                        if (parsed.important) Chip(stringResource(R.string.important), color = Amber, background = AmberBg, border = AmberBorder)
                        if (parsed.title.isNotBlank()) Chip(stringResource(R.string.title_is, parsed.title))
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LabelTitle(stringResource(R.string.reminders_pick))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    val options = ((if (allDay) Presets.allDayQuick else Presets.timedQuick) + defaults).distinct().sorted()
                    options.forEach { m ->
                        val on = m in reminders
                        FilterChip(
                            selected = on,
                            onClick = { custom = if (on) reminders - m else (reminders + m).take(MAX_REMINDERS) },
                            label = { Text(Fmt.reminder(m, allDay, en)) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = TonalBg, selectedLabelColor = Text1),
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                OutlinedButton(onClick = { onMore(build()) }, modifier = Modifier.weight(1f).height(48.dp)) {
                    Text(stringResource(R.string.more_options))
                }
                Button(onClick = { onSave(build()) }, enabled = canSave, modifier = Modifier.weight(2f).height(48.dp)) {
                    Text(stringResource(R.string.save), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
