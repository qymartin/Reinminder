package io.github.qymartin.reinminder.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import java.time.LocalDateTime

// The CrossSync palette (dark Material 3).
val Bg = Color(0xFF111318)
val CardBg = Color(0xFF1E2128)
val NavBg = Color(0xFF1A1D24)
val InputBg = Color(0xFF15171C)
val Accent = Color(0xFFA8C7FA)
val OnAccent = Color(0xFF0B2E5A)
val SelectedBg = Color(0xFF3B4A6B)
val TonalBg = Color(0xFF2A3550)
val OnTonal = Color(0xFFD3E3FD)
val Text1 = Color(0xFFE3E3E8)
val Text2 = Color(0xFFB9BDC8)
val Text3 = Color(0xFFA9ADB8)
val Muted = Color(0xFF8A90A0)
val DividerColor = Color(0xFF2A2E37)
val OutlineColor = Color(0xFF5A6070)
val Amber = Color(0xFFF2A65A)
val AmberBg = Color(0xFF2A2620)
val AmberBorder = Color(0xFF5A4630)
val AmberText = Color(0xFFC9BBA8)
val Purple = Color(0xFFD59BF0)

private val scheme = darkColorScheme(
    primary = Accent,
    onPrimary = OnAccent,
    primaryContainer = SelectedBg,
    onPrimaryContainer = OnTonal,
    secondary = Accent,
    onSecondary = OnAccent,
    secondaryContainer = SelectedBg,
    onSecondaryContainer = OnTonal,
    tertiary = Amber,
    onTertiary = Color(0xFF2A1A08),
    background = Bg,
    onBackground = Text1,
    surface = Bg,
    onSurface = Text1,
    surfaceVariant = CardBg,
    onSurfaceVariant = Text2,
    outline = OutlineColor,
    outlineVariant = DividerColor,
    surfaceContainerLowest = Bg,
    surfaceContainerLow = CardBg,
    surfaceContainer = NavBg,
    surfaceContainerHigh = CardBg,
    surfaceContainerHighest = CardBg,
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, content = content)
}

/** App language (true = English), read once per activity; changing it recreates the activity. */
val LocalEn = compositionLocalOf { false }

/** "Now", refreshed every 30 seconds so overdue states update. */
val LocalNow = compositionLocalOf { LocalDateTime.now() }

/** Topic id -> name, for rows and details. */
val LocalTopics = compositionLocalOf { emptyMap<String, String>() }

fun Map<String, String>.nameOf(id: String?): String? = id?.let { this[it] }

/** Task id -> title, so a subtask can show its main task's name. */
val LocalTitles = compositionLocalOf { emptyMap<Long, String>() }

fun Map<Long, String>.parentOf(e: io.github.qymartin.reinminder.data.Event): String? = e.parentId?.let { this[it] }
