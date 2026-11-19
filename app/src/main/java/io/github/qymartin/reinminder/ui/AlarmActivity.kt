package io.github.qymartin.reinminder.ui

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.qymartin.reinminder.R
import io.github.qymartin.reinminder.alarm.Alarms
import io.github.qymartin.reinminder.alarm.Notifier
import io.github.qymartin.reinminder.alarm.Repo
import io.github.qymartin.reinminder.data.EventStore
import io.github.qymartin.reinminder.data.Lang
import io.github.qymartin.reinminder.data.Prefs
import io.github.qymartin.reinminder.logic.Fmt
import java.time.LocalDate

/** Full-screen page for alarm-style reminders. Any button stops the ringing. */
class AlarmActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(Lang.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = intent.getLongExtra(Alarms.EXTRA_ID, -1)
        val e = EventStore.get(this).get(id)
        if (e == null) {
            finish()
            return
        }
        val en = Lang.isEnglish(this)
        val snooze = Prefs(this).snoozeMinutes
        setContent {
            CompositionLocalProvider(LocalEn provides en) {
                AppTheme {
                    Column(
                        Modifier.fillMaxSize().background(Bg).safeDrawingPadding().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(painterResource(R.drawable.ic_bell), contentDescription = null, tint = Accent, modifier = Modifier.size(56.dp))
                        Spacer(Modifier.height(24.dp))
                        Text(e.title, fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Text1, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(8.dp))
                        Text(Fmt.moment(e, LocalDate.now(), en), fontSize = 17.sp, color = Text2, textAlign = TextAlign.Center)
                        if (e.notes.isNotBlank()) {
                            Spacer(Modifier.height(12.dp))
                            Text(e.notes, fontSize = 15.sp, color = Text3, textAlign = TextAlign.Center)
                        }
                        Spacer(Modifier.height(40.dp))
                        Button(onClick = {
                            Repo.complete(this@AlarmActivity, id)
                            finish()
                        }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                            Text(stringResource(R.string.mark_done), fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(12.dp))
                        OutlinedButton(onClick = {
                            Notifier.cancel(this@AlarmActivity, id)
                            Alarms.snooze(this@AlarmActivity, id, snooze)
                            finish()
                        }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                            Text(stringResource(R.string.action_snooze, snooze))
                        }
                        Spacer(Modifier.height(4.dp))
                        TextButton(onClick = {
                            Notifier.cancel(this@AlarmActivity, id)
                            finish()
                        }) { Text(stringResource(R.string.stop_ringing)) }
                    }
                }
            }
        }
    }
}
