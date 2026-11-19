package io.github.qymartin.reinminder.ui

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import io.github.qymartin.reinminder.alarm.Alarms
import io.github.qymartin.reinminder.alarm.Notifier
import io.github.qymartin.reinminder.data.Lang

class MainActivity : ComponentActivity() {
    private val openRequest = mutableStateOf<Long?>(null)

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(Lang.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        Notifier.ensureChannels(this)
        Alarms.rescheduleAll(this)
        if (savedInstanceState == null) openRequest.value = openId(intent)
        val en = Lang.isEnglish(this)
        setContent {
            CompositionLocalProvider(LocalEn provides en) {
                AppTheme {
                    App(openRequest = openRequest.value, onOpenHandled = { openRequest.value = null })
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        openId(intent)?.let { openRequest.value = it }
    }

    private fun openId(intent: Intent?): Long? =
        intent?.getLongExtra(Notifier.EXTRA_OPEN_ID, -1L)?.takeIf { it > 0 }
}
