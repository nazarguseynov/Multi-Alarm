package com.multialarm.app

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.RingtoneManager
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.nio.ByteBuffer

object AudioTools {
    fun systemSounds(ctx: Context): List<Pair<String, String>> {
        val out = mutableListOf<Pair<String, String>>()
        try {
            val rm = RingtoneManager(ctx)
            rm.setType(RingtoneManager.TYPE_ALL)
            val c = rm.cursor
            while (c.moveToNext()) {
                out += c.getString(RingtoneManager.TITLE_COLUMN_INDEX) to rm.getRingtoneUri(c.position).toString()
            }
        } catch (_: Exception) {}
        return out
    }

    private fun displayName(ctx: Context, uri: Uri): String {
        ctx.contentResolver.query(uri, null, null, null, null)?.use {
            val i = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (i >= 0 && it.moveToFirst()) return it.getString(i)
        }
        return "sound"
    }

    /** Принимает любой файл. Аудио копирует, из видео вытаскивает аудиодорожку в .m4a.
     *  Возвращает (uri сохранённого файла, название). */
    fun importSound(ctx: Context, uri: Uri): Result<Pair<String, String>> = runCatching {
        val dir = File(ctx.filesDir, "sounds").apply { mkdirs() }
        val name = displayName(ctx, uri)
        val title = name.substringBeforeLast('.')
        val mime = ctx.contentResolver.getType(uri) ?: ""
        val stamp = System.currentTimeMillis()
        if (mime.startsWith("audio/")) {
            val ext = name.substringAfterLast('.', "mp3")
            val out = File(dir, "$stamp.$ext")
            ctx.contentResolver.openInputStream(uri)!!.use { i -> out.outputStream().use { o -> i.copyTo(o) } }
            Uri.fromFile(out).toString() to title
        } else {
            val out = File(dir, "$stamp.m4a")
            extractAudio(ctx, uri, out)
            Uri.fromFile(out).toString() to title
        }
    }

    private fun extractAudio(ctx: Context, uri: Uri, out: File) {
        val ex = MediaExtractor()
        try {
            ex.setDataSource(ctx, uri, null)
            var idx = -1
            for (i in 0 until ex.trackCount) {
                val m = ex.getTrackFormat(i).getString(MediaFormat.KEY_MIME) ?: ""
                if (m.startsWith("audio/")) { idx = i; break }
            }
            require(idx >= 0) { "в файле нет аудиодорожки" }
            ex.selectTrack(idx)
            val mux = MediaMuxer(out.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            try {
                val t = mux.addTrack(ex.getTrackFormat(idx))
                mux.start()
                val buf = ByteBuffer.allocate(1 shl 20)
                val info = MediaCodec.BufferInfo()
                while (true) {
                    val n = ex.readSampleData(buf, 0)
                    if (n < 0) break
                    info.set(0, n, ex.sampleTime, ex.sampleFlags)
                    mux.writeSampleData(t, buf, info)
                    ex.advance()
                }
                mux.stop()
            } finally { mux.release() }
        } finally { ex.release() }
    }
}
