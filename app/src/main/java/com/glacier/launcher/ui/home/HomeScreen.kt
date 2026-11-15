package com.glacier.launcher.ui.home

import android.appwidget.AppWidgetProviderInfo
import android.view.MotionEvent
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.glacier.launcher.LauncherViewModel
import com.glacier.launcher.model.*
import com.glacier.launcher.ui.components.*
import com.glacier.launcher.ui.library.AppLibraryScreen
import com.glacier.launcher.ui.library.SearchPanel
import com.glacier.launcher.ui.onboarding.OnboardingScreen
import com.glacier.launcher.ui.settings.SettingsScreen
import com.glacier.launcher.ui.theme.*
import com.glacier.launcher.ui.widgets.BuiltinWidgetView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

sealed interface Overlay {
    data object None : Overlay
    data object Library : Overlay
    data object Search : Overlay
    data object Settings : Overlay
    data object WidgetPicker : Overlay
    data class Folder(val id: String) : Overlay
}

private data class DragState(val item: HomeItem, val pos: Offset, val travel: Float = 0f)
private data class MenuState(val item: HomeItem, val anchor: Offset)
private data class MenuEntry(val label: String, val action: () -> Unit)
private class DragCallbacks(val start: (HomeItem, Offset) -> Unit, val move: (Offset) -> Unit, val end: (Boolean) -> Unit)

@Composable
fun HomeScreen(vm: LauncherViewModel, env: LauncherEnv) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val items by vm.items.collectAsStateWithLifecycle()
    val apps by vm.visibleApps.collectAsStateWithLifecycle()
    val allApps by vm.appRepo.apps.collectAsStateWithLifecycle()
    val s = settings
    if (s == null) {
        Box(Modifier.fillMaxSize())
    } else {
        GlacierTheme(s) {
            CompositionLocalProvider(
                LocalEnv provides env,
                LocalAppRepo provides vm.appRepo,
                LocalFolderStyle provides s.iconStyle
            ) {
                HomeContent(vm, s, items, apps, allApps, env)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HomeContent(
    vm: LauncherViewModel, s: LauncherSettings, items: List<HomeItem>,
    apps: List<AppEntry>, allApps: List<AppEntry>, env: LauncherEnv
) {
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val motion = LocalMotion.current
    val g = LocalGlass.current
    val appMap = remember(allApps) { allApps.associateBy { it.key } }

    var overlay by remember { mutableStateOf<Overlay>(Overlay.None) }
    var drag by remember { mutableStateOf<DragState?>(null) }
    var menu by remember { mutableStateOf<MenuState?>(null) }
    val pager = rememberPagerState { s.pages }
    var pagesBounds by remember { mutableStateOf(Rect.Zero) }
    var dockBounds by remember { mutableStateOf(Rect.Zero) }
    var rootSize by remember { mutableStateOf(IntSize.Zero) }
    val resumeScale = remember { Animatable(1f) }

    val thresholdPx = with(density) { 12.dp.toPx() }
    val gridPadPx = with(density) { s.gridPadding.dp.toPx() }
    val cellW = (pagesBounds.width - 2 * gridPadPx) / s.columns
    val cellH = pagesBounds.height / s.rows

    fun tick() { if (s.haptics) haptic.performHapticFeedback(HapticFeedbackType.LongPress) }

    fun runAction(a: GestureAction) {
        when (a) {
            GestureAction.NONE -> Unit
            GestureAction.APP_LIBRARY -> overlay = Overlay.Library
            GestureAction.SEARCH -> overlay = Overlay.Search
            GestureAction.SETTINGS -> overlay = Overlay.Settings
            GestureAction.WIDGETS -> overlay = Overlay.WidgetPicker
            GestureAction.WALLPAPER -> env.openWallpaper()
        }
    }

    fun dropAt(d: DragState) {
        val p = d.pos
        val item = d.item
        if (item.kind != ItemKind.WIDGET && dockBounds.contains(p)) {
            val slotW = dockBounds.width / s.dockCount
            val col = ((p.x - dockBounds.left) / slotW).toInt().coerceIn(0, s.dockCount - 1)
            vm.dropItem(item.id, DOCK_PAGE, col, 0)
        } else if (pagesBounds.contains(p) && cellW > 0 && cellH > 0) {
            val local = p - pagesBounds.topLeft
            val col: Int
            val row: Int
            if (item.kind == ItemKind.WIDGET) {
                // Use the widget's top-left corner (ghost is centred on the finger).
                val tlx = local.x - item.spanX * cellW / 2 - gridPadPx
                val tly = local.y - item.spanY * cellH / 2
                col = (tlx / cellW).roundToInt()
                row = (tly / cellH).roundToInt()
            } else {
                col = ((local.x - gridPadPx) / cellW).toInt()
                row = (local.y / cellH).toInt()
            }
            vm.dropItem(item.id, pager.currentPage, col, row)
        }
    }

    val callbacks = DragCallbacks(
        start = { item, pos ->
            tick()
            drag = DragState(item, pos)
            menu = MenuState(item, pos)
        },
        move = { delta ->
            drag?.let { d ->
                val nd = d.copy(pos = d.pos + delta, travel = d.travel + delta.getDistance())
                drag = nd
                if (nd.travel > thresholdPx) menu = null
            }
        },
        end = { commit ->
            val d = drag
            drag = null
            if (d != null) {
                if (!commit) menu = null
                else if (d.travel >= thresholdPx) { menu = null; dropAt(d) }
                // else: user only long-pressed -> keep the context menu open
            }
        }
    )

    LaunchedEffect(pager.currentPage) { vm.currentPage = pager.currentPage }
    LaunchedEffect(Unit) {
        vm.homePressed.collect {
            overlay = Overlay.None; menu = null
            if (pager.currentPage != 0) pager.animateScrollToPage(0)
        }
    }
    LaunchedEffect(env.resumeTick.value) {
        if (!motion.reduce) {
            resumeScale.snapTo(0.96f)
            resumeScale.animateTo(1f, motion.spring())
        }
    }

    // Edge-hover while dragging flips pages.
    val edge = drag?.let { d ->
        val m = with(density) { 40.dp.toPx() }
        when {
            d.pos.x < pagesBounds.left + m -> -1
            d.pos.x > pagesBounds.right - m -> 1
            else -> 0
        }
    } ?: 0
    LaunchedEffect(edge) {
        if (edge != 0) while (true) {
            delay(700)
            val t = (pager.currentPage + edge).coerceIn(0, s.pages - 1)
            if (t != pager.currentPage) pager.animateScrollToPage(t)
        }
    }

    val blurActive = s.blur && env.blurAvailable()
    val wantsBlur = overlay is Overlay.Library || overlay is Overlay.Search || overlay is Overlay.Folder
    LaunchedEffect(wantsBlur, s.blur) { env.setWindowBlur(if (wantsBlur && s.blur) 60 else 0) }
    DisposableEffect(Unit) { onDispose { env.setWindowBlur(0) } }

    val view = LocalView.current
    val solidOverlay = overlay is Overlay.Settings || overlay is Overlay.WidgetPicker
    val lightBars = !g.dark && overlay != Overlay.None && overlay !is Overlay.Folder
    SideEffect {
        val w = (view.context as? android.app.Activity)?.window
        if (w != null) WindowCompat.getInsetsController(w, view).isAppearanceLightStatusBars = lightBars
    }

    BackHandler(enabled = menu != null || overlay != Overlay.None) {
        if (menu != null) menu = null else overlay = Overlay.None
    }

    val depth by animateFloatAsState(if (overlay != Overlay.None) 1f else 0f, motion.spring(), label = "depth")

    Box(Modifier.fillMaxSize().onSizeChanged { rootSize = it }) {
        // ---------- Home layer ----------
        Column(
            Modifier.fillMaxSize()
                .graphicsLayer {
                    val sc = (1f - 0.06f * depth) * resumeScale.value
                    scaleX = sc; scaleY = sc; alpha = 1f - 0.4f * depth
                }
                .statusBarsPadding().navigationBarsPadding()
                .pointerInput(s.swipeUp, s.swipeDown) {
                    var total = 0f
                    detectVerticalDragGestures(
                        onDragStart = { total = 0f },
                        onDragEnd = {
                            val th = 80.dp.toPx()
                            if (total < -th) runAction(s.swipeUp) else if (total > th) runAction(s.swipeDown)
                            total = 0f
                        },
                        onDragCancel = { total = 0f }
                    ) { _, dy -> total += dy }
                }
        ) {
            Box(Modifier.weight(1f).fillMaxWidth().onGloballyPositioned { pagesBounds = it.boundsInRoot() }) {
                HorizontalPager(
                    state = pager,
                    beyondViewportPageCount = if (drag != null) s.pages else 1,
                    userScrollEnabled = drag == null,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    PageGrid(page, s, items, appMap, vm, env, drag?.item?.id, callbacks, ::runAction, ::tick) { id -> overlay = Overlay.Folder(id) }
                }
            }
            PageDots(s.pages, pager.currentPage)
            Dock(s, items, appMap, env, drag?.item?.id, callbacks, { id -> overlay = Overlay.Folder(id) }) { dockBounds = it }
        }

        // ---------- Overlays ----------
        val scrim = (if (g.dark) Navy else IcyWhite).copy(alpha = if (blurActive) 0.34f else 0.86f)

        AnimatedVisibility(
            visible = overlay is Overlay.Library,
            enter = fadeIn(motion.fade()) + slideInVertically(motion.spring()) { it / 8 } + scaleIn(motion.spring(), 0.96f),
            exit = fadeOut(motion.fade()) + slideOutVertically(motion.fade()) { it / 10 }
        ) {
            Box(Modifier.fillMaxSize().background(scrim)) {
                AppLibraryScreen(vm, s, apps, onOpenSettings = { overlay = Overlay.Settings }, onClose = { overlay = Overlay.None })
            }
        }
        AnimatedVisibility(
            visible = overlay is Overlay.Search,
            enter = fadeIn(motion.fade()) + slideInVertically(motion.spring()) { -it / 8 },
            exit = fadeOut(motion.fade())
        ) {
            Box(Modifier.fillMaxSize().background(scrim)) {
                SearchPanel(vm, s, apps) { overlay = Overlay.None }
            }
        }
        AnimatedVisibility(
            visible = overlay is Overlay.WidgetPicker,
            enter = fadeIn(motion.fade()) + slideInVertically(motion.spring()) { it / 4 },
            exit = fadeOut(motion.fade()) + slideOutVertically(motion.fade()) { it / 6 }
        ) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                WidgetPicker(vm, env) { overlay = Overlay.None }
            }
        }
        AnimatedVisibility(
            visible = overlay is Overlay.Settings,
            enter = fadeIn(motion.fade()) + slideInVertically(motion.spring()) { it / 4 },
            exit = fadeOut(motion.fade()) + slideOutVertically(motion.fade()) { it / 6 }
        ) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                SettingsScreen(vm, s, items, allApps, env, onClose = { overlay = Overlay.None }, onAddWidget = { overlay = Overlay.WidgetPicker })
            }
        }
        AnimatedVisibility(
            visible = overlay is Overlay.Folder,
            enter = fadeIn(motion.fade()) + scaleIn(motion.spring(), 0.7f),
            exit = fadeOut(motion.fade()) + scaleOut(motion.fade(), 0.8f)
        ) {
            val id = (overlay as? Overlay.Folder)?.id
            val folder = items.firstOrNull { it.id == id }
            if (folder == null) LaunchedEffect(Unit) { overlay = Overlay.None }
            else FolderOverlay(folder, appMap, s, vm, env) { overlay = Overlay.None }
        }

        // ---------- Drag ghost + context menu ----------
        drag?.let { d ->
            val sw = if (d.item.kind == ItemKind.WIDGET) cellW * d.item.spanX else cellW
            val sh = if (d.item.kind == ItemKind.WIDGET) cellH * d.item.spanY else cellH
            if (sw > 0 && sh > 0) {
                Box(
                    Modifier
                        .offset { IntOffset((d.pos.x - sw / 2).roundToInt(), (d.pos.y - sh / 2).roundToInt()) }
                        .size(with(density) { sw.toDp() }, with(density) { sh.toDp() })
                        .graphicsLayer { scaleX = 1.08f; scaleY = 1.08f; alpha = 0.93f },
                    contentAlignment = Alignment.Center
                ) {
                    if (d.item.kind == ItemKind.WIDGET) Box(Modifier.fillMaxSize().padding(4.dp).glass(strength = 1.2f))
                    else IconVisual(d.item, appMap, s, s.showLabels && d.item.page != DOCK_PAGE)
                }
            }
        }

        menu?.let { m ->
            val entries = buildMenu(m.item, appMap, vm, env,
                openFolder = { overlay = Overlay.Folder(m.item.id) })
            Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { menu = null } })
            ContextMenu(m, entries, rootSize, cellH) { menu = null }
        }

        // ---------- First-run onboarding ----------
        AnimatedVisibility(visible = !s.onboarded, enter = fadeIn(), exit = fadeOut(motion.fade())) {
            OnboardingScreen(env, onFinish = { vm.finishOnboarding() })
        }
    }
}

private fun buildMenu(item: HomeItem, appMap: Map<String, AppEntry>, vm: LauncherViewModel, env: LauncherEnv, openFolder: () -> Unit): List<MenuEntry> =
    when (item.kind) {
        ItemKind.APP -> {
            val e = appMap[item.appKey]
            listOf(
                MenuEntry("App info") { if (e != null) env.appInfo(e.packageName) },
                MenuEntry("Remove from Home") { vm.removeItem(item.id) },
                MenuEntry("Hide app") { if (e != null) vm.setHidden(e.key, true) },
                MenuEntry("Uninstall") { if (e != null) env.uninstall(e.packageName) }
            )
        }
        ItemKind.FOLDER -> listOf(
            MenuEntry("Open / rename") { openFolder() },
            MenuEntry("Dissolve folder") { vm.dissolveFolder(item.id) }
        )
        ItemKind.WIDGET -> listOf(
            MenuEntry("Small") { vm.resizeWidget(item.id, 0) },
            MenuEntry("Medium") { vm.resizeWidget(item.id, 1) },
            MenuEntry("Large") { vm.resizeWidget(item.id, 2) },
            MenuEntry("Remove widget") {
                if (item.hostWidgetId >= 0) env.widgets.delete(item.hostWidgetId)
                vm.removeItem(item.id)
            }
        )
    }

@Composable
private fun ContextMenu(m: MenuState, entries: List<MenuEntry>, rootSize: IntSize, cellH: Float, dismiss: () -> Unit) {
    val g = LocalGlass.current
    val density = LocalDensity.current
    val w = 210.dp
    val h = (entries.size * 44 + 12).dp
    val wPx = with(density) { w.toPx() }
    val hPx = with(density) { h.toPx() }
    val margin = with(density) { 12.dp.toPx() }
    val x = (m.anchor.x - wPx / 2).coerceIn(margin, (rootSize.width - wPx - margin).coerceAtLeast(margin))
    var y = m.anchor.y - cellH / 2 - hPx - margin
    if (y < margin * 4) y = m.anchor.y + cellH / 2 + margin
    y = y.coerceIn(margin, (rootSize.height - hPx - margin * 3).coerceAtLeast(margin))
    Column(
        Modifier.offset { IntOffset(x.roundToInt(), y.roundToInt()) }.width(w)
            .glass(RoundedCornerShape(18.dp), strength = 1.5f).padding(vertical = 6.dp)
    ) {
        entries.forEach { e ->
            Box(
                Modifier.fillMaxWidth().height(44.dp)
                    .pointerInput(e.label) { detectTapGestures { dismiss(); e.action() } }
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterStart
            ) { Text(e.label, color = g.onGlass, fontSize = 15.sp) }
        }
    }
}

@Composable
private fun PageGrid(
    page: Int, s: LauncherSettings, items: List<HomeItem>, appMap: Map<String, AppEntry>,
    vm: LauncherViewModel, env: LauncherEnv, draggingId: String?, cb: DragCallbacks,
    runAction: (GestureAction) -> Unit, tick: () -> Unit, openFolder: (String) -> Unit
) {
    BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = s.gridPadding.dp)) {
        val cw = maxWidth / s.columns
        val ch = maxHeight / s.rows
        // Empty-area gestures live in a layer *under* the items so icons never trigger them.
        Box(
            Modifier.fillMaxSize().pointerInput(s.longPress, s.doubleTap) {
                detectTapGestures(
                    onDoubleTap = { runAction(s.doubleTap) },
                    onLongPress = { if (s.longPress != GestureAction.NONE) { tick(); runAction(s.longPress) } }
                )
            }
        )
        items.filter { it.page == page }.forEach { item ->
            key(item.id) {
                Box(Modifier.offset(cw * item.col, ch * item.row).size(cw * item.spanX, ch * item.spanY)) {
                    if (item.kind == ItemKind.WIDGET) {
                        WidgetCell(item, s, vm, env, cw * item.spanX - 8.dp, ch * item.spanY - 8.dp, item.id == draggingId, cb)
                    } else {
                        DraggableIcon(
                            item, appMap, s, s.showLabels, item.id == draggingId, Modifier.fillMaxSize(), cb,
                            onTap = { r -> openItem(item, appMap, env, r, openFolder) }
                        )
                    }
                }
            }
        }
    }
}

private fun openItem(item: HomeItem, appMap: Map<String, AppEntry>, env: LauncherEnv, rect: Rect, openFolder: (String) -> Unit) {
    when (item.kind) {
        ItemKind.APP -> appMap[item.appKey]?.let { env.launchApp(it, rect) }
        ItemKind.FOLDER -> openFolder(item.id)
        ItemKind.WIDGET -> Unit
    }
}

@Composable
private fun DraggableIcon(
    item: HomeItem, appMap: Map<String, AppEntry>, s: LauncherSettings, showLabel: Boolean,
    hidden: Boolean, modifier: Modifier, cb: DragCallbacks, onTap: (Rect) -> Unit
) {
    var coords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (pressed) 0.9f else 1f, LocalMotion.current.spring(), label = "press")
    val cbs by rememberUpdatedState(cb)
    val tap by rememberUpdatedState(onTap)
    Box(
        modifier
            .onGloballyPositioned { coords = it }
            .graphicsLayer { scaleX = scale; scaleY = scale; alpha = if (hidden) 0f else 1f }
            .pointerInput(item.id) {
                detectTapGestures(
                    onPress = { pressed = true; tryAwaitRelease(); pressed = false },
                    onTap = { coords?.let { tap(it.boundsInRoot()) } }
                )
            }
            .pointerInput(item.id) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { local -> coords?.let { cbs.start(item, it.positionInRoot() + local) } },
                    onDrag = { change, amount -> change.consume(); cbs.move(amount) },
                    onDragEnd = { cbs.end(true) },
                    onDragCancel = { cbs.end(false) }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        IconVisual(item, appMap, s, showLabel && item.page != DOCK_PAGE)
    }
}

@Composable
private fun WidgetCell(item: HomeItem, s: LauncherSettings, vm: LauncherViewModel, env: LauncherEnv, w: Dp, h: Dp, hidden: Boolean, cb: DragCallbacks) {
    var coords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val cbs by rememberUpdatedState(cb)
    Box(
        Modifier.fillMaxSize().padding(4.dp)
            .onGloballyPositioned { coords = it }
            .graphicsLayer { alpha = if (hidden) 0f else 1f }
    ) {
        val builtin = item.builtin?.let { n -> runCatching { BuiltinWidget.valueOf(n) }.getOrNull() }
        if (builtin != null) {
            Box(
                Modifier.fillMaxSize().pointerInput(item.id) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { local -> coords?.let { cbs.start(item, it.positionInRoot() + local) } },
                        onDrag = { change, amount -> change.consume(); cbs.move(amount) },
                        onDragEnd = { cbs.end(true) },
                        onDragCancel = { cbs.end(false) }
                    )
                }
            ) { BuiltinWidgetView(builtin, item.spanX, item.spanY, vm, s) }
        } else if (item.hostWidgetId >= 0) {
            HostWidget(item, env, w, h, cbs)
        }
    }
}

/**
 * Widget from another app (RemoteViews rendered by AppWidgetHostView).
 * Long-press on its surface starts a drag, driven by the raw touch events of the view.
 */
@Composable
private fun HostWidget(item: HomeItem, env: LauncherEnv, w: Dp, h: Dp, cb: DragCallbacks) {
    val g = LocalGlass.current
    val view = remember(item.hostWidgetId) { env.widgets.createView(item.hostWidgetId) }
    val cbs by rememberUpdatedState(cb)
    if (view == null) {
        Box(Modifier.fillMaxSize().glass().padding(12.dp), contentAlignment = Alignment.Center) {
            Text("Widget unavailable (its app may have been removed)", color = g.onGlassDim, fontSize = 12.sp, textAlign = TextAlign.Center)
        }
        return
    }
    AndroidView(
        factory = {
            (view.parent as? ViewGroup)?.removeView(view)
            val last = floatArrayOf(0f, 0f)
            val dragging = booleanArrayOf(false)
            view.setOnTouchListener { v, ev ->
                val loc = IntArray(2).also { v.rootView.getLocationOnScreen(it) }
                val x = ev.rawX - loc[0]
                val y = ev.rawY - loc[1]
                var consumed = false
                if (dragging[0]) {
                    when (ev.actionMasked) {
                        MotionEvent.ACTION_MOVE -> cbs.move(Offset(x - last[0], y - last[1]))
                        MotionEvent.ACTION_UP -> { dragging[0] = false; cbs.end(true) }
                        MotionEvent.ACTION_CANCEL -> { dragging[0] = false; cbs.end(false) }
                    }
                    consumed = true
                }
                last[0] = x; last[1] = y
                consumed
            }
            view.setOnLongClickListener {
                dragging[0] = true
                view.parent?.requestDisallowInterceptTouchEvent(true)
                cbs.start(item, Offset(last[0], last[1]))
                true
            }
            view
        },
        update = { v -> v.updateAppWidgetSize(null, w.value.toInt(), h.value.toInt(), w.value.toInt(), h.value.toInt()) },
        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(22.dp))
    )
}

@Composable
private fun PageDots(count: Int, current: Int) {
    if (count <= 1) return
    val g = LocalGlass.current
    Row(Modifier.fillMaxWidth().height(20.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        for (i in 0 until count) {
            val sel = i == current
            val size by animateDpAsState(if (sel) 8.dp else 6.dp, LocalMotion.current.spring(), label = "dot")
            Box(Modifier.padding(horizontal = 4.dp).size(size).clip(CircleShape).background(Color.White.copy(alpha = if (sel) 0.95f else 0.45f)))
        }
    }
}

@Composable
private fun Dock(
    s: LauncherSettings, items: List<HomeItem>, appMap: Map<String, AppEntry>, env: LauncherEnv,
    draggingId: String?, cb: DragCallbacks, openFolder: (String) -> Unit, onBounds: (Rect) -> Unit
) {
    Row(
        Modifier.padding(horizontal = 14.dp, vertical = 6.dp).fillMaxWidth()
            .height((s.iconSize + 26).dp)
            .onGloballyPositioned { onBounds(it.boundsInRoot()) }
            .glass(RoundedCornerShape((s.corner + 4).dp), strength = 1.1f),
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (slot in 0 until s.dockCount) {
            val item = items.firstOrNull { it.page == DOCK_PAGE && it.col == slot }
            Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                if (item != null) {
                    DraggableIcon(
                        item, appMap, s, false, item.id == draggingId, Modifier.fillMaxSize(), cb,
                        onTap = { r -> openItem(item, appMap, env, r, openFolder) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FolderOverlay(folder: HomeItem, appMap: Map<String, AppEntry>, s: LauncherSettings, vm: LauncherViewModel, env: LauncherEnv, onClose: () -> Unit) {
    val g = LocalGlass.current
    var title by remember(folder.id) { mutableStateOf(folder.title) }
    LaunchedEffect(title) { delay(400); if (title != folder.title) vm.renameFolder(folder.id, title) }
    val apps = folder.children.mapNotNull { appMap[it] }
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.28f)).pointerInput(Unit) { detectTapGestures { onClose() } },
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier.padding(24.dp).fillMaxWidth().glass(RoundedCornerShape(34.dp), strength = 1.3f)
                .pointerInput(Unit) { detectTapGestures { } }.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            BasicTextField(
                value = title, onValueChange = { title = it.take(24) }, singleLine = true,
                textStyle = TextStyle(color = g.onGlass, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center),
                cursorBrush = SolidColor(g.accent), modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            apps.chunked(3).forEach { row ->
                Row(Modifier.fillMaxWidth()) {
                    row.forEach { app ->
                        var menuOpen by remember { mutableStateOf(false) }
                        var bounds by remember { mutableStateOf<Rect?>(null) }
                        Box(Modifier.weight(1f)) {
                            Column(
                                Modifier.fillMaxWidth().onGloballyPositioned { bounds = it.boundsInRoot() }
                                    .combinedClickable(onClick = { env.launchApp(app, bounds); onClose() }, onLongClick = { menuOpen = true })
                                    .padding(vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                AppIconImage(app.key, s.iconSize.dp, s.iconStyle)
                                Spacer(Modifier.height(4.dp))
                                Text(app.label, color = g.onGlass, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                DropdownMenuItem(text = { Text("Remove from folder") }, onClick = { menuOpen = false; vm.removeFromFolder(folder.id, app.key) })
                            }
                        }
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun WidgetPicker(vm: LauncherViewModel, env: LauncherEnv, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val providers by produceState<List<Pair<AppWidgetProviderInfo, String>>>(emptyList()) {
        value = withContext(Dispatchers.IO) {
            val pm = ctx.packageManager
            env.widgets.providers().map { p ->
                val label = runCatching { p.loadLabel(pm) }.getOrNull().orEmpty().ifBlank { p.provider.className.substringAfterLast('.') }
                val app = runCatching { pm.getApplicationLabel(pm.getApplicationInfo(p.provider.packageName, 0)).toString() }.getOrDefault(p.provider.packageName)
                p to "$app · $label"
            }.sortedBy { it.second.lowercase() }
        }
    }
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = cs.onBackground) }
            Text("Add widget", color = cs.onBackground, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { Text("GLACIERLAUNCHER", color = cs.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
            items(BuiltinWidget.entries.toList()) { t ->
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(cs.surfaceVariant.copy(alpha = 0.6f)).padding(14.dp)) {
                    Text(t.title, color = cs.onSurface, fontWeight = FontWeight.SemiBold)
                    Text(t.description, color = cs.onSurfaceVariant, fontSize = 12.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Small", "Medium", "Large").forEachIndexed { i, label ->
                            TextButton(onClick = { if (vm.addBuiltinWidget(t, i)) onClose() }) { Text(label) }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(6.dp)); Text("FROM OTHER APPS", color = cs.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
            if (providers.isEmpty()) item { Text("No widgets from other apps were found.", color = cs.onSurfaceVariant, fontSize = 13.sp) }
            items(providers, key = { it.first.provider.flattenToString() }) { (info, label) ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(cs.surfaceVariant.copy(alpha = 0.6f))
                        .pointerInput(info) { detectTapGestures { env.widgets.request(info); onClose() } }.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(label, color = cs.onSurface, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text("Add", color = cs.primary, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
