package com.multialarm.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val id = i.getIntExtra("id", -1)
        val snooze = i.getBooleanExtra("snooze", false)
        val a = Store.load(c).find { it.id == id } ?: return
        if (!snooze) {
            if (a.days == 0) Repo.update(c, a.copy(enabled = false)) else Scheduler.schedule(c, a)
        }
        ContextCompat.startForegroundService(
            c, Intent(c, AlarmService::class.java).putExtra("id", id)
        )
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        Store.load(c).filter { it.enabled }.forEach { Scheduler.schedule(c, it) }
    }
}
