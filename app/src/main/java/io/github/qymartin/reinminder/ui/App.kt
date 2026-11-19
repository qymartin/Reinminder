package io.github.qymartin.reinminder.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.qymartin.reinminder.R
import io.github.qymartin.reinminder.alarm.Repo
import io.github.qymartin.reinminder.data.Codec
import io.github.qymartin.reinminder.data.Event
import io.github.qymartin.reinminder.data.EventStore
import io.github.qymartin.reinminder.data.Prefs
import kotlinx.coroutines.delay
import org.json.JSONObject
import java.time.LocalDateTime

private const val NONE = -1L

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun App(openRequest: Long?, onOpenHandled: () -> Unit) {
    val ctx = LocalContext.current
    val store = remember { EventStore.get(ctx) }
    val events by store.events.collectAsState()

    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            now = LocalDateTime.now()
        }
    }

    var tab by rememberSaveable { mutableIntStateOf(0) }
    var detailId by rememberSaveable { mutableLongStateOf(NONE) }
    // The event being edited, kept as JSON so it survives rotation and unfolding.
    var editJson by rememberSaveable { mutableStateOf<String?>(null) }
    var editIsNew by rememberSaveable { mutableStateOf(false) }
    var quickAdd by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(openRequest) {
        if (openRequest != null) {
            tab = 0
            detailId = openRequest
            onOpenHandled()
        }
    }

    // Ask for notification permission once, on first launch (Android 13+).
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        val prefs = Prefs(ctx)
        if (Build.VERSION.SDK_INT >= 33 && !prefs.askedNotificationPermission) {
            prefs.askedNotificationPermission = true
            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val wide = LocalConfiguration.current.screenWidthDp >= 600
    val twoPane = wide && tab == 0
    val openDetail: (Long) -> Unit = { detailId = it }
    val startEdit: (Event, Boolean) -> Unit = { e, isNew ->
        editJson = Codec.toJson(e).toString()
        editIsNew = isNew
    }

    CompositionLocalProvider(LocalNow provides now) {
        Box(Modifier.fillMaxSize().background(Bg)) {
            Scaffold(
                containerColor = Bg,
                bottomBar = {
                    NavigationBar(containerColor = NavBg) {
                        val items = listOf(
                            R.string.tab_today to R.drawable.ic_tab_today,
                            R.string.tab_schedule to R.drawable.ic_tab_schedule,
                            R.string.tab_settings to R.drawable.ic_tab_settings,
                        )
                        items.forEachIndexed { i, (label, icon) ->
                            NavigationBarItem(
                                selected = tab == i,
                                onClick = { tab = i },
                                icon = { Icon(painterResource(icon), contentDescription = null) },
                                label = { Text(stringResource(label)) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = OnTonal,
                                    selectedTextColor = Text1,
                                    indicatorColor = SelectedBg,
                                    unselectedIconColor = Text2,
                                    unselectedTextColor = Text2,
                                ),
                            )
                        }
                    }
                },
                floatingActionButton = {
                    if (tab != 2) {
                        FloatingActionButton(
                            onClick = { quickAdd = true },
                            containerColor = Accent,
                            contentColor = OnAccent,
                            shape = RoundedCornerShape(16.dp),
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.new_event))
                        }
                    }
                },
            ) { pad ->
                Box(Modifier.fillMaxSize().padding(pad).consumeWindowInsets(pad)) {
                    when (tab) {
                        0 -> if (twoPane) {
                            Row(Modifier.fillMaxSize()) {
                                TodayScreen(events, onOpen = openDetail, selectedId = detailId, modifier = Modifier.weight(1f))
                                VerticalDivider(color = DividerColor)
                                val selected = events.firstOrNull { it.id == detailId }
                                Box(Modifier.weight(1f).fillMaxHeight()) {
                                    if (selected != null) {
                                        DetailScreen(selected, embedded = true, onBack = { detailId = NONE }, onEdit = { startEdit(selected, false) })
                                    } else {
                                        EmptyPane()
                                    }
                                }
                            }
                        } else {
                            TodayScreen(events, onOpen = openDetail, selectedId = NONE)
                        }
                        1 -> ScheduleScreen(events, onOpen = openDetail)
                        else -> SettingsScreen(events)
                    }
                }
            }

            // Full-screen detail (phone, or any tab other than Today on the unfolded screen).
            val detail = events.firstOrNull { it.id == detailId }
            if (detail != null && !twoPane && editJson == null) {
                BackHandler { detailId = NONE }
                Box(Modifier.fillMaxSize().background(Bg).safeDrawingPadding()) {
                    DetailScreen(detail, embedded = false, onBack = { detailId = NONE }, onEdit = { startEdit(detail, false) })
                }
            }

            val editing = editJson?.let { Codec.fromJson(JSONObject(it)) }
            if (editing != null) {
                BackHandler { editJson = null }
                Box(Modifier.fillMaxSize().background(Bg).safeDrawingPadding()) {
                    EditScreen(
                        initial = editing,
                        isNew = editIsNew,
                        onClose = { editJson = null },
                        onDeleted = { editJson = null; detailId = NONE },
                    )
                }
            }
        }

        if (quickAdd) {
            QuickAddSheet(
                onDismiss = { quickAdd = false },
                onSave = { e ->
                    Repo.save(ctx, e)
                    quickAdd = false
                },
                onMore = { e ->
                    quickAdd = false
                    startEdit(e, true)
                },
            )
        }
    }
}

@Composable
private fun EmptyPane() {
    Box(Modifier.fillMaxSize().padding(24.dp)) {
        Text(stringResource(R.string.pick_event), color = Text3)
    }
}
