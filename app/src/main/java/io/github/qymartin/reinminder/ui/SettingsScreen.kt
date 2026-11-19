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
import io.github.qymartin.reinminder.data.Category
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

    // Re-read permission states whenever the screen comes back from system settings.
    var resumeTick by remember { mutableIntStateOf(0) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, ev -> if (ev == Lifecycle.Event.ON_RESUME) resumeTick++ }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs) }
    }
    val notifOk = remember(resumeTick) { Notifier.canNotify(ctx) && NotificationManagerCompat.from(ctx).areNotificationsEnabled() }
    val exactOk = remember(resumeTick) { Alarms.canExact(ctx) }
    val batteryOk = remember(resumeTick) { ctx.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(pkg) }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { resumeTick++ }

    // Settings that live in prefs, mirrored as state so the screen updates.
    var timedDefaults by remember { mutableStateOf(prefs.timedDefaults) }
    var allDayDefaults by remember { mutableStateOf(prefs.allDayDefaults) }
    var annivDefaults by remember { mutableStateOf(prefs.anniversaryDefaults) }
    var snooze by remember { mutableIntStateOf(prefs.snoozeMinutes) }
    var encrypt by remember { mutableStateOf(prefs.encryptBackup) }
    var lastExport by remember { mutableStateOf(prefs.lastExportAt to prefs.lastExportEncrypted) }
    var editing by remember { mutableStateOf<String?>(null) } // which default is being edited

    // Backup flow state.
    var exportPassword by remember { mutableStateOf<CharArray?>(null) }
    var askExportPassword by remember { mutableStateOf(false) }
    var importBytes by remember { mutableStateOf<ByteArray?>(null) }
    var importError by remember { mutableStateOf<String?>(null) }
    var pendingImport by remember { mutableStateOf<List<Event>?>(null) }
    var busy by remember { mutableStateOf(false) }

    val msgExported = stringResource(R.string.exported)
    val msgFailed = stringResource(R.string.backup_failed)
    val msgNotBackup = stringResource(R.string.not_a_backup)
    val msgWrongPw = stringResource(R.string.wrong_password)
    val msgImported = stringResource(R.string.imported)
    val msgNoNotif = stringResource(R.string.notifications_off)

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val pw = exportPassword
        exportPassword = null
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            try {
                val now = System.currentTimeMillis()
                val bytes = withContext(Dispatchers.Default) {
                    val json = Backup.toJson(EventStore.get(ctx).all(), now).toByteArray()
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

        // Language
        item {
            SectionCard {
                CardTitle(stringResource(R.string.language_title))
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    val options = listOf("zh" to "中文", "en" to "English")
                    options.forEachIndexed { i, (code, label) ->
                        SegmentedButton(
                            selected = prefs.lang == code,
                            onClick = {
                                if (prefs.lang != code) {
                                    prefs.lang = code
                                    Notifier.ensureChannels(ctx)
                                    (ctx as? Activity)?.recreate()
                                }
                            },
                            shape = SegmentedButtonDefaults.itemShape(index = i, count = options.size),
                            icon = {},
                        ) { Text(label) }
                    }
                }
                KeyValueRow(
                    stringResource(R.string.dates_look_like),
                    Fmt.date(LocalDate.of(2026, 11, 21), en) + " · " + Fmt.time(java.time.LocalTime.of(15, 0), en),
                    valueColor = Text1,
                )
            }
        }

        // Privacy
        item {
            SectionCard(spacing = 12) {
                CardTitle(stringResource(R.string.privacy_title))
                FactRow(stringResource(R.string.privacy_offline), stringResource(R.string.privacy_offline_note), stringResource(R.string.verified))
                FactRow(stringResource(R.string.privacy_no_cloud), stringResource(R.string.privacy_no_cloud_note), stringResource(R.string.off))
            }
        }

        // Reliability
        item {
            SectionCard {
                CardTitle(stringResource(R.string.reliability_title))
                StatusRow(stringResource(R.string.notifications), notifOk, stringResource(R.string.on), stringResource(R.string.off_needs_fix))
                StatusRow(stringResource(R.string.exact_alarms), exactOk, stringResource(R.string.allowed), stringResource(R.string.not_allowed))
                StatusRow(stringResource(R.string.battery_opt), batteryOk, stringResource(R.string.excluded), stringResource(R.string.not_excluded))
                if (!batteryOk) Text(stringResource(R.string.battery_note), fontSize = 13.sp, color = Text2, lineHeight = 19.sp)
                if (!notifOk) {
                    Button(onClick = {
                        if (Build.VERSION.SDK_INT >= 33 && !Notifier.canNotify(ctx)) {
                            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            open(ctx, Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, pkg))
                        }
                    }, modifier = Modifier.fillMaxWidth().height(44.dp)) { Text(stringResource(R.string.turn_on_notifications)) }
                }
                if (!exactOk && Build.VERSION.SDK_INT >= 31) {
                    Button(onClick = {
                        open(ctx, Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$pkg")))
                    }, modifier = Modifier.fillMaxWidth().height(44.dp)) { Text(stringResource(R.string.allow_exact)) }
                }
                if (!batteryOk) {
                    Button(onClick = {
                        @Suppress("BatteryLife")
                        val direct = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$pkg"))
                        if (!open(ctx, direct)) open(ctx, Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                    }, modifier = Modifier.fillMaxWidth().height(44.dp)) { Text(stringResource(R.string.exclude_battery)) }
                }
                OutlinedButton(onClick = { if (!Notifier.test(ctx)) toast(ctx, msgNoNotif) }, modifier = Modifier.fillMaxWidth().height(44.dp)) {
                    Text(stringResource(R.string.send_test))
                }
            }
        }

        // Default reminders
        item {
            SectionCard {
                CardTitle(stringResource(R.string.defaults_title))
                KeyValueRow(stringResource(R.string.def_timed), Fmt.reminders(timedDefaults, false, en) ?: stringResource(R.string.none), Accent) { editing = "timed" }
                KeyValueRow(stringResource(R.string.def_all_day), Fmt.reminders(allDayDefaults, true, en) ?: stringResource(R.string.none), Accent) { editing = "allDay" }
                KeyValueRow(stringResource(R.string.def_anniversary), Fmt.reminders(annivDefaults, false, en) ?: stringResource(R.string.none), Accent) { editing = "anniv" }
                KeyValueRow(stringResource(R.string.snooze), stringResource(R.string.minutes_n, snooze), Accent) { editing = "snooze" }
            }
        }

        // Categories
        item {
            SectionCard {
                CardTitle(stringResource(R.string.category))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Category.entries.forEach { c ->
                        val n = events.count { it.category == c && !it.done }
                        Chip("${Fmt.category(c, en)} · $n")
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
        "timed" -> ReminderDialog(false, timedDefaults, { editing = null }) { prefs.timedDefaults = it; timedDefaults = it; editing = null }
        "allDay" -> ReminderDialog(true, allDayDefaults, { editing = null }) { prefs.allDayDefaults = it; allDayDefaults = it; editing = null }
        "anniv" -> ReminderDialog(false, annivDefaults, { editing = null }) { prefs.anniversaryDefaults = it; annivDefaults = it; editing = null }
        "snooze" -> ChoiceDialog(
            stringResource(R.string.snooze),
            Presets.snooze.map { stringResource(R.string.minutes_n, it) to it },
            snooze,
            onDismiss = { editing = null },
            onPick = { prefs.snoozeMinutes = it; snooze = it; editing = null },
        )
    }

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
            text = stringResource(R.string.import_text, events.size, incoming.size),
            confirm = stringResource(R.string.import_),
            onDismiss = { pendingImport = null },
            onConfirm = {
                pendingImport = null
                Repo.replaceAll(ctx, incoming)
                toast(ctx, msgImported)
            },
        )
    }
}

@Composable
private fun FactRow(title: String, note: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, fontSize = 15.sp, color = Text1)
            Text(note, fontSize = 12.sp, color = Text3, lineHeight = 17.sp)
        }
        Text("$value ✓", fontSize = 15.sp, color = Text2, modifier = Modifier.padding(start = 12.dp))
    }
}

@Composable
private fun StatusRow(title: String, ok: Boolean, okText: String, badText: String) {
    KeyValueRow(title, if (ok) "$okText ✓" else badText, valueColor = if (ok) Text2 else Amber)
}

private fun open(ctx: Context, intent: Intent): Boolean = try {
    ctx.startActivity(intent)
    true
} catch (e: Exception) {
    false
}
