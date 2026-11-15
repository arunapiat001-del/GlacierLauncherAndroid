package com.glacier.launcher.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 18.dp)) {
        Text(
            title.uppercase(), color = MaterialTheme.colorScheme.primary, fontSize = 12.sp,
            fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 6.dp, bottom = 8.dp)
        )
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp), content = content
        )
    }
}

@Composable
fun SwitchRow(title: String, subtitle: String? = null, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = MaterialTheme.colorScheme.onSurface)
            if (subtitle != null) Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** Keeps the thumb responsive and writes to storage only when released. */
@Composable
fun SliderRow(title: String, value: Float, range: ClosedFloatingPointRange<Float>, steps: Int = 0, label: (Float) -> String, onCommit: (Float) -> Unit) {
    var tmp by remember(value) { mutableFloatStateOf(value) }
    Column {
        Row(Modifier.fillMaxWidth()) {
            Text(title, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
            Text(label(tmp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Slider(value = tmp, onValueChange = { tmp = it }, valueRange = range, steps = steps, onValueChangeFinished = { onCommit(tmp) })
    }
}

@Composable
fun <T> ChoiceRow(title: String, options: List<Pair<String, T>>, selected: T, onSelect: (T) -> Unit) {
    Column {
        Text(title, color = MaterialTheme.colorScheme.onSurface)
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { (label, v) ->
                FilterChip(selected = v == selected, onClick = { onSelect(v) }, label = { Text(label) })
            }
        }
    }
}

@Composable
fun <T> DropdownRow(title: String, options: List<Pair<String, T>>, selected: T, onSelect: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        Box {
            OutlinedButton(onClick = { open = true }) { Text(options.firstOrNull { it.second == selected }?.first ?: "—") }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                options.forEach { (label, v) -> DropdownMenuItem(text = { Text(label) }, onClick = { open = false; onSelect(v) }) }
            }
        }
    }
}

@Composable
fun ButtonRow(title: String, subtitle: String? = null, button: String, enabled: Boolean = true, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 8.dp)) {
            Text(title, color = MaterialTheme.colorScheme.onSurface)
            if (subtitle != null) Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        }
        Button(onClick = onClick, enabled = enabled) { Text(button) }
    }
}
