package com.multialarm.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.util.Calendar

object Scheduler {
    private fun am(c: Context) = c.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    private fun firePi(c: Context, id: Int, snooze: Boolean): PendingIntent {
        val i = Intent(c, AlarmReceiver::class.java).putExtra("id", id).putExtra("snooze", snooze)
        return PendingIntent.getBroadcast(
            c, if (snooze) id + 1_000_000 else id, i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun showPi(c: Context) = PendingIntent.getActivity(
        c, 0, Intent(c, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
    )

    fun nextTrigger(a: Alarm): Long {
        val now = System.currentTimeMillis()
        val c = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, a.hour); set(Calendar.MINUTE, a.minute)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        repeat(8) {
            val idx = (c.get(Calendar.DAY_OF_WEEK) + 5) % 7 // Пн=0 ... Вс=6
            val okDay = a.days == 0 || (a.days and (1 shl idx)) != 0
            if (c.timeInMillis > now && okDay) return c.timeInMillis
            c.add(Calendar.DAY_OF_YEAR, 1)
        }
        return c.timeInMillis
    }

    fun schedule(c: Context, a: Alarm) {
        am(c).setAlarmClock(AlarmManager.AlarmClockInfo(nextTrigger(a), showPi(c)), firePi(c, a.id, false))
    }

    fun cancel(c: Context, id: Int) {
        am(c).cancel(firePi(c, id, false))
        am(c).cancel(firePi(c, id, true))
    }

    fun sync(c: Context, a: Alarm) = if (a.enabled) schedule(c, a) else cancel(c, a.id)

    fun snooze(c: Context, id: Int, minutes: Int) {
        am(c).setAlarmClock(
            AlarmManager.AlarmClockInfo(System.currentTimeMillis() + minutes * 60_000L, showPi(c)),
            firePi(c, id, true)
        )
    }
}
