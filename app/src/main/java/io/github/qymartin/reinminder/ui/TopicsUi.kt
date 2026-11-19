package io.github.qymartin.reinminder.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.qymartin.reinminder.R
import io.github.qymartin.reinminder.alarm.Repo
import io.github.qymartin.reinminder.data.Topic
import io.github.qymartin.reinminder.data.TopicStore

/**
 * Topic chips (single choice; tap the chosen one again to clear), a "+ 新主题" chip,
 * and a "管理" button to rename or delete topics.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TopicPicker(topics: List<Topic>, selected: String?, onSelect: (String?) -> Unit) {
    val ctx = LocalContext.current
    var adding by remember { mutableStateOf(false) }
    var managing by remember { mutableStateOf(false) }

    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        topics.forEach { t ->
            FilterChip(
                selected = t.id == selected,
                onClick = { onSelect(if (t.id == selected) null else t.id) },
                label = { Text(t.name) },
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = TonalBg, selectedLabelColor = Text1),
            )
        }
        AssistChip(
            onClick = { adding = true },
            label = { Text(stringResource(R.string.new_topic)) },
            leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null, tint = Accent) },
        )
    }
    if (topics.isNotEmpty()) {
        TextButton(onClick = { managing = true }) { Text(stringResource(R.string.manage_topics)) }
    }

    if (adding) {
        TopicNameDialog(
            title = stringResource(R.string.new_topic),
            initial = "",
            onDismiss = { adding = false },
            onDone = { name ->
                adding = false
                onSelect(TopicStore.get(ctx).add(name).id)
            },
        )
    }
    if (managing) {
        TopicManager(topics, onDismiss = { managing = false }, onDeleted = { id -> if (id == selected) onSelect(null) })
    }
}

@Composable
fun TopicNameDialog(title: String, initial: String, onDismiss: () -> Unit, onDone: (String) -> Unit) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardBg,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name, onValueChange = { name = it.take(20) }, singleLine = true,
                label = { Text(stringResource(R.string.topic_name)) },
            )
        },
        confirmButton = { TextButton(onClick = { onDone(name.trim()) }, enabled = name.isNotBlank()) { Text(stringResource(R.string.ok)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** List of topics, each with rename and delete. Deleting keeps the events, without a topic. */
@Composable
fun TopicManager(topics: List<Topic>, onDismiss: () -> Unit, onDeleted: (String) -> Unit = {}) {
    val ctx = LocalContext.current
    var renaming by remember { mutableStateOf<Topic?>(null) }
    var deleting by remember { mutableStateOf<Topic?>(null) }
    var adding by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardBg,
        title = { Text(stringResource(R.string.topics)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                topics.forEach { t ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(t.name, fontSize = 15.sp, color = Text1, modifier = Modifier.weight(1f))
                        IconButton(onClick = { renaming = t }) {
                            Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.rename_topic, t.name), tint = Text2)
                        }
                        IconButton(onClick = { deleting = t }) {
                            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.delete_topic, t.name), tint = Text2)
                        }
                    }
                }
                TextButton(onClick = { adding = true }) { Text(stringResource(R.string.new_topic)) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.done_label)) } },
    )

    renaming?.let { t ->
        TopicNameDialog(stringResource(R.string.rename), t.name, onDismiss = { renaming = null }) { name ->
            TopicStore.get(ctx).rename(t.id, name)
            renaming = null
        }
    }
    deleting?.let { t ->
        ConfirmDialog(
            title = stringResource(R.string.delete_topic, t.name),
            text = stringResource(R.string.delete_topic_text),
            confirm = stringResource(R.string.delete),
            onDismiss = { deleting = null },
            onConfirm = {
                Repo.deleteTopic(ctx, t.id)
                onDeleted(t.id)
                deleting = null
            },
        )
    }
    if (adding) {
        TopicNameDialog(stringResource(R.string.new_topic), "", onDismiss = { adding = false }) { name ->
            TopicStore.get(ctx).add(name)
            adding = false
        }
    }
}
