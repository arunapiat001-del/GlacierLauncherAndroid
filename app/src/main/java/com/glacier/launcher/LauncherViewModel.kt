package com.glacier.launcher

import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.datastore.preferences.core.Preferences
import com.glacier.launcher.data.*
import com.glacier.launcher.media.MediaRepository
import com.glacier.launcher.model.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

class LauncherViewModel(application: Application) : AndroidViewModel(application) {
    val settingsRepo = SettingsRepository(application)
    val appRepo = AppRepository(application)
    val weatherRepo = WeatherRepository()
    val media = MediaRepository(application)

    /** null until DataStore has delivered the first value (prevents a flash of defaults). */
    val settings: StateFlow<LauncherSettings?> = settingsRepo.settings
        .map<LauncherSettings, LauncherSettings?> { it }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _items = MutableStateFlow<List<HomeItem>>(emptyList())
    val items: StateFlow<List<HomeItem>> = _items.asStateFlow()

    /** Installed apps minus those the user hid. */
    val visibleApps: StateFlow<List<AppEntry>> = combine(appRepo.apps, settings) { apps, s ->
        if (s == null) apps else apps.filter { it.key !in s.hidden }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val events: SharedFlow<String> = _events.asSharedFlow()
    val homePressed = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Updated by the UI so new widgets/apps are placed on the page the user is looking at. */
    var currentPage: Int = 0

    private data class GridKey(val cols: Int, val rows: Int, val pages: Int, val dock: Int)

    init {
        appRepo.start(viewModelScope)
        viewModelScope.launch {
            val first = settingsRepo.settings.first()
            _items.value = LayoutCodec.decode(first.layoutJson)
            val gridKeys = settingsRepo.settings
                .map { GridKey(it.columns, it.rows, it.pages, it.dockCount) }
                .distinctUntilChanged()
            combine(gridKeys, appRepo.apps, settingsRepo.settings.map { it.seeded }.distinctUntilChanged()) { g, apps, seeded ->
                Triple(g, apps, seeded)
            }.collect { (g, apps, seeded) ->
                if (!appRepo.loaded.value || apps.isEmpty()) return@collect
                val s = settingsRepo.settings.first()
                if (!s.seeded) seed(s, apps) else reconcile(g, apps)
            }
        }
    }

    override fun onCleared() {
        appRepo.stop()
        super.onCleared()
    }

    fun toast(msg: String) { _events.tryEmit(msg) }
    private fun newId() = UUID.randomUUID().toString().take(8)

    private fun commit(list: List<HomeItem>) {
        _items.value = list
        viewModelScope.launch { settingsRepo.set(K.LAYOUT, LayoutCodec.encode(list)) }
    }

    fun <T> set(key: Preferences.Key<T>, value: T) {
        viewModelScope.launch { settingsRepo.set(key, value) }
    }

    // ---------- seeding & reconciliation ----------

    private suspend fun seed(s: LauncherSettings, apps: List<AppEntry>) {
        val pm = getApplication<Application>().packageManager
        val visible = apps.filter { it.key !in s.hidden }
        var list = emptyList<HomeItem>()
        val used = HashSet<String>()

        // Dock: phone, messages, browser, camera (whatever the device actually resolves).
        val probes = listOf(
            Intent(Intent.ACTION_DIAL),
            Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:")),
            Intent(Intent.ACTION_VIEW, Uri.parse("https://")),
            Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
        )
        var slot = 0
        for (p in probes) {
            if (slot >= s.dockCount) break
            val pkg = try {
                pm.resolveActivity(p, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
            } catch (e: Exception) { null } ?: continue
            val app = visible.firstOrNull { it.packageName == pkg && it.key !in used } ?: continue
            list = list + HomeItem(newId(), ItemKind.APP, DOCK_PAGE, slot, 0, appKey = app.key)
            used += app.key
            slot++
        }

        // Page 1: clock widget + as many apps as fit. Everything else stays in the App Library.
        val w = presetSpan(1, s.columns, s.rows)
        list = list + HomeItem(newId(), ItemKind.WIDGET, 0, 0, 0, w.first, w.second, builtin = BuiltinWidget.CLOCK.name)
        for (app in visible) {
            if (app.key in used) continue
            val spot = LayoutOps.findFree(list, 0, 1, 1, s.columns, s.rows) ?: break
            list = list + HomeItem(newId(), ItemKind.APP, 0, spot.first, spot.second, appKey = app.key)
            used += app.key
        }
        _items.value = list
        settingsRepo.set(K.LAYOUT, LayoutCodec.encode(list))
        settingsRepo.set(K.SEEDED, true)
    }

    /** Keeps the layout valid after grid changes and after apps are uninstalled. */
    private fun reconcile(g: GridKey, apps: List<AppEntry>) {
        val installed = apps.map { it.key }.toHashSet()
        var list = _items.value.mapNotNull { i ->
            when (i.kind) {
                ItemKind.APP -> if (i.appKey in installed) i else null
                ItemKind.FOLDER -> {
                    val kids = i.children.filter { it in installed }
                    if (kids.isEmpty()) null else i.copy(children = kids)
                }
                ItemKind.WIDGET -> i
            }
        }
        list = LayoutOps.normalize(list, g.cols, g.rows, g.pages, g.dock)
        if (list != _items.value) commit(list)
    }

    // ---------- placement ----------

    fun presetSpan(preset: Int, cols: Int, rows: Int): Pair<Int, Int> = when (preset) {
        0 -> minOf(2, cols) to minOf(2, rows)
        1 -> minOf(4, cols) to minOf(2, rows)
        else -> minOf(4, cols) to minOf(4, rows)
    }

    fun addBuiltinWidget(type: BuiltinWidget, preset: Int): Boolean {
        val s = settings.value ?: return false
        val (sx, sy) = presetSpan(preset, s.columns, s.rows)
        val spot = LayoutOps.findFreeAnyPage(_items.value, currentPage, s.pages, sx, sy, s.columns, s.rows)
        if (spot == null) { toast("No room for that widget. Free some space or add a page in Settings."); return false }
        commit(_items.value + HomeItem(newId(), ItemKind.WIDGET, spot.first, spot.second, spot.third, sx, sy, builtin = type.name))
        return true
    }

    fun addHostWidget(widgetId: Int, sx: Int, sy: Int): Boolean {
        val s = settings.value ?: return false
        val spot = LayoutOps.findFreeAnyPage(_items.value, currentPage, s.pages, sx, sy, s.columns, s.rows)
        if (spot == null) { toast("No room for that widget."); return false }
        commit(_items.value + HomeItem(newId(), ItemKind.WIDGET, spot.first, spot.second, spot.third, sx, sy, hostWidgetId = widgetId))
        return true
    }

    fun addAppToHome(key: String) {
        val s = settings.value ?: return
        if (_items.value.any { it.kind == ItemKind.APP && it.appKey == key }) { toast("Already on Home"); return }
        val spot = LayoutOps.findFreeAnyPage(_items.value, currentPage, s.pages, 1, 1, s.columns, s.rows)
        if (spot == null) { toast("Home screen is full"); return }
        commit(_items.value + HomeItem(newId(), ItemKind.APP, spot.first, spot.second, spot.third, appKey = key))
    }

    fun removeItem(id: String) = commit(_items.value.filter { it.id != id })

    fun resizeWidget(id: String, preset: Int) {
        val s = settings.value ?: return
        val list = _items.value
        val item = list.firstOrNull { it.id == id } ?: return
        val (sx, sy) = presetSpan(preset, s.columns, s.rows)
        val c = item.col.coerceAtMost(s.columns - sx)
        val r = item.row.coerceAtMost(s.rows - sy)
        if (LayoutOps.isFree(list, item.page, c, r, sx, sy, id, s.columns, s.rows)) {
            commit(list.map { if (it.id == id) it.copy(col = c, row = r, spanX = sx, spanY = sy) else it })
            return
        }
        val spot = LayoutOps.findFree(list, item.page, sx, sy, s.columns, s.rows, ignoreId = id)
        if (spot != null) commit(list.map { if (it.id == id) it.copy(col = spot.first, row = spot.second, spanX = sx, spanY = sy) else it })
        else toast("Not enough space on this page for that size")
    }

    /** Called when an icon/widget is dropped. Handles move, folder creation and folder insert. */
    fun dropItem(id: String, page: Int, col: Int, row: Int) {
        val s = settings.value ?: return
        val list = _items.value
        val item = list.firstOrNull { it.id == id } ?: return
        val cols = if (page == DOCK_PAGE) s.dockCount else s.columns
        val rows = if (page == DOCK_PAGE) 1 else s.rows

        if (item.kind == ItemKind.WIDGET) {
            if (page == DOCK_PAGE) return
            val c = col.coerceIn(0, cols - item.spanX)
            val r = row.coerceIn(0, rows - item.spanY)
            if (LayoutOps.isFree(list, page, c, r, item.spanX, item.spanY, id, cols, rows))
                commit(list.map { if (it.id == id) it.copy(page = page, col = c, row = r) else it })
            else toast("Not enough space there")
            return
        }

        val c = col.coerceIn(0, cols - 1)
        val r = row.coerceIn(0, rows - 1)
        val occupant = list.firstOrNull { it.id != id && LayoutOps.occupies(it, page, c, r) }
        when {
            occupant == null ->
                commit(list.map { if (it.id == id) it.copy(page = page, col = c, row = r) else it })
            occupant.kind == ItemKind.WIDGET -> toast("That spot is taken by a widget")
            occupant.kind == ItemKind.FOLDER && item.kind == ItemKind.APP && item.appKey != null ->
                commit(list.filter { it.id != id }.map {
                    if (it.id == occupant.id && item.appKey !in it.children) it.copy(children = it.children + item.appKey) else it
                })
            occupant.kind == ItemKind.APP && item.kind == ItemKind.APP && occupant.appKey != null && item.appKey != null -> {
                val folder = HomeItem(
                    newId(), ItemKind.FOLDER, occupant.page, occupant.col, occupant.row,
                    title = "Folder", children = listOf(occupant.appKey, item.appKey)
                )
                commit(list.filter { it.id != id && it.id != occupant.id } + folder)
            }
            else -> Unit
        }
    }

    // ---------- folders ----------

    fun renameFolder(id: String, title: String) =
        commit(_items.value.map { if (it.id == id) it.copy(title = title) else it })

    fun removeFromFolder(folderId: String, appKey: String) {
        val s = settings.value ?: return
        val list = _items.value
        val folder = list.firstOrNull { it.id == folderId } ?: return
        val spot = LayoutOps.findFreeAnyPage(list, folder.page.coerceAtLeast(0), s.pages, 1, 1, s.columns, s.rows)
        if (spot == null) { toast("Home screen is full"); return }
        val kids = folder.children - appKey
        var next = list.map { if (it.id == folderId) it.copy(children = kids) else it }
        if (kids.isEmpty()) next = next.filter { it.id != folderId }
        commit(next + HomeItem(newId(), ItemKind.APP, spot.first, spot.second, spot.third, appKey = appKey))
    }

    fun dissolveFolder(id: String) {
        val s = settings.value ?: return
        val folder = _items.value.firstOrNull { it.id == id } ?: return
        var list = _items.value.filter { it.id != id }
        var dropped = 0
        for (key in folder.children) {
            val start = folder.page.coerceAtLeast(0)
            val spot = LayoutOps.findFreeAnyPage(list, start, s.pages, 1, 1, s.columns, s.rows)
            if (spot == null) { dropped++; continue }
            list = list + HomeItem(newId(), ItemKind.APP, spot.first, spot.second, spot.third, appKey = key)
        }
        commit(list)
        if (dropped > 0) toast("$dropped app(s) didn't fit on Home. They're still in the App Library.")
    }

    // ---------- hiding / misc settings ----------

    fun setHidden(key: String, hide: Boolean) {
        val s = settings.value ?: return
        val set = if (hide) s.hidden + key else s.hidden - key
        viewModelScope.launch { settingsRepo.set(K.HIDDEN, set) }
        if (hide) {
            commit(_items.value.mapNotNull { i ->
                when (i.kind) {
                    ItemKind.APP -> if (i.appKey == key) null else i
                    ItemKind.FOLDER -> {
                        val kids = i.children - key
                        if (kids.isEmpty()) null else i.copy(children = kids)
                    }
                    ItemKind.WIDGET -> i
                }
            })
        }
    }

    fun finishOnboarding() = set(K.ONBOARDED, true)
    fun setNotes(text: String) = set(K.NOTES, text)
    fun setPhoto(uri: String) = set(K.PHOTO, uri)

    fun setCity(city: String) {
        viewModelScope.launch {
            settingsRepo.set(K.CITY, city.trim())
            settingsRepo.set(K.WEATHER_CACHE, "")
            weatherRepo.refresh(city, "", true) { settingsRepo.set(K.WEATHER_CACHE, it) }
        }
    }

    fun refreshWeather(force: Boolean) {
        val s = settings.value ?: return
        viewModelScope.launch {
            weatherRepo.refresh(s.weatherCity, s.weatherCache, force) { settingsRepo.set(K.WEATHER_CACHE, it) }
        }
    }

    fun resetAll() {
        viewModelScope.launch {
            _items.value = emptyList()
            settingsRepo.clearAll()   // seeded=false -> the layout is rebuilt automatically
            toast("Settings reset")
        }
    }
}
