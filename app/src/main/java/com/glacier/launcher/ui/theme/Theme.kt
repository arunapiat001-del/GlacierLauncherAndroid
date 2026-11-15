package com.glacier.launcher.ui.theme

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.glacier.launcher.model.LauncherSettings
import com.glacier.launcher.model.ThemeMode

// GlacierLauncher palette: icy blue, white, soft grey, dark navy.
val Navy = Color(0xFF0B1B33)
val NavyLight = Color(0xFF16305A)
val IcyWhite = Color(0xFFF2F8FF)
val AccentPalette = listOf(
    Color(0xFF7CC8F2), // Glacier (icy blue)
    Color(0xFF4F8CFF), // Cobalt
    Color(0xFF8E7CF5), // Violet
    Color(0xFF3FD5C3), // Aqua
    Color(0xFFFF8FB1), // Rose
    Color(0xFFFFB84D)  // Amber
)
val AccentNames = listOf("Glacier", "Cobalt", "Violet", "Aqua", "Rose", "Amber")

@Immutable
data class GlassStyle(val intensity: Float, val corner: Dp, val dark: Boolean, val accent: Color) {
    val onGlass: Color get() = if (dark) Color.White else Navy
    val onGlassDim: Color get() = if (dark) Color(0xCCFFFFFF) else Color(0xB30B1B33)
}

val LocalGlass = staticCompositionLocalOf { GlassStyle(0.6f, 24.dp, true, AccentPalette[0]) }

/** Animation tuning from Settings. Reduce Motion makes every transition instant. */
class Motion(val reduce: Boolean, val speed: Float, val bounce: Float) {
    fun <T> spring(): FiniteAnimationSpec<T> =
        if (reduce) snap() else androidx.compose.animation.core.spring<T>(dampingRatio = 0.9f - 0.35f * bounce, stiffness = 380f * speed)

    fun <T> fade(): FiniteAnimationSpec<T> =
        if (reduce) snap() else tween((220 / speed).toInt().coerceAtLeast(60))
}

val LocalMotion = staticCompositionLocalOf { Motion(false, 1f, 0.5f) }

@Composable
fun GlacierTheme(s: LauncherSettings, content: @Composable () -> Unit) {
    val dark = when (s.themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val accent = AccentPalette[s.accent.coerceIn(0, AccentPalette.lastIndex)]
    val scheme = if (dark) {
        darkColorScheme(
            primary = accent, onPrimary = Navy, secondary = accent, onSecondary = Navy,
            background = Navy, onBackground = Color.White,
            surface = Color(0xFF10264A), onSurface = Color.White,
            surfaceVariant = Color(0xFF1B3763), onSurfaceVariant = Color(0xFFC6D6EC),
            outline = Color(0x66FFFFFF)
        )
    } else {
        lightColorScheme(
            primary = lerp(accent, Navy, 0.35f), onPrimary = Color.White,
            secondary = lerp(accent, Navy, 0.35f), onSecondary = Color.White,
            background = IcyWhite, onBackground = Navy,
            surface = Color.White, onSurface = Navy,
            surfaceVariant = Color(0xFFE3EEF9), onSurfaceVariant = Color(0xFF3B4D68),
            outline = Color(0x660B1B33)
        )
    }
    val glass = remember(s.glass, s.corner, dark, accent) { GlassStyle(s.glass, s.corner.dp, dark, accent) }
    val motion = remember(s.reduceMotion, s.animSpeed, s.animBounce) { Motion(s.reduceMotion, s.animSpeed, s.animBounce) }
    CompositionLocalProvider(LocalGlass provides glass, LocalMotion provides motion) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}

/**
 * Glass panel: translucent gradient fill, thin highlight border, soft shadow.
 * Real backdrop blur of the wallpaper is applied at window level while overlays are open
 * (Android 12+). Individual panels stay cheap: translucency only.
 */
@Composable
fun Modifier.glass(shape: Shape? = null, strength: Float = 1f, elevated: Boolean = true): Modifier {
    val g = LocalGlass.current
    val sh = shape ?: RoundedCornerShape(g.corner)
    val base = if (g.dark) NavyLight else Color.White
    val a = ((0.16f + 0.5f * g.intensity) * strength).coerceIn(0.08f, 0.92f)
    val fill = Brush.verticalGradient(listOf(base.copy(alpha = (a * 1.2f).coerceAtMost(0.95f)), base.copy(alpha = a * 0.8f)))
    val edge = Brush.verticalGradient(
        listOf(Color.White.copy(alpha = if (g.dark) 0.45f else 0.85f), Color.White.copy(alpha = 0.10f))
    )
    return this
        .then(if (elevated) Modifier.shadow(8.dp, sh, clip = false, ambientColor = Color(0x33000000), spotColor = Color(0x33000000)) else Modifier)
        .clip(sh)
        .background(fill)
        .border(0.8.dp, edge, sh)
}
