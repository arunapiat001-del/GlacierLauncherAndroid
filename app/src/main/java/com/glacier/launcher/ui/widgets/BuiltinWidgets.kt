package com.glacier.launcher.ui.widgets

import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.text.format.DateFormat
import android.text.format.Formatter
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.glacier.launcher.LauncherViewModel
import com.glacier.launcher.data.WeatherRepository
import com.glacier.launcher.model.BuiltinWidget
import com.glacier.launcher.model.LauncherSettings
import com.glacier.launcher.ui.home.LocalEnv
import com.glacier.launcher.ui.theme.LocalGlass
import com.glacier.launcher.ui.theme.glass
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.WeekFields
import java.util.Locale

/** Ticks once a minute (aligned to the minute boundary). Not a battery-hungry animation loop. */
@Composable
private fun rememberMinuteTick(): State<LocalDateTime> {
    val now = remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now.value = LocalDateTime.now()
            delay(60_000L - (System.currentTimeMillis() % 60_000L) + 50L)
        }
    }
    return now
}

@Composable
fun BuiltinWidgetView(type: BuiltinWidget, spanX: Int, spanY: Int, vm: LauncherViewModel, s: LauncherSettings, modifier: Modifier = Modifier) {
    val compact = spanX <= 2 && spanY <= 2
    val tall = spanY >= 3
    Box(modifier.fillMaxSize().glass().padding(14.dp)) {
        when (type) {
            BuiltinWidget.CLOCK -> ClockWidget(compact)
            BuiltinWidget.CALENDAR -> CalendarWidget(compact)
            BuiltinWidget.WEATHER -> WeatherWidget(vm, s, compact)
            BuiltinWidget.BATTERY -> BatteryWidget(compact)
            BuiltinWidget.NOTES -> NotesWidget(vm, s)
            BuiltinWidget.MUSIC -> MusicWidget(vm, compact)
            BuiltinWidget.PHOTO -> PhotoWidget(s)
            BuiltinWidget.DEVICE -> DeviceWidget(tall)
        }
    }
}

@Composable
private fun ClockWidget(compact: Boolean) {
    val g = LocalGlass.current
    val ctx = LocalContext.current
    val now by rememberMinuteTick()
    val is24 = DateFormat.is24HourFormat(ctx)
    val time = now.format(DateTimeFormatter.ofPattern(if (is24) "HH:mm" else "h:mm", Locale.getDefault()))
    val date = now.format(DateTimeFormatter.ofPattern(if (compact) "EEE d" else "EEEE, d MMMM", Locale.getDefault()))
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
        Text(time, color = g.onGlass, fontSize = if (compact) 34.sp else 52.sp, fontWeight = FontWeight.Light, maxLines = 1)
        Text(date, color = g.onGlassDim, fontSize = if (compact) 13.sp else 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun CalendarWidget(compact: Boolean) {
    val g = LocalGlass.current
    val now by rememberMinuteTick()
    val today = now.toLocalDate()
    if (compact) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
            Text(today.format(DateTimeFormatter.ofPattern("EEEE", Locale.getDefault())).uppercase(), color = g.accent, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(today.dayOfMonth.toString(), color = g.onGlass, fontSize = 44.sp, fontWeight = FontWeight.Light)
            Text(today.format(DateTimeFormatter.ofPattern("MMMM", Locale.getDefault())), color = g.onGlassDim, fontSize = 13.sp, maxLines = 1)
        }
        return
    }
    val wf = WeekFields.of(Locale.getDefault())
    val first = wf.firstDayOfWeek
    val ym = today.withDayOfMonth(1)
    val offset = ((ym.dayOfWeek.value - first.value) + 7) % 7
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceEvenly) {
        Text(today.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())), color = g.onGlass, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        Row(Modifier.fillMaxWidth()) {
            for (i in 0 until 7) {
                val d = first.plus(i.toLong())
                Text(
                    d.getDisplayName(java.time.format.TextStyle.NARROW, Locale.getDefault()),
                    Modifier.weight(1f), color = g.onGlassDim, fontSize = 11.sp, textAlign = TextAlign.Center
                )
            }
        }
        for (week in 0 until 6) {
            Row(Modifier.fillMaxWidth()) {
                for (i in 0 until 7) {
                    val day = week * 7 + i - offset + 1
                    val valid = day in 1..ym.lengthOfMonth()
                    val isToday = valid && day == today.dayOfMonth
                    Box(Modifier.weight(1f).height(22.dp), contentAlignment = Alignment.Center) {
                        if (isToday) Box(Modifier.size(22.dp).clip(RoundedCornerShape(11.dp)).background(g.accent))
                        if (valid) Text(day.toString(), fontSize = 11.sp, color = if (isToday) Color(0xFF0B1B33) else g.onGlass)
                    }
                }
            }
        }
    }
}

@Composable
private fun WeatherWidget(vm: LauncherViewModel, s: LauncherSettings, compact: Boolean) {
    val g = LocalGlass.current
    val env = LocalEnv.current
    val state by vm.weatherRepo.state.collectAsState()
    LaunchedEffect(s.weatherCity) { vm.refreshWeather(false) }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
        val data: WeatherRepository.Data? = when (val st = state) {
            is WeatherRepository.State.Ready -> st.data
            is WeatherRepository.State.Error -> st.cached
            else -> null
        }
        when {
            s.weatherCity.isBlank() -> {
                Text("Weather", color = g.onGlass, fontWeight = FontWeight.SemiBold)
                Text("Choose a city in Settings › Widgets", color = g.onGlassDim, fontSize = 12.sp)
            }
            data != null -> {
                Text(data.city, color = g.onGlassDim, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${data.tempC.toInt()}°", color = g.onGlass, fontSize = if (compact) 36.sp else 52.sp, fontWeight = FontWeight.Light)
                Text(WeatherRepository.describe(data.code), color = g.onGlass, fontSize = 14.sp)
                Text("H ${data.hi.toInt()}°  L ${data.lo.toInt()}°", color = g.onGlassDim, fontSize = 12.sp)
                if (state is WeatherRepository.State.Error) Text("Offline · last updated " + java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(data.updated), color = g.onGlassDim, fontSize = 10.sp)
            }
            state is WeatherRepository.State.Loading -> Text("Loading weather…", color = g.onGlassDim, fontSize = 13.sp)
            state is WeatherRepository.State.Error -> {
                Text("Weather unavailable", color = g.onGlass, fontWeight = FontWeight.SemiBold)
                Text((state as WeatherRepository.State.Error).message, color = g.onGlassDim, fontSize = 11.sp, maxLines = 2)
                TextButton(onClick = { vm.refreshWeather(true) }, contentPadding = PaddingValues(0.dp)) { Text("Retry") }
            }
            else -> Unit
        }
    }
}

@Composable
private fun BatteryWidget(compact: Boolean) {
    val g = LocalGlass.current
    val ctx = LocalContext.current
    var level by remember { mutableIntStateOf(-1) }
    var charging by remember { mutableStateOf(false) }
    DisposableEffect(Unit) {
        fun read(i: Intent?) {
            if (i == null) return
            val l = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val sc = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
            if (l >= 0 && sc > 0) level = l * 100 / sc
            val st = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            charging = st == BatteryManager.BATTERY_STATUS_CHARGING || st == BatteryManager.BATTERY_STATUS_FULL
        }
        val r = object : BroadcastReceiver() { override fun onReceive(c: Context?, i: Intent?) = read(i) }
        // ACTION_BATTERY_CHANGED is sticky: the first value is returned immediately.
        read(androidx.core.content.ContextCompat.registerReceiver(ctx, r, IntentFilter(Intent.ACTION_BATTERY_CHANGED), androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED))
        onDispose { runCatching { ctx.unregisterReceiver(r) } }
    }
    Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        val frac = if (level < 0) 0f else level / 100f
        val accent = g.accent
        val track = g.onGlass.copy(alpha = 0.15f)
        Box(Modifier.size(if (compact) 56.dp else 72.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val sw = 7.dp.toPx()
                val inset = sw / 2
                val sz = Size(size.width - sw, size.height - sw)
                drawArc(track, 0f, 360f, false, Offset(inset, inset), sz, style = Stroke(sw, cap = StrokeCap.Round))
                drawArc(accent, -90f, 360f * frac, false, Offset(inset, inset), sz, style = Stroke(sw, cap = StrokeCap.Round))
            }
            Text(if (level < 0) "–" else "$level%", color = g.onGlass, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
        if (!compact) Column {
            Text("Battery", color = g.onGlass, fontWeight = FontWeight.SemiBold)
            Text(if (charging) "Charging" else "On battery", color = g.onGlassDim, fontSize = 13.sp)
        }
    }
}

@Composable
private fun NotesWidget(vm: LauncherViewModel, s: LauncherSettings) {
    val g = LocalGlass.current
    var text by remember { mutableStateOf(s.notes) }
    LaunchedEffect(text) {
        delay(600)
        if (text != s.notes) vm.setNotes(text)
    }
    Column(Modifier.fillMaxSize()) {
        Text("Notes", color = g.accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        BasicTextField(
            value = text, onValueChange = { text = it },
            textStyle = TextStyle(color = g.onGlass, fontSize = 14.sp),
            cursorBrush = SolidColor(g.accent),
            modifier = Modifier.fillMaxSize(),
            decorationBox = { inner ->
                Box { if (text.isEmpty()) Text("Tap to write…", color = g.onGlassDim, fontSize = 14.sp); inner() }
            }
        )
    }
}

@Composable
private fun MusicWidget(vm: LauncherViewModel, compact: Boolean) {
    val g = LocalGlass.current
    val env = LocalEnv.current
    val access by vm.media.hasAccess.collectAsState()
    val info by vm.media.info.collectAsState()
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
        val i = info
        when {
            !access -> {
                Text("Music", color = g.onGlass, fontWeight = FontWeight.SemiBold)
                Text("Needs Notification Access to see what's playing. GlacierLauncher never reads your notifications.", color = g.onGlassDim, fontSize = 11.sp, maxLines = if (compact) 3 else 4, overflow = TextOverflow.Ellipsis)
                TextButton(onClick = env.openNotificationAccess, contentPadding = PaddingValues(0.dp)) { Text("Grant access") }
            }
            i == null -> {
                Text("Music", color = g.onGlass, fontWeight = FontWeight.SemiBold)
                Text("Nothing playing", color = g.onGlassDim, fontSize = 13.sp)
            }
            else -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                i.art?.let {
                    Image(it.asImageBitmap(), null, Modifier.size(if (compact) 48.dp else 64.dp).clip(RoundedCornerShape(10.dp)), contentScale = ContentScale.Crop)
                }
                Column(Modifier.weight(1f)) {
                    Text(i.title, color = g.onGlass, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(i.artist, color = g.onGlassDim, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        MediaBtn("⏮") { vm.media.previous() }
                        MediaBtn(if (i.playing) "⏸" else "▶") { vm.media.playPause() }
                        MediaBtn("⏭") { vm.media.next() }
                    }
                }
            }
        }
    }
}

@Composable
private fun MediaBtn(label: String, onClick: () -> Unit) {
    val g = LocalGlass.current
    Text(label, color = g.onGlass, fontSize = 20.sp, modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 4.dp))
}

@Composable
private fun PhotoWidget(s: LauncherSettings) {
    val g = LocalGlass.current
    val env = LocalEnv.current
    val ctx = LocalContext.current
    val bmp by produceState<android.graphics.Bitmap?>(null, s.photoUri) {
        value = if (s.photoUri.isBlank()) null else withContext(Dispatchers.IO) { decodeSampled(ctx, Uri.parse(s.photoUri), 900) }
    }
    Box(Modifier.fillMaxSize().clickable { env.pickPhoto() }, contentAlignment = Alignment.Center) {
        val b = bmp
        if (b != null) Image(b.asImageBitmap(), null, Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp)), contentScale = ContentScale.Crop)
        else Text(if (s.photoUri.isBlank()) "Tap to choose a photo" else "Photo unavailable. Tap to choose another", color = g.onGlassDim, textAlign = TextAlign.Center, fontSize = 13.sp)
    }
}

private fun decodeSampled(ctx: Context, uri: Uri, target: Int): android.graphics.Bitmap? = try {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= target && bounds.outHeight / (sample * 2) >= target) sample *= 2
    ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
} catch (e: Exception) { null }

@Composable
private fun DeviceWidget(tall: Boolean) {
    val g = LocalGlass.current
    val ctx = LocalContext.current
    val rows = remember {
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        val st = StatFs(Environment.getDataDirectory().path)
        val dm = ctx.resources.displayMetrics
        listOf(
            "Model" to "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}",
            "Android" to "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            "Memory" to "${Formatter.formatShortFileSize(ctx, mi.availMem)} free of ${Formatter.formatShortFileSize(ctx, mi.totalMem)}",
            "Storage" to "${Formatter.formatShortFileSize(ctx, st.availableBytes)} free of ${Formatter.formatShortFileSize(ctx, st.totalBytes)}",
            "Display" to "${dm.widthPixels}×${dm.heightPixels} @ ${dm.densityDpi}dpi"
        )
    }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceEvenly) {
        Text("Device", color = g.accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        rows.take(if (tall) rows.size else 3).forEach { (k, v) ->
            Column {
                Text(k, color = g.onGlassDim, fontSize = 10.sp)
                Text(v, color = g.onGlass, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
