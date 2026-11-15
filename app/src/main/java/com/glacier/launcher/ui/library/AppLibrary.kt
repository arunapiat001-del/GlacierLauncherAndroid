package com.glacier.launcher.ui.library

import android.content.pm.ApplicationInfo
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.glacier.launcher.LauncherViewModel
import com.glacier.launcher.data.K
import com.glacier.launcher.model.AppEntry
import com.glacier.launcher.model.LauncherSettings
import com.glacier.launcher.ui.components.AppIconImage
import com.glacier.launcher.ui.home.LocalEnv
import com.glacier.launcher.ui.theme.LocalGlass
import com.glacier.launcher.ui.theme.glass
import kotlinx.coroutines.launch
import java.util.Locale

private fun categoryName(c: Int): String = when (c) {
    ApplicationInfo.CATEGORY_GAME -> "Games"
    ApplicationInfo.CATEGORY_AUDIO -> "Music & Audio"
    ApplicationInfo.CATEGORY_VIDEO -> "Video"
    ApplicationInfo.CATEGORY_IMAGE -> "Photo"
    ApplicationInfo.CATEGORY_SOCIAL -> "Social"
    ApplicationInfo.CATEGORY_NEWS -> "News"
    ApplicationInfo.CATEGORY_MAPS -> "Maps & Navigation"
    ApplicationInfo.CATEGORY_PRODUCTIVITY -> "Productivity"
    ApplicationInfo.CATEGORY_ACCESSIBILITY -> "Accessibility"
    else -> "Other"
}

@Composable
fun SearchField(query: String, onChange: (String) -> Unit, modifier: Modifier = Modifier, focus: FocusRequester? = null, onSearch: () -> Unit = {}) {
    val g = LocalGlass.current
    Row(
        modifier.fillMaxWidth().glass(RoundedCornerShape(16.dp), strength = 1.1f).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Search, null, tint = g.onGlassDim, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) Text("Search apps", color = g.onGlassDim, fontSize = 16.sp)
            BasicTextField(
                value = query, onValueChange = onChange, singleLine = true,
                textStyle = TextStyle(color = g.onGlass, fontSize = 16.sp),
                cursorBrush = SolidColor(g.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                modifier = Modifier.fillMaxWidth().let { if (focus != null) it.focusRequester(focus) else it }
            )
        }
        if (query.isNotEmpty()) {
            Icon(Icons.Default.Close, "Clear", tint = g.onGlassDim, modifier = Modifier.size(20.dp).pointerInput(Unit) { detectTapGestures { onChange("") } })
        }
    }
}

/** One app tile with launch + long-press menu (add to Home, hide, info, uninstall). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryAppCell(app: AppEntry, s: LauncherSettings, vm: LauncherViewModel, onLaunched: () -> Unit, modifier: Modifier = Modifier) {
    val env = LocalEnv.current
    val g = LocalGlass.current
    var bounds by remember { mutableStateOf<Rect?>(null) }
    var menu by remember { mutableStateOf(false) }
    Box(modifier) {
        Column(
            Modifier.fillMaxWidth()
                .onGloballyPositioned { bounds = it.boundsInRoot() }
                .combinedClickable(onClick = { env.launchApp(app, bounds); onLaunched() }, onLongClick = { menu = true })
                .padding(vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AppIconImage(app.key, (s.iconSize - 8).coerceAtLeast(40).dp, s.iconStyle)
            Spacer(Modifier.height(4.dp))
            Text(app.label, color = g.onGlass, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text("Add to Home") }, onClick = { menu = false; vm.addAppToHome(app.key) })
            DropdownMenuItem(text = { Text("App info") }, onClick = { menu = false; env.appInfo(app.packageName) })
            DropdownMenuItem(text = { Text("Hide from launcher") }, onClick = { menu = false; vm.setHidden(app.key, true) })
            DropdownMenuItem(text = { Text("Uninstall") }, onClick = { menu = false; env.uninstall(app.packageName) })
        }
    }
}

@Composable
fun AppLibraryScreen(vm: LauncherViewModel, s: LauncherSettings, apps: List<AppEntry>, onOpenSettings: () -> Unit, onClose: () -> Unit) {
    val g = LocalGlass.current
    val env = LocalEnv.current
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    val cols = (s.columns + 0).coerceIn(3, 5)

    // "Recently used" needs Usage Access; otherwise we silently fall back to name.
    val lastUsed by produceState<Map<String, Long>>(emptyMap(), s.librarySort, env.usageAccess.value) {
        value = if (s.librarySort == 1 && env.usageAccess.value) vm.appRepo.lastUsed() else emptyMap()
    }
    val sorted = remember(apps, s.librarySort, lastUsed) {
        if (s.librarySort == 1 && lastUsed.isNotEmpty())
            apps.sortedWith(compareByDescending<AppEntry> { lastUsed[it.packageName] ?: 0L }.thenBy { it.label.lowercase() })
        else apps.sortedBy { it.label.lowercase() }
    }
    val filtered = remember(sorted, query) {
        if (query.isBlank()) sorted else sorted.filter { it.label.contains(query.trim(), ignoreCase = true) }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(12.dp))
        SearchField(query, { query = it })
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            FilterChip(selected = s.libraryView == 0, onClick = { vm.set(K.LIB_VIEW, 0) }, label = { Text("Categories") })
            FilterChip(selected = s.libraryView == 1, onClick = { vm.set(K.LIB_VIEW, 1) }, label = { Text("A–Z") })
            Spacer(Modifier.weight(1f))
            Text("${filtered.size} apps", color = g.onGlassDim, fontSize = 12.sp)
            IconButton(onClick = onOpenSettings) { Icon(Icons.Default.Settings, "Settings", tint = g.onGlass) }
        }
        Spacer(Modifier.height(8.dp))

        when {
            apps.isEmpty() -> EmptyState("No apps to show", "Apps you hide in Settings › App Management won't appear here.")
            filtered.isEmpty() -> EmptyState("No results", "Nothing matches “$query”.")
            query.isNotBlank() -> FlatGrid(filtered, cols, s, vm, onClose)
            s.libraryView == 1 -> AlphabeticalList(filtered, cols, s, vm, onClose)
            else -> CategoryList(filtered, cols, s, vm, onClose)
        }
    }
}

@Composable
private fun EmptyState(title: String, body: String) {
    val g = LocalGlass.current
    Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, color = g.onGlass, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text(body, color = g.onGlassDim, fontSize = 14.sp, textAlign = TextAlign.Center)
    }
}

/** Rows of [cols] cells. Lazy lists + chunking keeps big app lists cheap. */
@Composable
private fun FlatGrid(list: List<AppEntry>, cols: Int, s: LauncherSettings, vm: LauncherViewModel, onClose: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize()) {
        items(list.chunked(cols)) { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { LibraryAppCell(it, s, vm, onClose, Modifier.weight(1f)) }
                repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun CategoryList(list: List<AppEntry>, cols: Int, s: LauncherSettings, vm: LauncherViewModel, onClose: () -> Unit) {
    val g = LocalGlass.current
    val groups = remember(list) {
        list.groupBy { categoryName(it.category) }.toList()
            .sortedWith(compareBy({ it.first == "Other" }, { it.first }))
    }
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        items(groups) { (name, apps) ->
            Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(22.dp)).padding(12.dp)) {
                Text(name, color = g.onGlass, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, modifier = Modifier.padding(start = 4.dp, bottom = 4.dp))
                apps.chunked(cols).forEach { row ->
                    Row(Modifier.fillMaxWidth()) {
                        row.forEach { LibraryAppCell(it, s, vm, onClose, Modifier.weight(1f)) }
                        repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun AlphabeticalList(list: List<AppEntry>, cols: Int, s: LauncherSettings, vm: LauncherViewModel, onClose: () -> Unit) {
    val g = LocalGlass.current
    val scope = rememberCoroutineScope()
    val state = rememberLazyListState()
    fun letter(a: AppEntry): String {
        val c = a.label.trim().firstOrNull()?.uppercaseChar() ?: '#'
        return if (c.isLetter()) c.toString() else "#"
    }
    val groups = remember(list) { list.groupBy { letter(it) }.toSortedMap(compareBy<String>({ it == "#" }, { it })).toList() }
    val letters = groups.map { it.first }
    // index of first LazyColumn item for each letter (header + chunks)
    val starts = remember(groups, cols) {
        var idx = 0
        groups.associate { (l, apps) -> val at = idx; idx += 1 + (apps.size + cols - 1) / cols; l to at }
    }
    Row(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(1f), state = state) {
            groups.forEach { (l, apps) ->
                item(key = "h$l") { Text(l, color = g.accent, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.padding(top = 10.dp, bottom = 2.dp, start = 4.dp)) }
                items(apps.chunked(cols), key = { "r$l${it.first().key}" }) { row ->
                    Row(Modifier.fillMaxWidth()) {
                        row.forEach { LibraryAppCell(it, s, vm, onClose, Modifier.weight(1f)) }
                        repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
        // Alphabet index: tap or drag
        var h by remember { mutableIntStateOf(1) }
        Column(
            Modifier.fillMaxHeight().padding(start = 4.dp).onGloballyPositioned { h = it.size.height.coerceAtLeast(1) }
                .pointerInput(letters) {
                    detectVerticalDragGestures { change, _ ->
                        val i = ((change.position.y / h) * letters.size).toInt().coerceIn(0, letters.lastIndex)
                        scope.launch { state.scrollToItem(starts[letters[i]] ?: 0) }
                    }
                }
                .pointerInput(letters) {
                    detectTapGestures { p ->
                        val i = ((p.y / h) * letters.size).toInt().coerceIn(0, letters.lastIndex)
                        scope.launch { state.scrollToItem(starts[letters[i]] ?: 0) }
                    }
                },
            verticalArrangement = Arrangement.SpaceEvenly, horizontalAlignment = Alignment.CenterHorizontally
        ) {
            letters.forEach { Text(it, color = g.onGlassDim, fontSize = 10.sp, fontWeight = FontWeight.SemiBold) }
        }
    }
}

/** Swipe-down search panel: instant app search plus a web-search fallback. */
@Composable
fun SearchPanel(vm: LauncherViewModel, s: LauncherSettings, apps: List<AppEntry>, onClose: () -> Unit) {
    val g = LocalGlass.current
    val env = LocalEnv.current
    var query by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) { focus.requestFocus(); keyboard?.show() }
    val results = remember(apps, query) {
        if (query.isBlank()) emptyList()
        else apps.filter { it.label.contains(query.trim(), ignoreCase = true) }
            .sortedWith(compareBy({ !it.label.startsWith(query.trim(), ignoreCase = true) }, { it.label.lowercase() }))
            .take(30)
    }
    Column(Modifier.fillMaxSize().statusBarsPadding().imePadding().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            SearchField(query, { query = it }, Modifier.weight(1f), focus) {
                if (results.size == 1) { env.launchApp(results[0], null); onClose() }
                else if (query.isNotBlank()) { env.webSearch(query.trim()); onClose() }
            }
            TextButton(onClick = onClose) { Text("Cancel", color = g.onGlass) }
        }
        Spacer(Modifier.height(8.dp))
        when {
            query.isBlank() -> EmptyState("Search", "Type to find an app instantly.")
            else -> LazyColumn(Modifier.fillMaxSize()) {
                items(results, key = { it.key }) { app ->
                    Row(
                        Modifier.fillMaxWidth().pointerInput(app.key) { detectTapGestures { env.launchApp(app, null); onClose() } }.padding(vertical = 8.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AppIconImage(app.key, 44.dp, s.iconStyle)
                        Spacer(Modifier.width(14.dp))
                        Text(app.label, color = g.onGlass, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                item {
                    Row(
                        Modifier.fillMaxWidth().pointerInput(query) { detectTapGestures { env.webSearch(query.trim()); onClose() } }.padding(vertical = 14.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Search, null, tint = g.accent)
                        Spacer(Modifier.width(14.dp))
                        Text(if (results.isEmpty()) "No apps found. Search the web for “${query.trim()}”" else "Search the web for “${query.trim()}”", color = g.onGlass, fontSize = 15.sp)
                    }
                }
            }
        }
    }
}

