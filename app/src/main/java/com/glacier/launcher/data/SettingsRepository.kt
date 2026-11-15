package com.glacier.launcher.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.glacier.launcher.model.GestureAction
import com.glacier.launcher.model.LauncherSettings
import com.glacier.launcher.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "glacier_settings")

/** All preference keys. Public so the UI can write through the ViewModel generically. */
object K {
    val THEME = stringPreferencesKey("theme")
    val ACCENT = intPreferencesKey("accent")
    val GLASS = floatPreferencesKey("glass")
    val CORNER = intPreferencesKey("corner")
    val ICON_STYLE = intPreferencesKey("icon_style")
    val ICON_SIZE = intPreferencesKey("icon_size")
    val COLUMNS = intPreferencesKey("columns")
    val ROWS = intPreferencesKey("rows")
    val LABELS = booleanPreferencesKey("labels")
    val DOCK = intPreferencesKey("dock")
    val GRID_PAD = intPreferencesKey("grid_pad")
    val PAGES = intPreferencesKey("pages")
    val HAPTICS = booleanPreferencesKey("haptics")
    val REDUCE = booleanPreferencesKey("reduce_motion")
    val ANIM_SPEED = floatPreferencesKey("anim_speed")
    val ANIM_BOUNCE = floatPreferencesKey("anim_bounce")
    val BLUR = booleanPreferencesKey("blur")
    val SWIPE_UP = stringPreferencesKey("swipe_up")
    val SWIPE_DOWN = stringPreferencesKey("swipe_down")
    val LONG_PRESS = stringPreferencesKey("long_press")
    val DOUBLE_TAP = stringPreferencesKey("double_tap")
    val LIB_VIEW = intPreferencesKey("lib_view")
    val LIB_SORT = intPreferencesKey("lib_sort")
    val HIDDEN = stringSetPreferencesKey("hidden")
    val ONBOARDED = booleanPreferencesKey("onboarded")
    val SEEDED = booleanPreferencesKey("seeded")
    val LAYOUT = stringPreferencesKey("layout")
    val NOTES = stringPreferencesKey("notes")
    val CITY = stringPreferencesKey("weather_city")
    val WEATHER_CACHE = stringPreferencesKey("weather_cache")
    val PHOTO = stringPreferencesKey("photo_uri")
}

class SettingsRepository(private val context: Context) {

    val settings: Flow<LauncherSettings> = context.dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { it.toSettings() }

    suspend fun <T> set(key: Preferences.Key<T>, value: T) {
        context.dataStore.edit { it[key] = value }
    }

    suspend fun clearAll() {
        context.dataStore.edit { it.clear() }
    }

    private inline fun <reified E : Enum<E>> Preferences.enum(key: Preferences.Key<String>, def: E): E =
        this[key]?.let { v -> runCatching { enumValueOf<E>(v) }.getOrNull() } ?: def

    private fun Preferences.toSettings(): LauncherSettings {
        val d = LauncherSettings()
        return LauncherSettings(
            themeMode = enum(K.THEME, d.themeMode),
            accent = this[K.ACCENT] ?: d.accent,
            glass = this[K.GLASS] ?: d.glass,
            corner = this[K.CORNER] ?: d.corner,
            iconStyle = this[K.ICON_STYLE] ?: d.iconStyle,
            iconSize = this[K.ICON_SIZE] ?: d.iconSize,
            columns = (this[K.COLUMNS] ?: d.columns).coerceIn(3, 6),
            rows = (this[K.ROWS] ?: d.rows).coerceIn(4, 7),
            showLabels = this[K.LABELS] ?: d.showLabels,
            dockCount = (this[K.DOCK] ?: d.dockCount).coerceIn(3, 5),
            gridPadding = this[K.GRID_PAD] ?: d.gridPadding,
            pages = (this[K.PAGES] ?: d.pages).coerceIn(1, 6),
            haptics = this[K.HAPTICS] ?: d.haptics,
            reduceMotion = this[K.REDUCE] ?: d.reduceMotion,
            animSpeed = this[K.ANIM_SPEED] ?: d.animSpeed,
            animBounce = this[K.ANIM_BOUNCE] ?: d.animBounce,
            blur = this[K.BLUR] ?: d.blur,
            swipeUp = enum(K.SWIPE_UP, d.swipeUp),
            swipeDown = enum(K.SWIPE_DOWN, d.swipeDown),
            longPress = enum(K.LONG_PRESS, d.longPress),
            doubleTap = enum(K.DOUBLE_TAP, d.doubleTap),
            libraryView = this[K.LIB_VIEW] ?: d.libraryView,
            librarySort = this[K.LIB_SORT] ?: d.librarySort,
            hidden = this[K.HIDDEN] ?: d.hidden,
            onboarded = this[K.ONBOARDED] ?: d.onboarded,
            seeded = this[K.SEEDED] ?: d.seeded,
            layoutJson = this[K.LAYOUT] ?: d.layoutJson,
            notes = this[K.NOTES] ?: d.notes,
            weatherCity = this[K.CITY] ?: d.weatherCity,
            weatherCache = this[K.WEATHER_CACHE] ?: d.weatherCache,
            photoUri = this[K.PHOTO] ?: d.photoUri
        )
    }
}
