package io.github.qymartin.reinminder.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.qymartin.reinminder.R
import io.github.qymartin.reinminder.alarm.Repo
import io.github.qymartin.reinminder.data.Event
import io.github.qymartin.reinminder.data.Repeat
import io.github.qymartin.reinminder.logic.ALL_DAY_REF
import io.github.qymartin.reinminder.logic.Fmt
import io.github.qymartin.reinminder.logic.isOverdue
import io.github.qymartin.reinminder.logic.shownDate
import io.github.qymartin.reinminder.logic.shownTime

@Composable
fun DetailScreen(e: Event, embedded: Boolean, onBack: () -> Unit, onEdit: () -> Unit) {
    val ctx = LocalContext.current
    val en = LocalEn.current
    val now = LocalNow.current
    val today = now.toLocalDate()
    var confirmDelete by remember { mutableStateOf(false) }
    val doneNow = e.done

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (!embedded) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back), tint = Text1)
                }
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onEdit) { Text(stringResource(R.string.edit), fontSize = 15.sp) }
        }

        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.padding(horizontal = 2.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(e.title, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Text1)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LocalTopics.current.nameOf(e.topic)?.let { Chip(it) }
                    if (e.important) Chip(stringResource(R.string.important), color = Amber, background = Bg, border = AmberBorder)
                    if (doneNow) Chip(stringResource(R.string.done_label))
                    if (e.isOverdue(now)) Chip(stringResource(R.string.overdue), color = Amber, background = AmberBg, border = AmberBorder)
                }
            }

            SectionCard {
                LabelTitle(stringResource(R.string.time))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(Fmt.date(e.shownDate(), en, today), fontSize = 17.sp, fontWeight = FontWeight.Medium, color = Text1, modifier = Modifier.weight(1f))
                    Text(Fmt.timeOrAllDay(e.shownTime(), en), fontSize = 17.sp, fontWeight = FontWeight.Medium, color = Text1)
                }
                if (e.postponedTo != null) {
                    Text(
                        stringResource(R.string.postponed_from, Fmt.date(e.date, en, today) + " " + Fmt.timeOrAllDay(e.time, en)),
                        fontSize = 12.sp, color = Text3,
                    )
                }
                KeyValueRow(stringResource(R.string.repeat), Fmt.repeat(e.repeat, e.anchor, en), valueColor = Accent)
            }

            SectionCard(spacing = 4) {
                LabelTitle(stringResource(R.string.reminders))
                val allDay = e.time == null
                if (e.postponedTo != null) {
                    ReminderLine(Fmt.reminder(0, false, en), Fmt.date(e.postponedTo.toLocalDate(), en, today) + " " + Fmt.time(e.postponedTo.toLocalTime(), en))
                } else if (e.reminders.isEmpty()) {
                    Text(stringResource(R.string.no_reminders), fontSize = 14.sp, color = Text3, modifier = Modifier.padding(vertical = 6.dp))
                } else {
                    val ref = e.date.atTime(e.time ?: ALL_DAY_REF)
                    e.reminders.sortedDescending().forEachIndexed { i, m ->
                        if (i > 0) RowDivider()
                        val at = ref.minusMinutes(m.toLong())
                        ReminderLine(Fmt.reminder(m, allDay, en), Fmt.date(at.toLocalDate(), en, today) + " " + Fmt.time(at.toLocalTime(), en))
                    }
                }
                Spacer(Modifier.height(6.dp))
                RowDivider()
                Spacer(Modifier.height(6.dp))
                SwitchRow(
                    stringResource(R.string.alarm_style), stringResource(R.string.alarm_style_note), e.alarmStyle,
                    onChange = { Repo.save(ctx, e.copy(alarmStyle = it)) },
                )
            }

            if (e.notes.isNotBlank()) {
                SectionCard {
                    LabelTitle(stringResource(R.string.notes))
                    Text(e.notes, fontSize = 15.sp, color = Text2, lineHeight = 24.sp)
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (e.done) {
                OutlinedButton(onClick = { confirmDelete = true }, modifier = Modifier.weight(1f).height(48.dp)) { Text(stringResource(R.string.delete)) }
                Button(onClick = { Repo.uncomplete(ctx, e.id) }, modifier = Modifier.weight(2f).height(48.dp)) {
                    Text(stringResource(R.string.undo_done), fontWeight = FontWeight.Bold)
                }
            } else {
                OutlinedButton(onClick = { Repo.postponeToTomorrow(ctx, e.id) }, modifier = Modifier.weight(1f).height(48.dp)) {
                    Text(stringResource(R.string.postpone_tomorrow))
                }
                Button(onClick = { Repo.complete(ctx, e.id); if (!embedded && e.repeat == Repeat.NONE) onBack() }, modifier = Modifier.weight(1f).height(48.dp)) {
                    Text(stringResource(R.string.mark_done), fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = stringResource(R.string.delete_title),
            text = stringResource(R.string.delete_text, e.title),
            confirm = stringResource(R.string.delete),
            onDismiss = { confirmDelete = false },
            onConfirm = { confirmDelete = false; Repo.delete(ctx, e.id); onBack() },
        )
    }
}

@Composable
private fun ReminderLine(label: String, at: String) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(painterResource(R.drawable.ic_bell), contentDescription = null, tint = Text2, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, fontSize = 15.sp, color = Text1)
            Text(at, fontSize = 12.sp, color = Text3)
        }
    }
}
