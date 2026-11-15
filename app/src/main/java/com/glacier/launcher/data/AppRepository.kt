package com.glacier.launcher.data

import android.app.Application
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import com.glacier.launcher.model.AppEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Reads installed launchable apps and keeps the list in sync with install/uninstall/update events. */
class AppRepository(private val app: Application) {
    private val pm: PackageManager = app.packageManager
    private val _apps = MutableStateFlow<List<AppEntry>>(emptyList())
    val apps: StateFlow<List<AppEntry>> = _apps.asStateFlow()
    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    private val iconCache = LruCache<String, ImageBitmap>(160)
    @Volatile private var byKey: Map<String, AppEntry> = emptyMap()
    private var receiver: BroadcastReceiver? = null

    fun start(scope: CoroutineScope) {
        scope.launch { reload() }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        val r = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                scope.launch { reload() }
            }
        }
        ContextCompat.registerReceiver(app, r, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        receiver = r
    }

    fun stop() {
        receiver?.let { runCatching { app.unregisterReceiver(it) } }
        receiver = null
    }

    suspend fun reload() = withContext(Dispatchers.IO) {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val infos = try {
            pm.queryIntentActivities(intent, 0)
        } catch (e: Exception) {
            emptyList()
        }
        val list = infos.mapNotNull { ri ->
            runCatching {
                val ai = ri.activityInfo
                AppEntry(
                    key = "${ai.packageName}/${ai.name}",
                    packageName = ai.packageName,
                    className = ai.name,
                    label = ri.loadLabel(pm)?.toString().orEmpty().ifBlank { ai.packageName },
                    category = ai.applicationInfo?.category ?: -1
                )
            }.getOrNull()
        }.distinctBy { it.key }.sortedBy { it.label.lowercase() }
        iconCache.evictAll()
        byKey = list.associateBy { it.key }
        _apps.value = list
        _loaded.value = true
    }

    fun entry(key: String): AppEntry? = byKey[key]

    fun cachedIcon(key: String): ImageBitmap? = iconCache.get(key)

    /** Loads the real system icon off the main thread. Returns null (never throws) on failure. */
    suspend fun loadIcon(key: String): ImageBitmap? = withContext(Dispatchers.IO) {
        iconCache.get(key)?.let { return@withContext it }
        val e = byKey[key] ?: return@withContext null
        try {
            val drawable = pm.getActivityInfo(ComponentName(e.packageName, e.className), 0).loadIcon(pm)
            val bmp = drawable.toBitmap(192, 192).asImageBitmap()
            iconCache.put(key, bmp)
            bmp
        } catch (t: Throwable) {
            null
        }
    }

    /** package -> lastTimeUsed. Empty unless the user granted Usage Access. */
    suspend fun lastUsed(): Map<String, Long> = withContext(Dispatchers.IO) {
        try {
            val usm = app.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val now = System.currentTimeMillis()
            usm.queryAndAggregateUsageStats(now - 14L * 24 * 3600 * 1000, now)
                .mapValues { it.value.lastTimeUsed }
        } catch (e: Exception) {
            emptyMap()
        }
    }
}
