package com.multialarm.app

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class RingActivity : ComponentActivity() {
    private var alarm by mutableStateOf<Alarm?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        load(intent)
        setContent {
            AlarmTheme {
                Surface(Modifier.fillMaxSize()) {
                    Column(
                        Modifier.fillMaxSize().padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        val a = alarm
                        Text(if (a == null) "" else "%02d:%02d".format(a.hour, a.minute),
                            fontSize = 84.sp, fontWeight = FontWeight.Light)
                        Text(a?.label ?: "", style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.height(64.dp))
                        Button(onClick = { send(AlarmService.ACTION_STOP) }, Modifier.fillMaxWidth().height(64.dp)) {
                            Text("Выключить", fontSize = 20.sp)
                        }
                        Spacer(Modifier.height(16.dp))
                        FilledTonalButton(onClick = { send(AlarmService.ACTION_SNOOZE) }, Modifier.fillMaxWidth().height(64.dp)) {
                            Text("Отложить", fontSize = 20.sp)
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent); load(intent)
    }

    private fun load(i: Intent) {
        val id = i.getIntExtra("id", -1)
        alarm = Store.load(this).find { it.id == id }
    }

    private fun send(action: String) {
        startService(Intent(this, AlarmService::class.java).setAction(action).putExtra("id", alarm?.id ?: -1))
        finish()
    }
}
