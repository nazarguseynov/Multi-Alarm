package com.multialarm.app

import android.Manifest
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AlarmTheme { App() } }
    }
}

sealed class Entry(val sort: Int) {
    class Single(val a: Alarm) : Entry(a.hour * 60 + a.minute)
    class Group(val gid: Int, val members: List<Alarm>) :
        Entry((members.firstOrNull { it.center } ?: members.first()).let { it.hour * 60 + it.minute })
}

val DAY_NAMES = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")
fun fmt(h: Int, m: Int) = "%02d:%02d".format(h, m)
fun daysText(d: Int): String = when {
    d == 0 -> "Один раз"
    d == 127 -> "Каждый день"
    else -> (0..6).filter { d and (1 shl it) != 0 }.joinToString(", ") { DAY_NAMES[it] }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App() {
    val ctx = LocalContext.current
    var alarms by remember { mutableStateOf(Store.load(ctx)) }
    var settings by remember { mutableStateOf(Store.settings(ctx)) }
    var editing by remember { mutableStateOf<Alarm?>(null) }
    var creating by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    val expanded = remember { mutableStateListOf<Int>() }
    fun refresh() { alarms = Store.load(ctx) }

    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33) perm.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    val entries: List<Entry> = (
        alarms.filter { it.groupId == 0 }.map { Entry.Single(it) } +
        alarms.filter { it.groupId != 0 }.groupBy { it.groupId }.map { Entry.Group(it.key, it.value) }
    ).sortedBy { it.sort }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Будильник") },
                actions = { IconButton({ showSettings = true }) { Icon(Icons.Default.Settings, "Настройки") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        floatingActionButton = {
            FloatingActionButton({ creating = true }, containerColor = MaterialTheme.colorScheme.primary) {
                Icon(Icons.Default.Add, "Добавить")
            }
        }
    ) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize()) {
            items(entries, key = { if (it is Entry.Group) "g${it.gid}" else "s${(it as Entry.Single).a.id}" }) { e ->
                when (e) {
                    is Entry.Single -> AlarmRow(e.a, false,
                        { on -> Repo.update(ctx, e.a.copy(enabled = on)); refresh() },
                        { editing = e.a })
                    is Entry.Group -> GroupCard(
                        e.members, e.gid in expanded,
                        { if (e.gid in expanded) expanded.remove(e.gid) else expanded.add(e.gid) },
                        { on -> Repo.setGroupEnabled(ctx, e.gid, on); refresh() },
                        { a, on -> Repo.update(ctx, a.copy(enabled = on)); refresh() },
                        { editing = it },
                        { Repo.deleteGroup(ctx, e.gid); refresh() }
                    )
                }
            }
            item { Spacer(Modifier.height(96.dp)) }
        }
    }

    if (creating) EditorDialog(null, settings, { creating = false },
        { h, m, label, days, uri, title, group ->
            Repo.create(ctx, h, m, label, days, uri, title, group, settings)
            creating = false; refresh()
        }, null)

    editing?.let { a ->
        EditorDialog(a, settings, { editing = null },
            { h, m, label, days, uri, title, _ ->
                Repo.update(ctx, a.copy(hour = h, minute = m, label = label, days = days,
                    soundUri = uri, soundTitle = title, enabled = true))
                editing = null; refresh()
            },
            { Repo.delete(ctx, a); editing = null; refresh() })
    }

    if (showSettings) SettingsDialog(settings, { showSettings = false }) {
        settings = it; Store.saveSettings(ctx, it)
    }
}

@Composable
fun AlarmRow(a: Alarm, small: Boolean, onToggle: (Boolean) -> Unit, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(fmt(a.hour, a.minute), fontSize = if (small) 32.sp else 48.sp,
                fontWeight = FontWeight.Light, color = if (a.enabled) cs.onSurface else cs.onSurfaceVariant)
            Text(listOf(a.label, daysText(a.days)).filter { it.isNotBlank() }.joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
        }
        Switch(checked = a.enabled, onCheckedChange = onToggle)
    }
}

@Composable
fun GroupCard(
    members: List<Alarm>, expanded: Boolean, onExpand: () -> Unit, onToggleAll: (Boolean) -> Unit,
    onToggle: (Alarm, Boolean) -> Unit, onEdit: (Alarm) -> Unit, onDelete: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val center = members.firstOrNull { it.center } ?: members.first()
    val on = members.any { it.enabled }
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = cs.surfaceVariant)
    ) {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onExpand).padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(fmt(center.hour, center.minute), fontSize = 48.sp, fontWeight = FontWeight.Light,
                    color = if (on) cs.onSurface else cs.onSurfaceVariant)
                Text("Папка · ${members.size} шт. · ${fmt(members.first().hour, members.first().minute)}–${fmt(members.last().hour, members.last().minute)}",
                    style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
            }
            Switch(checked = on, onCheckedChange = onToggleAll)
        }
        if (expanded) {
            members.forEach { m -> AlarmRow(m, true, { c -> onToggle(m, c) }, { onEdit(m) }) }
            TextButton(onDelete, Modifier.padding(horizontal = 8.dp)) { Text("Удалить папку") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorDialog(
    initial: Alarm?, s: AppSettings, onDismiss: () -> Unit,
    onSave: (Int, Int, String, Int, String, String, Boolean) -> Unit,
    onDelete: (() -> Unit)?
) {
    val now = remember { Calendar.getInstance() }
    val tp = rememberTimePickerState(
        initial?.hour ?: now.get(Calendar.HOUR_OF_DAY), initial?.minute ?: now.get(Calendar.MINUTE), true
    )
    var label by remember { mutableStateOf(initial?.label ?: "") }
    var days by remember { mutableStateOf(initial?.days ?: 0) }
    var uri by remember { mutableStateOf(initial?.soundUri ?: "") }
    var title by remember { mutableStateOf(initial?.soundTitle ?: "По умолчанию") }
    var group by remember { mutableStateOf(initial == null) }
    var pickSound by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 32.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onDismiss) { Text("Отмена") }
                    Button({ onSave(tp.hour, tp.minute, label, days, uri, title, group) }) { Text("Сохранить") }
                }
                Spacer(Modifier.height(16.dp))
                TimePicker(tp, Modifier.align(Alignment.CenterHorizontally))
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    DAY_NAMES.forEachIndexed { i, n ->
                        val sel = days and (1 shl i) != 0
                        Box(
                            Modifier.size(42.dp).clip(CircleShape)
                                .background(if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { days = days xor (1 shl i) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(n, fontSize = 13.sp,
                                color = if (sel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
                Text(daysText(days), Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(label, { label = it }, label = { Text("Название") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                TextButton({ pickSound = true }) { Text("Мелодия: $title") }
                if (initial == null && s.count > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(group, { group = it })
                        Text("Создать папку: ±${s.count} × ${s.intervalMin} мин (${2 * s.count + 1} будильников)")
                    }
                }
                if (onDelete != null) {
                    Spacer(Modifier.height(16.dp))
                    OutlinedButton(onDelete) { Text("Удалить будильник") }
                }
            }
        }
    }
    if (pickSound) SoundDialog({ u, t -> uri = u; title = t; pickSound = false }, { pickSound = false })
}

@Composable
fun SoundDialog(onPick: (String, String) -> Unit, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    val sounds = remember { AudioTools.systemSounds(ctx) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u: Uri? ->
        if (u != null) {
            busy = true
            scope.launch {
                val r = withContext(Dispatchers.IO) { AudioTools.importSound(ctx, u) }
                busy = false
                r.onSuccess { onPick(it.first, it.second) }
                    .onFailure { Toast.makeText(ctx, "Не удалось: ${it.message}", Toast.LENGTH_LONG).show() }
            }
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onDismiss) { Text("Закрыть") } },
        title = { Text("Мелодия") },
        text = {
            Column {
                Button({ launcher.launch(arrayOf("*/*")) }, enabled = !busy) {
                    Text(if (busy) "Извлекаю аудио…" else "Файл с телефона (аудио/видео)")
                }
                Spacer(Modifier.height(8.dp))
                LazyColumn(Modifier.heightIn(max = 320.dp)) {
                    item { Text("По умолчанию", Modifier.fillMaxWidth().clickable { onPick("", "По умолчанию") }.padding(12.dp)) }
                    items(sounds) { (t, u) ->
                        Text(t, Modifier.fillMaxWidth().clickable { onPick(u, t) }.padding(12.dp))
                    }
                }
            }
        }
    )
}

@Composable
fun SettingsDialog(s: AppSettings, onDismiss: () -> Unit, onChange: (AppSettings) -> Unit) {
    var cur by remember { mutableStateOf(s) }
    fun upd(n: AppSettings) { cur = n; onChange(n) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onDismiss) { Text("Готово") } },
        title = { Text("Настройки папок") },
        text = {
            Column {
                Text("Интервал: ${cur.intervalMin} мин")
                Slider(cur.intervalMin.toFloat(), { upd(cur.copy(intervalMin = it.toInt())) }, valueRange = 1f..30f, steps = 28)
                Text("Будильников с каждой стороны: ${cur.count}")
                Slider(cur.count.toFloat(), { upd(cur.copy(count = it.toInt())) }, valueRange = 0f..10f, steps = 9)
                Text("Отложить на: ${cur.snoozeMin} мин")
                Slider(cur.snoozeMin.toFloat(), { upd(cur.copy(snoozeMin = it.toInt())) }, valueRange = 1f..30f, steps = 28)
            }
        }
    )
}
