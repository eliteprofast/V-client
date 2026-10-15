package com.vclient.android.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape

// Same palette as the website and the Windows launcher: midnight navy with gold highlights.
val Midnight = Color(0xFF080B16)
val Panel = Color(0xFF101525)
val PanelLight = Color(0xFF151C2F)
val Gold = Color(0xFFE7BF68)
val GoldPale = Color(0xFFFFE4A1)
val Ink = Color(0xFFF5F4F0)
val Muted = Color(0xFF9DA6B9)
val Online = Color(0xFF6FD39B)
val Offline = Color(0xFFE5626F)
val Info = Color(0xFF17315A)
val Warning = Color(0xFF4A3A12)
val Critical = Color(0xFF5A1C26)

private val VClientColors = darkColorScheme(
    primary = Gold,
    onPrimary = Color(0xFF151522),
    primaryContainer = Color(0xFF3A3017),
    onPrimaryContainer = GoldPale,
    secondary = Online,
    onSecondary = Color(0xFF0D1A14),
    background = Midnight,
    onBackground = Ink,
    surface = Panel,
    onSurface = Ink,
    surfaceVariant = PanelLight,
    onSurfaceVariant = Muted,
    surfaceContainer = Panel,
    surfaceContainerHigh = PanelLight,
    outline = Color(0xFF2A3350),
    outlineVariant = Color(0xFF1D2540),
    error = Offline,
)

private val VClientTypography = Typography(
    headlineMedium = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 15.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp),
)

private val VClientShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
)

@Composable
fun VClientTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = VClientColors,
        typography = VClientTypography,
        shapes = VClientShapes,
        content = content,
    )
}
