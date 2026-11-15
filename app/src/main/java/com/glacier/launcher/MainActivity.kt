package com.glacier.launcher

import android.app.ActivityOptions
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.lifecycleScope
import com.glacier.launcher.model.AppEntry
import com.glacier.launcher.system.DefaultLauncher
import com.glacier.launcher.system.UsageAccess
import com.glacier.launcher.ui.home.HomeScreen
import com.glacier.launcher.ui.home.LauncherEnv
import com.glacier.launcher.widget.WidgetHostController
import kotlinx.coroutines.launch
import androidx.compose.ui.geometry.Rect as ComposeRect

class MainActivity : ComponentActivity() {
    private val vm: LauncherViewModel by viewModels()
    private lateinit var widgets: WidgetHostController

    private val isDefault = mutableStateOf(false)
    private val usageAccess = mutableStateOf(false)
    private val resumeTick = mutableIntStateOf(0)

    private val roleLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { refreshStatus() }
    private val photoLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            vm.setPhoto(uri.toString())
        }
    }

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 29) window.isNavigationBarContrastEnforced = false

        widgets = WidgetHostController(
            activity = this,
            grid = { (vm.settings.value?.columns ?: 4) to (vm.settings.value?.rows ?: 6) },
            onAdded = { id, sx, sy -> vm.addHostWidget(id, sx, sy) },
            toast = { toast(it) }
        )

        lifecycleScope.launch { vm.events.collect { toast(it) } }

        val versionName = try {
            packageManager.getPackageInfo(packageName, 0).versionName ?: "1.0.0"
        } catch (e: Exception) { "1.0.0" }

        val env = LauncherEnv(
            launchApp = { e, r -> launchApp(e, r) },
            appInfo = { pkg -> safeStart(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", pkg, null))) },
            uninstall = { pkg -> safeStart(Intent(Intent.ACTION_DELETE, Uri.fromParts("package", pkg, null))) },
            openWallpaper = { safeStart(Intent.createChooser(Intent(Intent.ACTION_SET_WALLPAPER), "Wallpaper")) },
            requestDefault = { DefaultLauncher.request(this, roleLauncher) },
            openHomeSettings = { DefaultLauncher.openHomeSettings(this) },
            openNotificationAccess = { safeStart(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) },
            openUsageAccess = { safeStart(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) },
            pickPhoto = { photoLauncher.launch(arrayOf("image/*")) },
            webSearch = { q ->
                val i = Intent(Intent.ACTION_WEB_SEARCH).putExtra("query", q)
                if (!safeStart(i)) safeStart(Intent(Intent.ACTION_VIEW, Uri.parse("https://duckduckgo.com/?q=" + Uri.encode(q))))
            },
            setWindowBlur = { setWindowBlur(it) },
            blurAvailable = { blurAvailable() },
            isDefault = isDefault,
            usageAccess = usageAccess,
            resumeTick = resumeTick,
            versionName = versionName,
            widgets = widgets
        )
        setContent { HomeScreen(vm, env) }
    }

    override fun onStart() {
        super.onStart()
        widgets.start()
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
        vm.media.refresh()
        resumeTick.intValue++
    }

    override fun onStop() {
        widgets.stop()
        super.onStop()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Home button pressed while we're already the foreground launcher: reset to the first page.
        if (intent.hasCategory(Intent.CATEGORY_HOME)) vm.homePressed.tryEmit(Unit)
    }

    @Deprecated("Required by AppWidgetHost.startAppWidgetConfigureActivityForResult")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (!widgets.handleResult(requestCode, resultCode)) {
            @Suppress("DEPRECATION")
            super.onActivityResult(requestCode, resultCode, data)
        }
    }

    private fun refreshStatus() {
        isDefault.value = DefaultLauncher.isDefault(this)
        usageAccess.value = UsageAccess.has(this)
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    private fun safeStart(i: Intent): Boolean = try {
        startActivity(i); true
    } catch (e: Exception) { false }

    private fun launchApp(e: AppEntry, r: ComposeRect?) {
        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setComponent(ComponentName(e.packageName, e.className))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        try {
            val opts = r?.let {
                ActivityOptions.makeScaleUpAnimation(
                    window.decorView, it.left.toInt(), it.top.toInt(), it.width.toInt().coerceAtLeast(1), it.height.toInt().coerceAtLeast(1)
                )
            }
            startActivity(intent, opts?.toBundle())
        } catch (ex: Exception) {
            toast("Can't open ${e.label}")
        }
    }

    /** Blurs the wallpaper behind the window (Android 12+, only when the system allows it). */
    private fun blurAvailable(): Boolean =
        Build.VERSION.SDK_INT >= 31 && windowManager.isCrossWindowBlurEnabled

    private fun setWindowBlur(px: Int) {
        if (Build.VERSION.SDK_INT >= 31) {
            window.setBackgroundBlurRadius(if (px > 0 && windowManager.isCrossWindowBlurEnabled) px else 0)
        }
    }
}
