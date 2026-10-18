package com.familywardrobe.app

import androidx.compose.ui.graphics.Color
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.pow

class ThemeTest {
    private fun luminance(color: Color): Double {
        fun linear(value: Float): Double = value.toDouble().let { if (it <= 0.04045) it / 12.92 else ((it + 0.055) / 1.055).pow(2.4) }
        return 0.2126 * linear(color.red) + 0.7152 * linear(color.green) + 0.0722 * linear(color.blue)
    }
    private fun contrast(a: Color, b: Color): Double {
        val first = luminance(a); val second = luminance(b)
        return (maxOf(first, second) + 0.05) / (minOf(first, second) + 0.05)
    }

    @Test fun readableTextInEveryPaletteAndMode() {
        for (palette in WardrobePalette.entries) for (dark in listOf(false, true)) {
            val c = wardrobeColorScheme(palette, dark)
            val pairs = listOf(c.onPrimary to c.primary, c.onBackground to c.background,
                c.onSecondary to c.secondary, c.onTertiary to c.tertiary,
                c.onSecondaryContainer to c.secondaryContainer, c.onTertiaryContainer to c.tertiaryContainer,
                c.onSurface to c.surface, c.onSurfaceVariant to c.surface,
                c.onSurfaceVariant to c.surfaceVariant, c.onSurfaceVariant to c.primaryContainer,
                c.onPrimaryContainer to c.primaryContainer, c.primary to c.surface,
                c.primary to c.primaryContainer, c.secondary to c.surface,
                c.onSurfaceVariant to c.background, c.onSurfaceVariant to c.surfaceContainerHigh,
                c.error to c.background, c.onErrorContainer to c.errorContainer,
                c.onError to c.error)
            pairs.forEach { (text, surface) ->
                assertTrue("$palette dark=$dark: ${contrast(text, surface)}", contrast(text, surface) >= 4.5)
            }
            assertTrue(contrast(c.outline, c.surface) >= 3.0)
        }
    }

    @Test fun combinationsHaveDistinctBackgroundsAndSecondaryAccents() {
        val combinations = listOf(WardrobePalette.OLIVE_TERRA, WardrobePalette.PLUM_SAGE,
            WardrobePalette.INDIGO_APRICOT, WardrobePalette.TEAL_HONEY,
            WardrobePalette.BURGUNDY_LINEN, WardrobePalette.COBALT_CORAL)
        for (dark in listOf(false, true)) {
            val colours = combinations.map { wardrobeColorScheme(it, dark) }
            assertEquals(combinations.size, colours.map { it.background }.distinct().size)
            colours.forEach { c ->
                assertNotEquals(c.primary, c.secondary)
                assertNotEquals(c.surface, c.background)
            }
        }
    }

    @Test fun photoBackgroundsAndErrorsAreIndependentOfPalette() {
        for (dark in listOf(false, true)) {
            val reference = wardrobeColorScheme(WardrobePalette.BLUE, dark)
            for (palette in WardrobePalette.entries) {
                val colors = wardrobeColorScheme(palette, dark)
                assertEquals(reference.surfaceVariant, colors.surfaceVariant)
                assertEquals(reference.error, colors.error)
                assertEquals(reference.errorContainer, colors.errorContainer)
            }
        }
    }

    @Test fun explicitModeOverridesSystemAndSystemTracksChanges() {
        for (system in listOf(false, true)) {
            assertEquals(system, AppearanceMode.SYSTEM.usesDark(system))
            assertFalse(AppearanceMode.LIGHT.usesDark(system))
            assertTrue(AppearanceMode.DARK.usesDark(system))
        }
    }

    @Test fun storedIdentifiersRoundTripAndUnknownValuesRecover() {
        WardrobePalette.entries.forEach { assertEquals(it, WardrobePalette.fromKey(it.key)) }
        AppearanceMode.entries.forEach { assertEquals(it, AppearanceMode.fromKey(it.key)) }
        for (value in listOf(null, "", "future-value")) {
            assertEquals(WardrobePalette.OLIVE_TERRA, WardrobePalette.fromKey(value))
            assertEquals(AppearanceMode.SYSTEM, AppearanceMode.fromKey(value))
        }
    }
}
