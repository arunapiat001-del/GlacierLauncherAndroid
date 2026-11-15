package com.glacier.launcher.ui.home

import androidx.compose.runtime.State
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Rect
import com.glacier.launcher.model.AppEntry
import com.glacier.launcher.widget.WidgetHostController

/** Things only the Activity can do. Keeps composables free of Context plumbing. */
class LauncherEnv(
    val launchApp: (AppEntry, Rect?) -> Unit,
    val appInfo: (String) -> Unit,
    val uninstall: (String) -> Unit,
    val openWallpaper: () -> Unit,
    val requestDefault: () -> Unit,
    val openHomeSettings: () -> Unit,
    val openNotificationAccess: () -> Unit,
    val openUsageAccess: () -> Unit,
    val pickPhoto: () -> Unit,
    val webSearch: (String) -> Unit,
    val setWindowBlur: (Int) -> Unit,
    val blurAvailable: () -> Boolean,
    val isDefault: State<Boolean>,
    val usageAccess: State<Boolean>,
    val resumeTick: State<Int>,
    val versionName: String,
    val widgets: WidgetHostController
)

val LocalEnv = staticCompositionLocalOf<LauncherEnv> { error("LauncherEnv not provided") }
