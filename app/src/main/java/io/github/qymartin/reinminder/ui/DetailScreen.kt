package io.github.qymartin.reinminder.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import io.github.qymartin.reinminder.logic.eventOrder
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.filled.Close
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
fun DetailScreen(
    e: Event,
    events: List<Event>,
    embedded: Boolean,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onOpen: (Long) -> Unit,
    onAddSubtask: () -> Unit,
) {
    val ctx = LocalContext.current
    val en = LocalEn.current
    val now = LocalNow.current
    val today = now.toLocalDate()
    var confirmDelete by remember { mutableStateOf(false) }
    var showComplete by remember { mutableStateOf(false) }
    var detaching by remember { mutableStateOf<Event?>(null) }
    val doneNow = e.done
    val parent = e.parentId?.let { pid -> events.firstOrNull { it.id == pid } }
    val subtasks = events.filter { it.parentId == e.id }.sortedWith(compareBy<Event>({ it.done }).then(eventOrder))

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
                if (parent != null) {
                    // Tap to go to the main task. Removing a subtask is done from the main task's page.
                    Text(
                        stringResource(R.string.main_task_is, parent.title) + "  ›", fontSize = 14.sp, color = ParentColor,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { onOpen(parent.id) }.padding(vertical = 6.dp),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (e.important) Chip(stringResource(R.string.important), color = Amber, background = Bg, border = AmberBorder)
                    LocalTopics.current.nameOf(e.topic)?.let { Box(Modifier.align(Alignment.CenterVertically)) { TopicBadge(it, fontSize = 13) } }
                    if (doneNow) Chip(stringResource(R.string.done_label))
                    if (e.isOverdue(now)) Chip(stringResource(R.string.overdue), color = Amber, background = AmberBg, border = AmberBorder)
                }
            }

            // One card: when it is, how it repeats, when it reminds.
            SectionCard {
                LabelTitle(stringResource(R.string.reminder))
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
                if (e.repeat != Repeat.NONE) {
                    KeyValueRow(stringResource(R.string.repeat), Fmt.repeat(e.repeat, e.anchor, en, e.interval), valueColor = Text2)
                }
                // Only an earlier reminder needs its own line; "at time" is the date and time above.
                val lead = e.reminders.maxOrNull() ?: 0
                if (e.postponedTo == null && lead > 0) {
                    val at = e.date.atTime(e.time ?: ALL_DAY_REF).minusMinutes(lead.toLong())
                    KeyValueRow(
                        Fmt.reminder(lead, false, en),
                        Fmt.day(at.toLocalDate(), today, en) + " " + Fmt.time(at.toLocalTime(), en),
                        valueColor = Text2,
                    )
                }
                if (e.alarmStyle) {
                    KeyValueRow(stringResource(R.string.alarm_style), stringResource(R.string.every_10_min), valueColor = Text2)
                }
            }

            // Subtasks: one level only, so a subtask has no subtasks of its own.
            if (e.parentId == null) {
                SectionCard(spacing = 0) {
                    LabelTitle(stringResource(R.string.subtasks))
                    val topics = LocalTopics.current
                    subtasks.forEachIndexed { i, sub ->
                        if (i > 0) RowDivider()
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable { onOpen(sub.id) },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f).padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                TitleText(sub.title, sub.important, muted = sub.done, strike = sub.done && sub.purge, fontSize = 15)
                                MetaLine(Fmt.day(sub.shownDate(), today, en), topics.nameOf(sub.topic), Fmt.metaTail(sub, en), muted = sub.done, fontSize = 12)
                            }
                            Text(Fmt.timeOrAllDay(sub.shownTime(), en), fontSize = 14.sp, color = if (sub.done) Muted else Text2)
                            IconButton(onClick = { detaching = sub }) {
                                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.detach_named, sub.title), tint = Text3, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                    OutlinedButton(onClick = onAddSubtask, modifier = Modifier.padding(top = 8.dp)) {
                        Text(stringResource(R.string.add_subtask))
                    }
                }
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
                Button(onClick = { Repo.restore(ctx, e.id) }, modifier = Modifier.weight(2f).height(48.dp)) {
                    Text(stringResource(R.string.restore), fontWeight = FontWeight.Bold)
                }
            } else {
                OutlinedButton(onClick = { Repo.postponeToTomorrow(ctx, e.id) }, modifier = Modifier.weight(1f).height(48.dp)) {
                    Text(stringResource(R.string.postpone_tomorrow))
                }
                Button(onClick = { showComplete = true }, modifier = Modifier.weight(1f).height(48.dp)) {
                    Text(stringResource(R.string.mark_done), fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    detaching?.let { sub ->
        ConfirmDialog(
            title = stringResource(R.string.detach_named, sub.title),
            text = stringResource(R.string.detach_text),
            confirm = stringResource(R.string.detach),
            onDismiss = { detaching = null },
            onConfirm = { Repo.detach(ctx, sub.id); detaching = null },
        )
    }

    if (showComplete) {
        CompleteFlow(e, onClose = { showComplete = false })
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = stringResource(R.string.delete_title),
            text = stringResource(R.string.delete_text, e.title) +
                if (subtasks.isNotEmpty()) "\n" + stringResource(R.string.delete_subtasks_too, subtasks.size) else "",
            confirm = stringResource(R.string.delete),
            onDismiss = { confirmDelete = false },
            onConfirm = { confirmDelete = false; Repo.delete(ctx, e.id); onBack() },
        )
    }
}
