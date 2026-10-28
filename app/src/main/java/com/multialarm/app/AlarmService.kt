package com.multialarm.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.app.NotificationCompat

class AlarmService : Service() {
    companion object {
        const val ACTION_STOP = "STOP"
        const val ACTION_SNOOZE = "SNOOZE"
        const val CHANNEL = "alarm"
        const val NOTIF_ID = 42
    }

    private var player: MediaPlayer? = null
    private val handler = Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val id = intent?.getIntExtra("id", -1) ?: -1
        when (intent?.action) {
            ACTION_STOP -> { shutdown(); return START_NOT_STICKY }
            ACTION_SNOOZE -> {
                Scheduler.snooze(this, id, Store.settings(this).snoozeMin)
                shutdown(); return START_NOT_STICKY
            }
        }
        val a = Store.load(this).find { it.id == id }
        goForeground(id, a)
        if (a == null) { shutdown(); return START_NOT_STICKY }
        play(a)
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({ shutdown() }, 5 * 60_000L) // автостоп через 5 минут
        return START_NOT_STICKY
    }

    private fun goForeground(id: Int, a: Alarm?) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Будильник", NotificationManager.IMPORTANCE_HIGH).apply { setSound(null, null) }
        )
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val full = PendingIntent.getActivity(
            this, 0,
            Intent(this, RingActivity::class.java).putExtra("id", id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            flags
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, AlarmService::class.java).setAction(ACTION_STOP).putExtra("id", id), flags
        )
        val snooze = PendingIntent.getService(
            this, 2, Intent(this, AlarmService::class.java).setAction(ACTION_SNOOZE).putExtra("id", id), flags
        )
        val title = if (a == null || a.label.isBlank()) "Будильник" else a.label
        val text = if (a == null) "" else "%02d:%02d".format(a.hour, a.minute)
        val n = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title).setContentText(text)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setFullScreenIntent(full, true)
            .setContentIntent(full)
            .setOngoing(true)
            .addAction(0, "Отложить", snooze)
            .addAction(0, "Выключить", stop)
            .build()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else startForeground(NOTIF_ID, n)
    }

    private fun play(a: Alarm) {
        player?.release(); player = null
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
        fun start(uri: Uri) = MediaPlayer().apply {
            setAudioAttributes(attrs)
            setDataSource(this@AlarmService, uri)
            isLooping = true
            prepare(); start()
        }
        player = try {
            start(if (a.soundUri.isNotEmpty()) Uri.parse(a.soundUri)
                  else RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))
        } catch (e: Exception) {
            try { start(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)) } catch (e2: Exception) { null }
        }
        val vib = getSystemService(Vibrator::class.java)
        vib?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 600, 400), 0))
    }

    private fun shutdown() {
        handler.removeCallbacksAndMessages(null)
        player?.release(); player = null
        getSystemService(Vibrator::class.java)?.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        player?.release(); player = null
        super.onDestroy()
    }
}
