/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.theme

import android.graphics.Bitmap
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.SaverScope
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.palette.graphics.Palette
import com.materialkolor.score.Score

val DefaultThemeColor = Color(0xFFED5564)

@Composable
fun MetrolistTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    pureBlack: Boolean = false,
    content: @Composable () -> Unit,
) {
    val baseColorScheme = if (darkTheme) RiffDarkColorScheme else RiffLightColorScheme

    // Apply pureBlack modification if needed, similar to original logic
    val colorScheme = remember(baseColorScheme, pureBlack, darkTheme) {
        if (darkTheme && pureBlack) {
            baseColorScheme.pureBlack(true)
        } else {
            baseColorScheme
        }
    }

    // Use standard MaterialTheme instead of MaterialExpressiveTheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography, // Use the defined AppTypography
        content = content
    )
}

private val RiffLightColorScheme =
    lightColorScheme(
        primary = Color(0xFF111112),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFE4E4E2),
        onPrimaryContainer = Color(0xFF111112),
        inversePrimary = Color(0xFFD0D0CE),
        secondary = Color(0xFF55565A),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFE8E8E6),
        onSecondaryContainer = Color(0xFF1A1A1B),
        tertiary = Color(0xFF68696C),
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFE5E5E3),
        onTertiaryContainer = Color(0xFF1A1A1B),
        background = Color(0xFFFAFAF8),
        onBackground = Color(0xFF151516),
        surface = Color(0xFFFAFAF8),
        onSurface = Color(0xFF151516),
        surfaceVariant = Color(0xFFECECEA),
        onSurfaceVariant = Color(0xFF5E5E61),
        outline = Color(0xFF77777B),
        outlineVariant = Color(0xFFC9C9C7),
        scrim = Color.Black,
        inverseSurface = Color(0xFF2E2E30),
        inverseOnSurface = Color(0xFFF4F4F2),
        surfaceTint = Color(0xFF111112),
    ).copy(
        surfaceDim = Color(0xFFDEDEDC),
        surfaceBright = Color(0xFFFAFAF8),
        surfaceContainerLowest = Color.White,
        surfaceContainerLow = Color(0xFFF4F4F2),
        surfaceContainer = Color(0xFFEEEEEC),
        surfaceContainerHigh = Color(0xFFE8E8E6),
        surfaceContainerHighest = Color(0xFFE2E2E0),
    )

private val RiffDarkColorScheme =
    darkColorScheme(
        primary = Color.White,
        onPrimary = Color(0xFF111112),
        primaryContainer = Color(0xFF343437),
        onPrimaryContainer = Color.White,
        inversePrimary = Color(0xFF2B2B2D),
        secondary = Color(0xFFC5C5C7),
        onSecondary = Color(0xFF1A1A1B),
        secondaryContainer = Color(0xFF303034),
        onSecondaryContainer = Color(0xFFF2F2F2),
        tertiary = Color(0xFFB8B8BB),
        onTertiary = Color(0xFF1A1A1B),
        tertiaryContainer = Color(0xFF323235),
        onTertiaryContainer = Color.White,
        background = Color(0xFF0E0F10),
        onBackground = Color(0xFFF4F4F4),
        surface = Color(0xFF0E0F10),
        onSurface = Color(0xFFF4F4F4),
        surfaceVariant = Color(0xFF292A2D),
        onSurfaceVariant = Color(0xFFBEBEC1),
        outline = Color(0xFF8D8D91),
        outlineVariant = Color(0xFF444449),
        scrim = Color.Black,
        inverseSurface = Color(0xFFE5E5E3),
        inverseOnSurface = Color(0xFF242426),
        surfaceTint = Color.White,
    ).copy(
        surfaceDim = Color(0xFF0E0F10),
        surfaceBright = Color(0xFF36373A),
        surfaceContainerLowest = Color(0xFF090A0B),
        surfaceContainerLow = Color(0xFF151618),
        surfaceContainer = Color(0xFF1A1B1D),
        surfaceContainerHigh = Color(0xFF242528),
        surfaceContainerHighest = Color(0xFF2E2F32),
    )

fun Bitmap.extractThemeColor(): Color {
    val colorsToPopulation = Palette.from(this)
        .maximumColorCount(8)
        .generate()
        .swatches
        .associate { it.rgb to it.population }
    val rankedColors = Score.score(colorsToPopulation)
    return Color(rankedColors.first())
}

fun Bitmap.extractGradientColors(): List<Color> {
    val extractedColors = Palette.from(this)
        .maximumColorCount(64)
        .generate()
        .swatches
        .associate { it.rgb to it.population }

    val orderedColors = Score.score(extractedColors, 2, 0xff4285f4.toInt(), true)
        .sortedByDescending { Color(it).luminance() }

    return if (orderedColors.size >= 2)
        listOf(Color(orderedColors[0]), Color(orderedColors[1]))
    else
        listOf(Color(0xFF595959), Color(0xFF0D0D0D))
}

fun ColorScheme.pureBlack(apply: Boolean) =
    if (apply) copy(
        surface = Color.Black,
        background = Color.Black
    ) else this

val ColorSaver = object : Saver<Color, Int> {
    override fun restore(value: Int): Color = Color(value)
    override fun SaverScope.save(value: Color): Int = value.toArgb()
}
