package com.glacier.launcher.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.glacier.launcher.ui.home.LauncherEnv
import kotlinx.coroutines.launch

private data class Step(val emoji: String, val title: String, val body: String)

private val steps = listOf(
    Step("🧊", "Welcome to GlacierLauncher", "A clean, fast home screen with glass panels and smooth motion. This 30-second tour covers the basics."),
    Step("👆", "Gestures", "Swipe left/right for pages, up for the App Library, down for search. Long-press an icon to open its menu, then drag to move it or drop it on another icon to make a folder."),
    Step("🧩", "Widgets", "Long-press empty space (or open Settings › Widgets) to add clock, calendar, weather, battery, notes, music, photo and device widgets, or widgets from other apps."),
    Step("🖼️", "Wallpaper & Settings", "Change the wallpaper from Settings › Personalization. Open Settings with a double-tap on empty Home space, or with the gear icon in the App Library."),
    Step("🏠", "Make it your Home app", "Android needs your approval to switch launchers. Tap the button and choose GlacierLauncher. You can change this anytime.")
)

@Composable
fun OnboardingScreen(env: LauncherEnv, onFinish: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val pager = rememberPagerState { steps.size }
    val scope = rememberCoroutineScope()
    val last = pager.currentPage == steps.lastIndex
    val isDefault by env.isDefault
    Column(Modifier.fillMaxSize().background(cs.background).statusBarsPadding().navigationBarsPadding().padding(24.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onFinish) { Text("Skip") }
        }
        HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth()) { i ->
            val st = steps[i]
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Text(st.emoji, fontSize = 72.sp)
                Spacer(Modifier.height(20.dp))
                Text(st.title, color = cs.onBackground, fontSize = 24.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                Spacer(Modifier.height(12.dp))
                Text(st.body, color = cs.onSurfaceVariant, fontSize = 16.sp, textAlign = TextAlign.Center)
                if (i == steps.lastIndex) {
                    Spacer(Modifier.height(24.dp))
                    if (isDefault) Text("GlacierLauncher is already your Home app ✓", color = cs.primary)
                    else Button(onClick = env.requestDefault) { Text("Set as Default Launcher") }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.Center) {
            repeat(steps.size) { i ->
                Box(Modifier.padding(4.dp).size(if (i == pager.currentPage) 9.dp else 7.dp).clip(CircleShape).background(if (i == pager.currentPage) cs.primary else cs.outline))
            }
        }
        Button(
            onClick = { if (last) onFinish() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } },
            modifier = Modifier.fillMaxWidth()
        ) { Text(if (last) "Get started" else "Next") }
    }
}
