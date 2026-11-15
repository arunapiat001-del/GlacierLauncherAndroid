package com.glacier.launcher.data

import com.glacier.launcher.model.DOCK_PAGE
import com.glacier.launcher.model.HomeItem
import com.glacier.launcher.model.ItemKind
import org.json.JSONArray
import org.json.JSONObject

/** JSON (de)serialisation of the home layout. Stored locally in DataStore only. */
object LayoutCodec {
    fun encode(items: List<HomeItem>): String {
        val arr = JSONArray()
        items.forEach { i ->
            val o = JSONObject()
            o.put("id", i.id)
            o.put("k", i.kind.name)
            o.put("p", i.page)
            o.put("c", i.col)
            o.put("r", i.row)
            o.put("sx", i.spanX)
            o.put("sy", i.spanY)
            if (i.appKey != null) o.put("a", i.appKey)
            o.put("t", i.title)
            o.put("ch", JSONArray(i.children))
            if (i.builtin != null) o.put("b", i.builtin)
            o.put("w", i.hostWidgetId)
            arr.put(o)
        }
        return arr.toString()
    }

    fun decode(json: String): List<HomeItem> {
        if (json.isBlank()) return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).mapNotNull { idx ->
                runCatching {
                    val o = arr.getJSONObject(idx)
                    val ch = o.optJSONArray("ch")
                    HomeItem(
                        id = o.getString("id"),
                        kind = ItemKind.valueOf(o.getString("k")),
                        page = o.optInt("p", 0),
                        col = o.optInt("c", 0),
                        row = o.optInt("r", 0),
                        spanX = o.optInt("sx", 1),
                        spanY = o.optInt("sy", 1),
                        appKey = if (o.has("a")) o.getString("a") else null,
                        title = o.optString("t", ""),
                        children = if (ch == null) emptyList() else (0 until ch.length()).map { ch.getString(it) },
                        builtin = if (o.has("b")) o.getString("b") else null,
                        hostWidgetId = o.optInt("w", -1)
                    )
                }.getOrNull()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}

/** Pure grid helpers (no Android dependencies). */
object LayoutOps {
    fun occupies(i: HomeItem, page: Int, col: Int, row: Int): Boolean =
        i.page == page && col >= i.col && col < i.col + i.spanX && row >= i.row && row < i.row + i.spanY

    fun isFree(
        items: List<HomeItem>, page: Int, col: Int, row: Int, sx: Int, sy: Int,
        ignoreId: String?, cols: Int, rows: Int
    ): Boolean {
        if (col < 0 || row < 0 || col + sx > cols || row + sy > rows) return false
        return items.none {
            it.id != ignoreId && it.page == page &&
                it.col < col + sx && col < it.col + it.spanX &&
                it.row < row + sy && row < it.row + it.spanY
        }
    }

    fun findFree(items: List<HomeItem>, page: Int, sx: Int, sy: Int, cols: Int, rows: Int, ignoreId: String? = null): Pair<Int, Int>? {
        for (r in 0..(rows - sy)) for (c in 0..(cols - sx)) {
            if (isFree(items, page, c, r, sx, sy, ignoreId, cols, rows)) return c to r
        }
        return null
    }

    /** Searches [startPage] first, then the remaining pages. Returns Triple(page, col, row). */
    fun findFreeAnyPage(items: List<HomeItem>, startPage: Int, pages: Int, sx: Int, sy: Int, cols: Int, rows: Int): Triple<Int, Int, Int>? {
        val order = (0 until pages).sortedBy { if (it >= startPage) it - startPage else it + pages }
        for (p in order) findFree(items, p, sx, sy, cols, rows)?.let { return Triple(p, it.first, it.second) }
        return null
    }

    /**
     * Re-flows items after the grid shrinks (fewer columns/rows/pages/dock slots).
     * Items that fit stay put; the rest move to the first free cell; items that cannot fit anywhere
     * are dropped from the home screen (the app remains in the App Library).
     */
    fun normalize(items: List<HomeItem>, cols: Int, rows: Int, pages: Int, dock: Int): List<HomeItem> {
        val out = ArrayList<HomeItem>()
        val order = items.sortedWith(compareBy({ it.page != DOCK_PAGE }, { it.page }, { it.row }, { it.col }))
        val deferred = ArrayList<HomeItem>()
        for (it in order) {
            val ok = if (it.page == DOCK_PAGE) {
                it.col < dock && isFree(out, DOCK_PAGE, it.col, 0, 1, 1, null, dock, 1)
            } else {
                it.page in 0 until pages && isFree(out, it.page, it.col, it.row, it.spanX, it.spanY, null, cols, rows)
            }
            if (ok) out.add(it) else deferred.add(it)
        }
        for (it in deferred) {
            val sx = it.spanX.coerceAtMost(cols)
            val sy = it.spanY.coerceAtMost(rows)
            val spot = findFreeAnyPage(out, 0, pages, sx, sy, cols, rows) ?: continue
            out.add(it.copy(page = spot.first, col = spot.second, row = spot.third, spanX = sx, spanY = sy))
        }
        return out
    }
}
