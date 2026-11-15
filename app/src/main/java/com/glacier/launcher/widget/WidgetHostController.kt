package com.glacier.launcher.widget

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Intent
import kotlin.math.ceil

/**
 * Wraps the official AppWidgetHost / AppWidgetManager APIs so users can add widgets from other apps.
 *
 * Flow: allocate id -> bindAppWidgetIdIfAllowed (else system "allow this app to bind" dialog)
 *       -> optional configure activity -> place on grid.
 *
 * Android limits (documented in README): widgets are rendered by their owning app via RemoteViews,
 * so we cannot restyle them, and some widgets (e.g. ones needing a special config activity or
 * list items that swallow touches) can't be dragged by long-press on their own surface.
 */
class WidgetHostController(
    private val activity: Activity,
    private val grid: () -> Pair<Int, Int>,                       // (columns, rows)
    private val onAdded: (id: Int, spanX: Int, spanY: Int) -> Boolean,
    private val toast: (String) -> Unit
) {
    companion object {
        private const val HOST_ID = 0x474C
        const val REQ_BIND = 7001
        const val REQ_CONFIGURE = 7002
    }

    val manager: AppWidgetManager = AppWidgetManager.getInstance(activity)
    private val host = AppWidgetHost(activity, HOST_ID)
    private var pendingId = -1

    fun start() { try { host.startListening() } catch (e: Exception) { /* ignore */ } }
    fun stop() { try { host.stopListening() } catch (e: Exception) { /* ignore */ } }

    fun providers(): List<AppWidgetProviderInfo> =
        try { manager.installedProviders } catch (e: Exception) { emptyList() }

    fun request(info: AppWidgetProviderInfo) {
        val id = host.allocateAppWidgetId()
        pendingId = id
        val allowed = try { manager.bindAppWidgetIdIfAllowed(id, info.provider) } catch (e: Exception) { false }
        if (allowed) {
            afterBind(id)
        } else {
            val i = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
            try {
                @Suppress("DEPRECATION")
                activity.startActivityForResult(i, REQ_BIND)
            } catch (e: Exception) {
                toast("This device can't bind widgets for third-party launchers")
                cancel(id)
            }
        }
    }

    /** Returns true if the result belonged to us. */
    fun handleResult(requestCode: Int, resultCode: Int): Boolean {
        val id = pendingId
        when (requestCode) {
            REQ_BIND -> {
                if (id < 0) return true
                if (resultCode == Activity.RESULT_OK) afterBind(id) else cancel(id)
                return true
            }
            REQ_CONFIGURE -> {
                if (id < 0) return true
                if (resultCode == Activity.RESULT_OK) finish(id) else cancel(id)
                return true
            }
        }
        return false
    }

    private fun afterBind(id: Int) {
        val info = manager.getAppWidgetInfo(id)
        if (info == null) { cancel(id); return }
        if (info.configure != null) {
            try {
                host.startAppWidgetConfigureActivityForResult(activity, id, 0, REQ_CONFIGURE, null)
            } catch (e: Exception) {
                toast("Couldn't open the widget's setup screen")
                cancel(id)
            }
        } else finish(id)
    }

    private fun finish(id: Int) {
        val info = manager.getAppWidgetInfo(id)
        if (info == null) { cancel(id); return }
        val (cols, rows) = grid()
        val m = activity.resources.displayMetrics
        val d = m.density
        val cellW = (m.widthPixels / d) / cols
        val cellH = (m.heightPixels / d) * 0.72f / rows
        val sx = ceil(info.minWidth / d / cellW).toInt().coerceIn(1, cols)
        val sy = ceil(info.minHeight / d / cellH).toInt().coerceIn(1, rows)
        pendingId = -1
        if (!onAdded(id, sx, sy)) host.deleteAppWidgetId(id)
    }

    private fun cancel(id: Int) {
        try { host.deleteAppWidgetId(id) } catch (e: Exception) { /* ignore */ }
        pendingId = -1
    }

    fun delete(id: Int) { try { host.deleteAppWidgetId(id) } catch (e: Exception) { /* ignore */ } }

    fun info(id: Int): AppWidgetProviderInfo? = try { manager.getAppWidgetInfo(id) } catch (e: Exception) { null }

    fun createView(id: Int): AppWidgetHostView? {
        val info = info(id) ?: return null
        return try { host.createView(activity, id, info) } catch (e: Exception) { null }
    }
}
