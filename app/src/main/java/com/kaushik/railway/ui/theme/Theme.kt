package com.kaushik.railway.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val lightScheme = lightColorScheme(
    primary = Color(0xFF245CC5), onPrimary = Color.White,
    primaryContainer = Color(0xFFE7EEFF), onPrimaryContainer = Color(0xFF15356C),
    secondary = Color(0xFF9A5800), onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFDFA6), onSecondaryContainer = Color(0xFF382100),
    background = Color(0xFFF4F6FA), onBackground = Color(0xFF182236),
    surface = Color(0xFFFFFFFF), onSurface = Color(0xFF182236),
    surfaceVariant = Color(0xFFEDF1F7), onSurfaceVariant = Color(0xFF596579),
    outline = Color(0xFF788396), outlineVariant = Color(0xFFDCE2EC),
    error = Color(0xFFB3261E), onError = Color.White
)
private val darkScheme = darkColorScheme(
    primary = Color(0xFFABC7FF), onPrimary = Color(0xFF102D5B),
    primaryContainer = Color(0xFF213B63), onPrimaryContainer = Color(0xFFD8E5FF),
    secondary = Color(0xFFFFC875), onSecondary = Color(0xFF452B00),
    secondaryContainer = Color(0xFF59401D), onSecondaryContainer = Color(0xFFFFDFA6),
    background = Color(0xFF10141C), onBackground = Color(0xFFE6EBF4),
    surface = Color(0xFF1B222E), onSurface = Color(0xFFE6EBF4),
    surfaceVariant = Color(0xFF252F3E), onSurfaceVariant = Color(0xFFB5C0D1),
    outline = Color(0xFF8390A3), outlineVariant = Color(0xFF354152),
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005)
)

// Theme-aware aliases keep the existing screens consistent in both appearances.
val Navy: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.primary
val NavyMid: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.primary
val NavyDark: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.onSurface
val Orange: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.secondary
val OrangeDeep: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.secondary
val Cream: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.background
val CardWhite: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.surface
val Mute: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.onSurfaceVariant
val GlassFill: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.surface
val GlassStroke: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.outlineVariant
val GlassHighlight: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.surface
val Success: Color @Composable @ReadOnlyComposable get() = if (isSystemInDarkTheme()) Color(0xFF93D5AF) else Color(0xFF246C45)
val Waitlist: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.error
val Rac: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.secondary
val TricolorSaffron = Color(0xFFFF9933)
val TricolorGreen = Color(0xFF138808)

@Composable
fun RailwayTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) darkScheme else lightScheme,
        shapes = Shapes(
            small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(18.dp),
            large = RoundedCornerShape(24.dp), extraLarge = RoundedCornerShape(28.dp)
        ),
        typography = Typography(
            headlineLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 38.sp),
            headlineMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 27.sp, lineHeight = 34.sp),
            titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 21.sp, lineHeight = 28.sp),
            titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 24.sp),
            bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
            bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp),
            labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp)
        ),
        content = content
    )
}

@Composable
fun LiquidGlassBackdrop(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), content = content)
}
