package io.github.qymartin.reinminder.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.qymartin.reinminder.R

object Presets {
    val snooze = listOf(5, 10, 15, 30, 60)
}

/** Pick one value. */
@Composable
fun <T> ChoiceDialog(title: String, options: List<Pair<String, T>>, selected: T, onDismiss: () -> Unit, onPick: (T) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardBg,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                options.forEach { (label, value) ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { onPick(value) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = value == selected, onClick = null)
                        Text(label, fontSize = 15.sp, modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
fun ConfirmDialog(title: String, text: String, confirm: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardBg,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** Password for an encrypted backup. [twice] asks for it again when exporting. */
@Composable
fun PasswordDialog(title: String, twice: Boolean, error: String?, onDismiss: () -> Unit, onDone: (CharArray) -> Unit) {
    var pw by remember { mutableStateOf("") }
    var pw2 by remember { mutableStateOf("") }
    val tooShort = pw.length < 4
    val mismatch = twice && pw != pw2
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardBg,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = pw, onValueChange = { pw = it }, singleLine = true,
                    label = { Text(stringResource(R.string.password)) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                )
                if (twice) {
                    OutlinedTextField(
                        value = pw2, onValueChange = { pw2 = it }, singleLine = true,
                        label = { Text(stringResource(R.string.password_again)) },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    )
                    Text(stringResource(R.string.password_warning), fontSize = 12.sp, color = Text3)
                }
                if (error != null) Text(error, fontSize = 13.sp, color = Amber)
            }
        },
        confirmButton = {
            TextButton(onClick = { onDone(pw.toCharArray()) }, enabled = !tooShort && !mismatch) { Text(stringResource(R.string.ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/**
 * What tapping a task's tick circle offers. One-off: 推迟到明天 / 完成并存档 / 完成并删除.
 * Repeating: 推迟到明天 / 完成本次 (the series itself is deleted from its edit page).
 */
@Composable
fun CompleteDialog(
    title: String,
    repeating: Boolean,
    onDismiss: () -> Unit,
    onPostpone: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardBg,
        title = { Text(title, maxLines = 2) },
        text = {
            Column {
                MenuRow(stringResource(R.string.postpone_tomorrow), null, onPostpone)
                if (repeating) {
                    MenuRow(stringResource(R.string.done_once), stringResource(R.string.done_once_note), onArchive)
                } else {
                    MenuRow(stringResource(R.string.done_archive), stringResource(R.string.done_archive_note), onArchive)
                    MenuRow(stringResource(R.string.done_delete), stringResource(R.string.done_delete_note), onDelete)
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun MenuRow(label: String, note: String?, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(label, fontSize = 16.sp, color = Text1)
        if (note != null) Text(note, fontSize = 12.sp, color = Text3)
    }
}
