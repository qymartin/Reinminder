package io.github.qymartin.reinminder.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.Alignment
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.qymartin.reinminder.R
import io.github.qymartin.reinminder.alarm.Alarms
import io.github.qymartin.reinminder.alarm.Notifier
import io.github.qymartin.reinminder.alarm.Repo
import io.github.qymartin.reinminder.backup.BadBackupException
import io.github.qymartin.reinminder.backup.Backup
import io.github.qymartin.reinminder.backup.WrongPasswordException
import io.github.qymartin.reinminder.data.TopicStore
import io.github.qymartin.reinminder.data.Event
import io.github.qymartin.reinminder.data.EventStore
import io.github.qymartin.reinminder.data.Prefs
import io.github.qymartin.reinminder.logic.Fmt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

private fun toast(ctx: Context, msg: String) = Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(events: List<Event>) {
    val ctx = LocalContext.current
    val en = LocalEn.current
    val today = LocalNow.current.toLocalDate()
    val prefs = remember { Prefs(ctx) }
    val scope = rememberCoroutineScope()
    val pkg = ctx.packageName

    // Settings that live in prefs, mirrored as state so the screen updates.
    val topics by remember { TopicStore.get(ctx) }.topics.collectAsState()
    var snooze by remember { mutableIntStateOf(prefs.snoozeMinutes) }
    var encrypt by remember { mutableStateOf(prefs.encryptBackup) }
    var lastExport by remember { mutableStateOf(prefs.lastExportAt to prefs.lastExportEncrypted) }
    var editing by remember { mutableStateOf<String?>(null) } // which choice dialog is open
    var managingTopics by remember { mutableStateOf(false) }

    // Backup flow state.
    var exportPassword by remember { mutableStateOf<CharArray?>(null) }
    var askExportPassword by remember { mutableStateOf(false) }
    var importBytes by remember { mutableStateOf<ByteArray?>(null) }
    var importError by remember { mutableStateOf<String?>(null) }
    var pendingImport by remember { mutableStateOf<Backup.Contents?>(null) }
    var busy by remember { mutableStateOf(false) }

    val msgExported = stringResource(R.string.exported)
    val msgFailed = stringResource(R.string.backup_failed)
    val msgNotBackup = stringResource(R.string.not_a_backup)
    val msgWrongPw = stringResource(R.string.wrong_password)
    val msgImported = stringResource(R.string.imported)

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val pw = exportPassword
        exportPassword = null
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            try {
                val now = System.currentTimeMillis()
                val bytes = withContext(Dispatchers.Default) {
                    val json = Backup.toJson(EventStore.get(ctx).all(), TopicStore.get(ctx).all(), now).toByteArray()
                    if (pw != null) Backup.encrypt(json, pw).also { pw.fill(' ') } else json
                }
                withContext(Dispatchers.IO) {
                    ctx.contentResolver.openOutputStream(uri, "w")?.use { it.write(bytes) } ?: error("no stream")
                }
                prefs.lastExportAt = now
                prefs.lastExportEncrypted = pw != null
                lastExport = now to (pw != null)
                toast(ctx, msgExported)
            } catch (e: Exception) {
                toast(ctx, msgFailed)
            } finally {
                busy = false
            }
        }
    }

    fun loadImport(bytes: ByteArray, pw: CharArray?) {
        busy = true
        scope.launch {
            try {
                val list = withContext(Dispatchers.Default) {
                    val plain = if (pw != null) Backup.decrypt(bytes, pw).also { pw.fill(' ') } else bytes
                    Backup.fromJson(String(plain, Charsets.UTF_8))
                }
                importBytes = null
                importError = null
                pendingImport = list
            } catch (e: WrongPasswordException) {
                importError = msgWrongPw
            } catch (e: BadBackupException) {
                importBytes = null
                toast(ctx, msgNotBackup)
            } catch (e: Exception) {
                importBytes = null
                toast(ctx, msgFailed)
            } finally {
                busy = false
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val bytes = try {
                withContext(Dispatchers.IO) { ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } }
            } catch (e: Exception) {
                null
            }
            when {
                bytes == null -> toast(ctx, msgFailed)
                Backup.isEncrypted(bytes) -> { importError = null; importBytes = bytes }
                else -> loadImport(bytes, null)
            }
        }
    }

    fun startExport() {
        val stamp = LocalDate.now().toString()
        if (encrypt) askExportPassword = true
        else exportLauncher.launch("reinminder-$stamp.json")
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            val version = try {
                ctx.packageManager.getPackageInfo(pkg, 0).versionName ?: ""
            } catch (e: Exception) {
                ""
            }
            ScreenTitle(stringResource(R.string.tab_settings), "Reinminder · v$version")
        }

        // General: language and snooze, as plain rows.
        item {
            SectionCard {
                KeyValueRow(stringResource(R.string.language_title), if (en) "English" else "中文", Accent) { editing = "lang" }
                KeyValueRow(stringResource(R.string.snooze), stringResource(R.string.minutes_n, snooze), Accent) { editing = "snooze" }
            }
        }

        // Topics
        item {
            SectionCard {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    CardTitle(stringResource(R.string.topics))
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { managingTopics = true }) { Text(stringResource(R.string.manage_topics)) }
                }
                if (topics.isEmpty()) Text(stringResource(R.string.no_topics), fontSize = 14.sp, color = Text3)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    topics.forEach { t ->
                        val n = events.count { it.topic == t.id && !it.done }
                        Chip("${t.name} · $n")
                    }
                }
            }
        }

        // Backup
        item {
            SectionCard {
                CardTitle(stringResource(R.string.backup_title))
                Text(stringResource(R.string.backup_note), fontSize = 13.sp, color = Text2, lineHeight = 19.sp)
                SwitchRow(
                    stringResource(R.string.encrypt_backup),
                    stringResource(if (encrypt) R.string.encrypt_on_note else R.string.encrypt_off_note),
                    encrypt,
                    onChange = { encrypt = it; prefs.encryptBackup = it },
                    noteColor = if (encrypt) Text3 else Amber,
                )
                val (at, wasEncrypted) = lastExport
                Text(
                    if (at == 0L) stringResource(R.string.never_exported)
                    else stringResource(
                        if (wasEncrypted) R.string.last_export_encrypted else R.string.last_export_plain,
                        Fmt.day(Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()).toLocalDate(), today, en) + " " +
                            Fmt.time(LocalDateTime.ofInstant(Instant.ofEpochMilli(at), ZoneId.systemDefault()).toLocalTime(), en),
                    ),
                    fontSize = 12.sp, color = Text3,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { startExport() }, enabled = !busy, modifier = Modifier.weight(1f).height(44.dp)) {
                        Text(stringResource(R.string.export))
                    }
                    OutlinedButton(onClick = { importLauncher.launch(arrayOf("*/*")) }, enabled = !busy, modifier = Modifier.weight(1f).height(44.dp)) {
                        Text(stringResource(R.string.import_))
                    }
                }
            }
        }
    }

    when (editing) {
        "lang" -> ChoiceDialog(
            stringResource(R.string.language_title),
            listOf("中文" to "zh", "English" to "en"),
            prefs.lang,
            onDismiss = { editing = null },
            onPick = { code ->
                editing = null
                if (prefs.lang != code) {
                    prefs.lang = code
                    Notifier.ensureChannels(ctx)
                    (ctx as? Activity)?.recreate()
                }
            },
        )
        "snooze" -> ChoiceDialog(
            stringResource(R.string.snooze),
            Presets.snooze.map { stringResource(R.string.minutes_n, it) to it },
            snooze,
            onDismiss = { editing = null },
            onPick = { prefs.snoozeMinutes = it; snooze = it; editing = null },
        )
    }

    if (managingTopics) TopicManager(topics, onDismiss = { managingTopics = false })

    if (askExportPassword) {
        PasswordDialog(stringResource(R.string.set_backup_password), twice = true, error = null, onDismiss = { askExportPassword = false }) { pw ->
            askExportPassword = false
            exportPassword = pw
            exportLauncher.launch("reinminder-${LocalDate.now()}.reinminder")
        }
    }

    val toDecrypt = importBytes
    if (toDecrypt != null) {
        PasswordDialog(stringResource(R.string.enter_backup_password), twice = false, error = importError, onDismiss = { importBytes = null }) { pw ->
            loadImport(toDecrypt, pw)
        }
    }

    val incoming = pendingImport
    if (incoming != null) {
        ConfirmDialog(
            title = stringResource(R.string.import_title),
            text = stringResource(R.string.import_text, events.size, incoming.events.size),
            confirm = stringResource(R.string.import_),
            onDismiss = { pendingImport = null },
            onConfirm = {
                pendingImport = null
                Repo.replaceAll(ctx, incoming.events, incoming.topics)
                toast(ctx, msgImported)
            },
        )
    }
}
