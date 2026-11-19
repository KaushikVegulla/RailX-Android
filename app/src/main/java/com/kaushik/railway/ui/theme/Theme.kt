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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import com.kaushik.railway.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/** 0 = fully tinted glass, 1 = ultra-clear glass. Set from Settings. */
val LocalGlassIntensity = compositionLocalOf { 0.55f }

// Inter, fetched at runtime from the Google Fonts provider (falls back to
// the system sans if the provider is unavailable). SF-style scale: tight
// tracking on large titles, generous line heights, semibold emphasis.
private val InterProvider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs
)
private val InterFont = GoogleFont("Inter")
private val AppFontFamily = FontFamily(
    Font(InterFont, InterProvider, FontWeight.Normal),
    Font(InterFont, InterProvider, FontWeight.Medium),
    Font(InterFont, InterProvider, FontWeight.SemiBold),
    Font(InterFont, InterProvider, FontWeight.Bold),
    Font(InterFont, InterProvider, FontWeight.ExtraBold)
)

private fun typeStyle(
    size: Int,
    weight: FontWeight,
    lineHeight: Int,
    tracking: Float = 0f
) = TextStyle(
    fontFamily = AppFontFamily,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = tracking.em
)

private val AppTypography = Typography(
    displayLarge = typeStyle(40, FontWeight.Bold, 46, -0.02f),
    displayMedium = typeStyle(34, FontWeight.Bold, 40, -0.02f),
    displaySmall = typeStyle(30, FontWeight.Bold, 36, -0.015f),
    headlineLarge = typeStyle(30, FontWeight.Bold, 36, -0.015f),
    headlineMedium = typeStyle(26, FontWeight.Bold, 32, -0.015f),
    headlineSmall = typeStyle(22, FontWeight.SemiBold, 28, -0.01f),
    titleLarge = typeStyle(21, FontWeight.SemiBold, 27, -0.01f),
    titleMedium = typeStyle(17, FontWeight.SemiBold, 23, -0.005f),
    titleSmall = typeStyle(15, FontWeight.SemiBold, 20, -0.005f),
    bodyLarge = typeStyle(16, FontWeight.Normal, 23),
    bodyMedium = typeStyle(14, FontWeight.Normal, 20),
    bodySmall = typeStyle(12, FontWeight.Normal, 17),
    labelLarge = typeStyle(15, FontWeight.SemiBold, 20),
    labelMedium = typeStyle(13, FontWeight.Medium, 18),
    labelSmall = typeStyle(11, FontWeight.Medium, 15)
)

private val lightScheme = lightColorScheme(
    primary = Color(0xFF0A5BD7), onPrimary = Color.White,
    primaryContainer = Color(0xFFDCEBFF), onPrimaryContainer = Color(0xFF0A2E63),
    secondary = Color(0xFFDD7A06), onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE3B8), onSecondaryContainer = Color(0xFF4A2A00),
    background = Color(0xFFEDF0F6), onBackground = Color(0xFF0B1220),
    surface = Color(0xFFFFFFFF), onSurface = Color(0xFF0B1220),
    surfaceVariant = Color(0xFFE4EAF3), onSurfaceVariant = Color(0xFF54657E),
    outline = Color(0xFF8494AC), outlineVariant = Color(0xFFD3DCE9),
    error = Color(0xFFD70015), onError = Color.White
)
private val darkScheme = darkColorScheme(
    primary = Color(0xFF57A5FF), onPrimary = Color(0xFF06264D),
    primaryContainer = Color(0xFF14335F), onPrimaryContainer = Color(0xFFD6E9FF),
    secondary = Color(0xFFFFB340), onSecondary = Color(0xFF452B00),
    secondaryContainer = Color(0xFF59401D), onSecondaryContainer = Color(0xFFFFDFA6),
    background = Color(0xFF000000), onBackground = Color(0xFFEDF2FA),
    surface = Color(0xFF1C1C1E), onSurface = Color(0xFFEDF2FA),
    surfaceVariant = Color(0xFF1C2536), onSurfaceVariant = Color(0xFFA7B4C8),
    outline = Color(0xFF5A6B84), outlineVariant = Color(0xFF2A3446),
    error = Color(0xFFFF6961), onError = Color(0xFF690005)
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

/** Translucent glass fill; alpha follows the user's glass intensity. */
val GlassFill: Color
    @Composable @ReadOnlyComposable
    get() {
        val intensity = LocalGlassIntensity.current
        return MaterialTheme.colorScheme.surface.copy(alpha = 0.94f - 0.55f * intensity)
    }

/** Bright glass edge stroke; stronger when the glass is clearer. */
val GlassStroke: Color
    @Composable @ReadOnlyComposable
    get() {
        val intensity = LocalGlassIntensity.current
        val dark = MaterialTheme.colorScheme.background.luminance() < 0.3f
        return if (dark) {
            Color.White.copy(alpha = 0.10f + 0.22f * intensity)
        } else {
            Color.White.copy(alpha = 0.45f + 0.40f * intensity)
        }
    }

/** Top sheen highlight for glass surfaces. */
val GlassHighlight: Color
    @Composable @ReadOnlyComposable
    get() {
        val intensity = LocalGlassIntensity.current
        val dark = MaterialTheme.colorScheme.background.luminance() < 0.3f
        return Color.White.copy(alpha = if (dark) 0.06f + 0.10f * intensity else 0.28f + 0.35f * intensity)
    }

val Success: Color @Composable @ReadOnlyComposable get() = if (isSystemInDarkTheme()) Color(0xFF93D5AF) else Color(0xFF1F7A44)
val Waitlist: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.error
val Rac: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.secondary
val TricolorSaffron = Color(0xFFFF9933)
val TricolorGreen = Color(0xFF138808)

@Composable
fun RailwayTheme(
    glassIntensity: Float = 0.55f,
    darkOverride: Boolean? = null,
    content: @Composable () -> Unit
) {
    val dark = darkOverride ?: isSystemInDarkTheme()
    CompositionLocalProvider(LocalGlassIntensity provides glassIntensity.coerceIn(0f, 1f)) {
        MaterialTheme(
            colorScheme = if (dark) darkScheme else lightScheme,
            shapes = Shapes(
                small = RoundedCornerShape(16.dp), medium = RoundedCornerShape(22.dp),
                large = RoundedCornerShape(28.dp), extraLarge = RoundedCornerShape(36.dp)
            ),
            typography = AppTypography,
            content = content
        )
    }
}

/**
 * Backdrop the glass sits on: one smooth vertical gradient and nothing
 * else. The earlier azure + saffron glow orbs left a visible seam and an
 * orange/blue split behind the screens (flagged 19 Nov 2026), so the
 * colour glows are gone; the glass tint comes from the surfaces
 * themselves.
 */
@Composable
fun LiquidGlassBackdrop(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.background.luminance() < 0.3f
    Box(
        modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    if (dark) {
                        listOf(Color(0xFF0C0D12), Color(0xFF000000), Color(0xFF000000))
                    } else {
                        listOf(Color(0xFFF4F7FC), Color(0xFFEDF0F6), Color(0xFFE3EAF4))
                    }
                )
            )
    ) {
        content()
    }
}
