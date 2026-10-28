package com.multialarm.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class Alarm(
    val id: Int,
    val hour: Int,
    val minute: Int,
    val label: String = "",
    val enabled: Boolean = true,
    val days: Int = 0,            // битовая маска: Пн=1 ... Вс=64, 0 = один раз
    val soundUri: String = "",
    val soundTitle: String = "По умолчанию",
    val groupId: Int = 0,         // 0 = без папки
    val center: Boolean = false   // центральный будильник папки
)

data class AppSettings(val intervalMin: Int = 5, val count: Int = 3, val snoozeMin: Int = 10)

object Store {
    private fun p(c: Context) = c.getSharedPreferences("multialarm", Context.MODE_PRIVATE)

    fun load(c: Context): List<Alarm> {
        val arr = JSONArray(p(c).getString("alarms", "[]") ?: "[]")
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Alarm(
                o.getInt("id"), o.getInt("h"), o.getInt("m"), o.optString("label"),
                o.optBoolean("on", true), o.optInt("days"), o.optString("uri"),
                o.optString("sound", "По умолчанию"), o.optInt("gid"), o.optBoolean("center")
            )
        }
    }

    fun save(c: Context, list: List<Alarm>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().put("id", it.id).put("h", it.hour).put("m", it.minute)
                .put("label", it.label).put("on", it.enabled).put("days", it.days)
                .put("uri", it.soundUri).put("sound", it.soundTitle)
                .put("gid", it.groupId).put("center", it.center))
        }
        p(c).edit().putString("alarms", arr.toString()).apply()
    }

    fun nextId(c: Context): Int {
        val n = p(c).getInt("next", 1)
        p(c).edit().putInt("next", n + 1).apply()
        return n
    }

    fun settings(c: Context) = AppSettings(
        p(c).getInt("interval", 5), p(c).getInt("count", 3), p(c).getInt("snooze", 10)
    )

    fun saveSettings(c: Context, s: AppSettings) {
        p(c).edit().putInt("interval", s.intervalMin).putInt("count", s.count)
            .putInt("snooze", s.snoozeMin).apply()
    }
}

object Repo {
    fun create(
        c: Context, hour: Int, minute: Int, label: String, days: Int,
        uri: String, title: String, group: Boolean, s: AppSettings
    ) {
        val list = Store.load(c).toMutableList()
        val created = mutableListOf<Alarm>()
        if (group) {
            val gid = Store.nextId(c)
            val base = hour * 60 + minute
            for (k in -s.count..s.count) {
                val m = ((base + k * s.intervalMin) % 1440 + 1440) % 1440
                created += Alarm(Store.nextId(c), m / 60, m % 60, label, true, days, uri, title, gid, k == 0)
            }
        } else {
            created += Alarm(Store.nextId(c), hour, minute, label, true, days, uri, title)
        }
        list += created
        Store.save(c, list)
        created.forEach { Scheduler.sync(c, it) }
    }

    fun update(c: Context, a: Alarm) {
        Store.save(c, Store.load(c).map { if (it.id == a.id) a else it })
        Scheduler.sync(c, a)
    }

    fun setGroupEnabled(c: Context, gid: Int, on: Boolean) {
        val list = Store.load(c).map { if (it.groupId == gid) it.copy(enabled = on) else it }
        Store.save(c, list)
        list.filter { it.groupId == gid }.forEach { Scheduler.sync(c, it) }
    }

    fun delete(c: Context, a: Alarm) {
        Scheduler.cancel(c, a.id)
        Store.save(c, Store.load(c).filter { it.id != a.id })
    }

    fun deleteGroup(c: Context, gid: Int) {
        val all = Store.load(c)
        all.filter { it.groupId == gid }.forEach { Scheduler.cancel(c, it.id) }
        Store.save(c, all.filter { it.groupId != gid })
    }
}
