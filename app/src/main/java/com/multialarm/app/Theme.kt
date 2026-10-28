package com.multialarm.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val scheme = darkColorScheme(
    primary = Color(0xFFA8C7FA),
    onPrimary = Color(0xFF062E6F),
    background = Color(0xFF131314),
    surface = Color(0xFF131314),
    surfaceVariant = Color(0xFF2B2D30),
    onSurface = Color(0xFFE3E3E3),
    onSurfaceVariant = Color(0xFFA9ABAF)
)

@Composable
fun AlarmTheme(content: @Composable () -> Unit) =
    MaterialTheme(colorScheme = scheme, content = content)
