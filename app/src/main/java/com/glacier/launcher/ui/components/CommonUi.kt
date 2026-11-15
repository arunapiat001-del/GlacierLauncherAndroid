package com.glacier.launcher.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.glacier.launcher.data.AppRepository
import com.glacier.launcher.model.*
import com.glacier.launcher.ui.theme.glass

val LocalAppRepo = staticCompositionLocalOf<AppRepository> { error("AppRepository not provided") }

/** The real system icon of an app, loaded lazily. Falls back to a neutral tile if loading fails. */
@Composable
fun AppIconImage(key: String, size: Dp, style: Int, modifier: Modifier = Modifier) {
    val repo = LocalAppRepo.current
    val bmp by produceState(initialValue = repo.cachedIcon(key), key) {
        if (value == null) value = repo.loadIcon(key)
    }
    val shape = when (style) {
        1 -> CircleShape
        0 -> RoundedCornerShape(size * 0.24f)
        else -> RoundedCornerShape(0.dp)
    }
    Box(modifier.size(size).clip(shape)) {
        val b = bmp
        if (b != null) Image(b, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
        else Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.18f)))
    }
}

val HomeLabelStyle = TextStyle(
    color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center,
    shadow = Shadow(Color(0x99000000), Offset(0f, 1f), 4f)
)

/** Visual only (no gestures). Used both in the grid and as the drag ghost. */
@Composable
fun IconVisual(item: HomeItem, appMap: Map<String, AppEntry>, s: LauncherSettings, showLabel: Boolean, modifier: Modifier = Modifier) {
    val size = s.iconSize.dp
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        when (item.kind) {
            ItemKind.APP -> {
                val key = item.appKey
                if (key != null) AppIconImage(key, size, s.iconStyle) else Spacer(Modifier.size(size))
            }
            ItemKind.FOLDER -> FolderGlyph(item, size)
            ItemKind.WIDGET -> Unit
        }
        if (showLabel) {
            val label = when (item.kind) {
                ItemKind.APP -> appMap[item.appKey]?.label.orEmpty()
                ItemKind.FOLDER -> item.title
                ItemKind.WIDGET -> ""
            }
            Spacer(Modifier.height(4.dp))
            Text(label, style = HomeLabelStyle, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = size + 24.dp))
        }
    }
}

/** 2x2 mini-icons on a glass tile. */
@Composable
fun FolderGlyph(item: HomeItem, size: Dp) {
    val s = LocalFolderStyle.current
    Box(Modifier.size(size).glass(RoundedCornerShape(size * 0.24f), strength = 1f, elevated = false).padding(size * 0.1f)) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceEvenly) {
            for (r in 0..1) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                for (c in 0..1) {
                    val key = item.children.getOrNull(r * 2 + c)
                    val mini = size * 0.36f
                    if (key != null) AppIconImage(key, mini, s) else Spacer(Modifier.size(mini))
                }
            }
        }
    }
}

/** Icon style used inside folder glyphs. */
val LocalFolderStyle = compositionLocalOf { 0 }
