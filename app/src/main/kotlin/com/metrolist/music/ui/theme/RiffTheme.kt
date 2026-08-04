package com.metrolist.music.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.ExperimentalTextApi
import com.metrolist.music.R

/**
 * Register each weight as a real variable-font instance. Wrapping the XML font
 * family in a single Font() descriptor made Compose treat the whole family as a
 * 400 face and synthesize every requested weight from it.
 */
@OptIn(ExperimentalTextApi::class)
private fun variableFont(
    resourceId: Int,
    weight: Int,
) = Font(
    resId = resourceId,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

val RiffGeneralSans = FontFamily(
    variableFont(R.font.general_sans_variable, 300),
    variableFont(R.font.general_sans_variable, 400),
    variableFont(R.font.general_sans_variable, 500),
    variableFont(R.font.general_sans_variable, 550),
    variableFont(R.font.general_sans_variable, 600),
    variableFont(R.font.general_sans_variable, 700),
)

val RiffAzeretMono = FontFamily(
    variableFont(R.font.azeret_mono_variable, 300),
    variableFont(R.font.azeret_mono_variable, 400),
    variableFont(R.font.azeret_mono_variable, 500),
    variableFont(R.font.azeret_mono_variable, 550),
    variableFont(R.font.azeret_mono_variable, 600),
    variableFont(R.font.azeret_mono_variable, 700),
)

val RiffAccent = Color(0xFFE6FF4D)
val RiffAccentLight = Color(0xFF96A82F)
val RiffDockFallback = Color(0xFF34485F)
val RiffDockEmpty = Color(0xFFFDFDFC)
val RiffSubtextWeight = FontWeight(550)

/**
 * Riff-owned control colors. Keep these separate from Material's dynamic color
 * scheme so primary and secondary actions remain visually consistent across
 * screens while the surrounding app can still use dynamic theming.
 */
@Immutable
data class RiffControlColors(
    val accent: Color,
    val onAccent: Color,
    val secondary: Color,
    val onSecondary: Color,
    val nestedSurface: Color,
)

@Composable
fun riffControlColors(): RiffControlColors {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return RiffControlColors(
        accent = if (isDark) Color.White else Color(0xFF111112),
        onAccent = if (isDark) Color(0xFF111112) else Color.White,
        secondary = if (isDark) Color.White.copy(alpha = 0.14f) else Color(0xFFE3E3E1),
        onSecondary = if (isDark) Color.White else Color(0xFF111112),
        nestedSurface = if (isDark) Color.Black.copy(alpha = 0.24f) else Color.Black.copy(alpha = 0.065f),
    )
}
