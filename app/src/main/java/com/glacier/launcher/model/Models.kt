package com.glacier.launcher.model

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Actions that can be bound to configurable gestures. */
enum class GestureAction(val label: String) {
    NONE("Do nothing"),
    APP_LIBRARY("App Library"),
    SEARCH("Search"),
    SETTINGS("Settings"),
    WIDGETS("Add widget"),
    WALLPAPER("Wallpaper")
}

enum class ItemKind { APP, FOLDER, WIDGET }

enum class BuiltinWidget(val title: String, val description: String) {
    CLOCK("Clock & Date", "Current time and date"),
    CALENDAR("Calendar", "Month view (dates only, no event access)"),
    WEATHER("Weather", "Open-Meteo forecast for a city you choose"),
    BATTERY("Battery", "Level and charging state"),
    NOTES("Notes", "A simple note saved on this device"),
    MUSIC("Music", "Now playing (needs Notification Access)"),
    PHOTO("Photo", "A photo you pick from your gallery"),
    DEVICE("Device", "Model, Android version, memory and storage")
}

const val DOCK_PAGE = -1

/** Launchable activity. key = "package/class". */
data class AppEntry(
    val key: String,
    val packageName: String,
    val className: String,
    val label: String,
    val category: Int
)

/**
 * One thing placed on the home screen (or dock when page == DOCK_PAGE).
 * Widgets use spanX/spanY; apps and folders are always 1x1.
 */
data class HomeItem(
    val id: String,
    val kind: ItemKind,
    val page: Int,
    val col: Int,
    val row: Int,
    val spanX: Int = 1,
    val spanY: Int = 1,
    val appKey: String? = null,
    val title: String = "",
    val children: List<String> = emptyList(),
    val builtin: String? = null,
    val hostWidgetId: Int = -1
)

data class LauncherSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val accent: Int = 0,
    val glass: Float = 0.6f,
    val corner: Int = 24,
    val iconStyle: Int = 0,          // 0 rounded, 1 circle, 2 original
    val iconSize: Int = 56,
    val columns: Int = 4,
    val rows: Int = 6,
    val showLabels: Boolean = true,
    val dockCount: Int = 4,
    val gridPadding: Int = 8,
    val pages: Int = 3,
    val haptics: Boolean = true,
    val reduceMotion: Boolean = false,
    val animSpeed: Float = 1f,
    val animBounce: Float = 0.5f,
    val blur: Boolean = true,
    val swipeUp: GestureAction = GestureAction.APP_LIBRARY,
    val swipeDown: GestureAction = GestureAction.SEARCH,
    val longPress: GestureAction = GestureAction.WIDGETS,
    val doubleTap: GestureAction = GestureAction.SETTINGS,
    val libraryView: Int = 0,        // 0 categories, 1 A-Z
    val librarySort: Int = 0,        // 0 name, 1 recently used
    val hidden: Set<String> = emptySet(),
    val onboarded: Boolean = false,
    val seeded: Boolean = false,
    val layoutJson: String = "",
    val notes: String = "",
    val weatherCity: String = "",
    val weatherCache: String = "",
    val photoUri: String = ""
)
