package com.glacier.launcher.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.border
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.glacier.launcher.LauncherViewModel
import com.glacier.launcher.data.K
import com.glacier.launcher.model.*
import com.glacier.launcher.ui.home.LauncherEnv
import com.glacier.launcher.ui.theme.AccentNames
import com.glacier.launcher.ui.theme.AccentPalette

@Composable
fun SettingsScreen(
    vm: LauncherViewModel, s: LauncherSettings, items: List<HomeItem>, allApps: List<AppEntry>,
    env: LauncherEnv, onClose: () -> Unit, onAddWidget: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    var confirmReset by remember { mutableStateOf(false) }
    var usageDialog by remember { mutableStateOf(false) }
    var city by remember(s.weatherCity) { mutableStateOf(s.weatherCity) }
    val mediaAccess by vm.media.hasAccess.collectAsState()
    val gestureOptions = GestureAction.entries.map { it.label to it }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = cs.onBackground) }
            Text("Settings", color = cs.onBackground, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        }
        LazyColumn(Modifier.fillMaxSize().imePadding(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 40.dp)) {
            // ---------------- A. Personalization ----------------
            item {
                Section("Personalization") {
                    ButtonRow("Wallpaper", "Opens the system wallpaper picker", "Change") { env.openWallpaper() }
                    ChoiceRow("Appearance", listOf("Follow system" to ThemeMode.SYSTEM, "Light" to ThemeMode.LIGHT, "Dark" to ThemeMode.DARK), s.themeMode) { vm.set(K.THEME, it.name) }
                    Column {
                        Text("Accent color", color = cs.onSurface)
                        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            AccentPalette.forEachIndexed { i, c ->
                                Box(
                                    Modifier.size(34.dp).clip(CircleShape).background(c)
                                        .border(if (i == s.accent) 3.dp else 0.dp, cs.onSurface, CircleShape)
                                        .clickable { vm.set(K.ACCENT, i) }
                                )
                            }
                        }
                        Text(AccentNames[s.accent.coerceIn(0, AccentNames.lastIndex)], color = cs.onSurfaceVariant, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                    }
                    SliderRow("Glass intensity", s.glass, 0.1f..1f, label = { "${(it * 100).toInt()}%" }) { vm.set(K.GLASS, it) }
                    SliderRow("Panel corner radius", s.corner.toFloat(), 8f..40f, label = { "${it.toInt()} dp" }) { vm.set(K.CORNER, it.toInt()) }
                    ChoiceRow("Icon style", listOf("Rounded" to 0, "Circle" to 1, "Original" to 2), s.iconStyle) { vm.set(K.ICON_STYLE, it) }
                    SliderRow("Icon size", s.iconSize.toFloat(), 40f..72f, label = { "${it.toInt()} dp" }) { vm.set(K.ICON_SIZE, it.toInt()) }
                }
            }
            // ---------------- B. Home Screen ----------------
            item {
                Section("Home Screen") {
                    SliderRow("Columns", s.columns.toFloat(), 3f..6f, 2, { "${it.toInt()}" }) { vm.set(K.COLUMNS, it.toInt()) }
                    SliderRow("Rows", s.rows.toFloat(), 4f..7f, 2, { "${it.toInt()}" }) { vm.set(K.ROWS, it.toInt()) }
                    SliderRow("Home pages", s.pages.toFloat(), 1f..6f, 4, { "${it.toInt()}" }) { vm.set(K.PAGES, it.toInt()) }
                    SliderRow("Dock icons", s.dockCount.toFloat(), 3f..5f, 1, { "${it.toInt()}" }) { vm.set(K.DOCK, it.toInt()) }
                    SliderRow("Grid side padding", s.gridPadding.toFloat(), 0f..24f, label = { "${it.toInt()} dp" }) { vm.set(K.GRID_PAD, it.toInt()) }
                    SwitchRow("Show app labels", checked = s.showLabels) { vm.set(K.LABELS, it) }
                    Text("Shrinking the grid re-flows items to free cells. Anything that no longer fits is removed from Home but stays in the App Library.", color = cs.onSurfaceVariant, fontSize = 12.sp)
                }
            }
            // ---------------- C. Widgets ----------------
            item {
                Section("Widgets") {
                    ButtonRow("Add a widget", "GlacierLauncher widgets and widgets from other apps", "Add") { onAddWidget() }
                    Text("Weather source: Open-Meteo (open-meteo.com). Only the city name is sent.", color = cs.onSurfaceVariant, fontSize = 12.sp)
                    OutlinedTextField(city, { city = it }, label = { Text("Weather city") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    ButtonRow("Save city", null, "Save") { vm.setCity(city) }
                    ButtonRow("Photo widget", "Pick the photo to show", "Choose") { env.pickPhoto() }
                    ButtonRow(
                        "Music widget access",
                        if (mediaAccess) "Notification Access is on. Only media session info is read." else "Needed to see what's playing. GlacierLauncher never reads notification content.",
                        if (mediaAccess) "Manage" else "Grant"
                    ) { env.openNotificationAccess() }
                }
            }
            item {
                val placed = items.filter { it.kind == ItemKind.WIDGET }
                Section("Placed widgets") {
                    if (placed.isEmpty()) Text("No widgets on Home yet.", color = cs.onSurfaceVariant)
                    placed.forEach { w ->
                        val name = w.builtin?.let { n -> runCatching { BuiltinWidget.valueOf(n).title }.getOrNull() }
                            ?: env.widgets.info(w.hostWidgetId)?.provider?.packageName ?: "Widget (unavailable)"
                        Column {
                            Text("$name  ·  page ${w.page + 1}  ·  ${w.spanX}×${w.spanY}", color = cs.onSurface)
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                listOf("S", "M", "L").forEachIndexed { i, l -> TextButton(onClick = { vm.resizeWidget(w.id, i) }) { Text(l) } }
                                TextButton(onClick = {
                                    if (w.hostWidgetId >= 0) env.widgets.delete(w.hostWidgetId)
                                    vm.removeItem(w.id)
                                }) { Text("Remove") }
                            }
                        }
                    }
                    Text("Move a widget by long-pressing it on Home and dragging.", color = cs.onSurfaceVariant, fontSize = 12.sp)
                }
            }
            // ---------------- D. Gestures ----------------
            item {
                Section("Gestures") {
                    DropdownRow("Swipe up", gestureOptions, s.swipeUp) { vm.set(K.SWIPE_UP, it.name) }
                    DropdownRow("Swipe down", gestureOptions, s.swipeDown) { vm.set(K.SWIPE_DOWN, it.name) }
                    DropdownRow("Long press empty space", gestureOptions, s.longPress) { vm.set(K.LONG_PRESS, it.name) }
                    DropdownRow("Double tap empty space", gestureOptions, s.doubleTap) { vm.set(K.DOUBLE_TAP, it.name) }
                    Text("Swipe left/right always switches pages. Gestures start above the system navigation area so they don't fight Android's own navigation.", color = cs.onSurfaceVariant, fontSize = 12.sp)
                }
            }
            // ---------------- E. Animation ----------------
            item {
                Section("Animation") {
                    SwitchRow("Reduce motion", "Turns transitions into instant changes", s.reduceMotion) { vm.set(K.REDUCE, it) }
                    SliderRow("Animation speed", s.animSpeed, 0.5f..2f, label = { "%.1fx".format(it) }) { vm.set(K.ANIM_SPEED, it) }
                    SliderRow("Spring bounce", s.animBounce, 0f..1f, label = { "${(it * 100).toInt()}%" }) { vm.set(K.ANIM_BOUNCE, it) }
                    SwitchRow(
                        "Background blur", if (env.blurAvailable()) "Blurs the wallpaper behind the App Library and search" else "Not available on this device or Android version. A translucent fallback is used.",
                        s.blur
                    ) { vm.set(K.BLUR, it) }
                    SwitchRow("Haptic feedback", checked = s.haptics) { vm.set(K.HAPTICS, it) }
                }
            }
            // ---------------- F. App Management ----------------
            item {
                Section("App Management") {
                    ChoiceRow("App Library layout", listOf("Categories" to 0, "A–Z" to 1), s.libraryView) { vm.set(K.LIB_VIEW, it) }
                    ChoiceRow("App Library order", listOf("Name" to 0, "Recently used" to 1), s.librarySort) {
                        if (it == 1 && !env.usageAccess.value) usageDialog = true else vm.set(K.LIB_SORT, it)
                    }
                    if (s.librarySort == 1 && !env.usageAccess.value)
                        Text("Usage Access isn't granted, so apps are sorted by name for now.", color = cs.onSurfaceVariant, fontSize = 12.sp)
                }
            }
            item {
                val folders = items.filter { it.kind == ItemKind.FOLDER }
                Section("Folders") {
                    if (folders.isEmpty()) Text("No folders. Drag one icon onto another on Home to make one.", color = cs.onSurfaceVariant)
                    folders.forEach { f ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("${f.title.ifBlank { "Folder" }} (${f.children.size})", color = cs.onSurface, modifier = Modifier.weight(1f))
                            TextButton(onClick = { vm.dissolveFolder(f.id) }) { Text("Dissolve") }
                        }
                    }
                }
            }
            item {
                Section("Hide apps") {
                    Text("Hidden apps disappear from Home, the App Library and search. They stay installed.", color = cs.onSurfaceVariant, fontSize = 12.sp)
                }
            }
            items(allApps, key = { "hide_" + it.key }) { app ->
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 2.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                        .background(cs.surfaceVariant.copy(alpha = 0.35f)).padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(app.label, color = cs.onSurface, modifier = Modifier.weight(1f), maxLines = 1)
                    Switch(checked = app.key in s.hidden, onCheckedChange = { vm.setHidden(app.key, it) })
                }
            }
            // ---------------- G. System & Launcher ----------------
            item {
                Section("System & Launcher") {
                    val def by env.isDefault
                    ButtonRow(
                        "Default launcher", if (def) "GlacierLauncher is your Home app ✓" else "GlacierLauncher is not the default Home app yet",
                        if (def) "Re-check" else "Set as Default Launcher"
                    ) { if (def) env.openHomeSettings() else env.requestDefault() }
                    ButtonRow("Android Home app settings", "Opens the system screen where you choose the Home app", "Open") { env.openHomeSettings() }
                    ButtonRow("Welcome tour", "Show the intro again", "Show") { vm.set(K.ONBOARDED, false) }
                    ButtonRow("Reset settings", "Restores defaults and rebuilds the Home layout", "Reset") { confirmReset = true }
                    Text("GlacierLauncher ${env.versionName}", color = cs.onSurface, fontWeight = FontWeight.SemiBold)
                    Text(
                        "An open, ad-free launcher. No analytics, no tracking, no accounts. Settings and layout never leave your device. The only network request is the optional weather widget (Open-Meteo).",
                        color = cs.onSurfaceVariant, fontSize = 12.sp
                    )
                }
            }
        }
    }

    if (confirmReset) AlertDialog(
        onDismissRequest = { confirmReset = false },
        title = { Text("Reset settings?") },
        text = { Text("All launcher settings, folders, hidden apps and the Home layout will be reset.") },
        confirmButton = { TextButton(onClick = { confirmReset = false; vm.resetAll() }) { Text("Reset") } },
        dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel") } }
    )
    if (usageDialog) AlertDialog(
        onDismissRequest = { usageDialog = false },
        title = { Text("Allow Usage Access?") },
        text = {
            Text(
                "To sort the App Library by recently used apps, Android needs to let GlacierLauncher see when each app was last opened. " +
                    "This stays on your device and is never sent anywhere. Without it the library is sorted by name. " +
                    "You can revoke it anytime in system settings."
            )
        },
        confirmButton = { TextButton(onClick = { usageDialog = false; vm.set(K.LIB_SORT, 1); env.openUsageAccess() }) { Text("Open settings") } },
        dismissButton = { TextButton(onClick = { usageDialog = false }) { Text("Not now") } }
    )
}
