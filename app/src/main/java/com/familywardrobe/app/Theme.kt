package com.familywardrobe.app

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Colours have distinct roles; photo canvases and semantic errors stay independent.
internal data class PaletteTokens(
    val primary: Color,
    val onPrimary: Color,
    val selected: Color,
    val secondary: Color,
    val onSecondary: Color,
    val details: Color,
    val onDetails: Color,
    val canvas: Color,
    val paper: Color,
    val ink: Color,
    val muted: Color,
    val soft: Color,
    val outline: Color,
    val outlineVariant: Color
)

private fun legacyPaletteTokens(primary: Color, onPrimary: Color, selected: Color, dark: Boolean) = PaletteTokens(
    primary = primary, onPrimary = onPrimary, selected = selected, secondary = primary, onSecondary = onPrimary,
    details = if (dark) Color(0xFF3B3732) else Color(0xFFEEEAE4),
    onDetails = if (dark) Color(0xFFF3F0EB) else Color(0xFF292B2D),
    canvas = if (dark) Color(0xFF222120) else Color(0xFFFAF8F5),
    paper = if (dark) Color(0xFF2B2927) else Color(0xFFFFFFFF),
    ink = if (dark) Color(0xFFF3F0EB) else Color(0xFF292B2D),
    muted = if (dark) Color(0xFFB6B2AC) else Color(0xFF686862),
    soft = if (dark) Color(0xFF3B3732) else Color(0xFFEEEAE4),
    outline = if (dark) Color(0xFF858880) else Color(0xFF868982),
    outlineVariant = if (dark) Color(0xFF494641) else Color(0xFFE1DED7)
)

internal fun paletteTokens(palette: WardrobePalette, dark: Boolean): PaletteTokens = when (palette) {
    WardrobePalette.OLIVE_TERRA -> if (dark) PaletteTokens(
        primary = Color(0xFFBDD3B6),
        onPrimary = Color(0xFF233626),
        selected = Color(0xFF334A3A),
        secondary = Color(0xFFE9B49C),
        onSecondary = Color(0xFF45291E),
        details = Color(0xFFCAB376),
        onDetails = Color(0xFF352A14),
        canvas = Color(0xFF20261F),
        paper = Color(0xFF293228),
        ink = Color(0xFFF1F0E3),
        muted = Color(0xFFC0C1AE),
        soft = Color(0xFF343C2D),
        outline = Color(0xFF889880),
        outlineVariant = Color(0xFF45513F)
    ) else PaletteTokens(
        primary = Color(0xFF355345),
        onPrimary = Color(0xFFFFFDF8),
        selected = Color(0xFFDCE3CB),
        secondary = Color(0xFF9F533E),
        onSecondary = Color(0xFFFFFDF8),
        details = Color(0xFFD4BA7A),
        onDetails = Color(0xFF493A1C),
        canvas = Color(0xFFF2F0E6),
        paper = Color(0xFFFFFDF6),
        ink = Color(0xFF28332B),
        muted = Color(0xFF625F50),
        soft = Color(0xFFEBE7D9),
        outline = Color(0xFF787E70),
        outlineVariant = Color(0xFFD7D6C7)
    )
    WardrobePalette.PLUM_SAGE -> if (dark) PaletteTokens(
        primary = Color(0xFFDFC0D5),
        onPrimary = Color(0xFF3B2436),
        selected = Color(0xFF493449),
        secondary = Color(0xFFAACDBF),
        onSecondary = Color(0xFF233D34),
        details = Color(0xFFEFC4AA),
        onDetails = Color(0xFF493021),
        canvas = Color(0xFF282127),
        paper = Color(0xFF332A33),
        ink = Color(0xFFF6EBF1),
        muted = Color(0xFFC6B7C2),
        soft = Color(0xFF443641),
        outline = Color(0xFFAA93A1),
        outlineVariant = Color(0xFF53404E)
    ) else PaletteTokens(
        primary = Color(0xFF633B59),
        onPrimary = Color(0xFFFFFDF8),
        selected = Color(0xFFE4CFDD),
        secondary = Color(0xFF47665D),
        onSecondary = Color(0xFFFFFDF8),
        details = Color(0xFFEFC4AA),
        onDetails = Color(0xFF593B2F),
        canvas = Color(0xFFF0E8ED),
        paper = Color(0xFFFCF8FA),
        ink = Color(0xFF322A34),
        muted = Color(0xFF605862),
        soft = Color(0xFFE9E1E7),
        outline = Color(0xFF817680),
        outlineVariant = Color(0xFFDCCFD8)
    )
    WardrobePalette.INDIGO_APRICOT -> if (dark) PaletteTokens(
        primary = Color(0xFFBBCCE9),
        onPrimary = Color(0xFF253750),
        selected = Color(0xFF31435F),
        secondary = Color(0xFFEABC9A),
        onSecondary = Color(0xFF44291B),
        details = Color(0xFFD7B391),
        onDetails = Color(0xFF3E2D1E),
        canvas = Color(0xFF222631),
        paper = Color(0xFF2B3342),
        ink = Color(0xFFEFF0F4),
        muted = Color(0xFFBCC0CD),
        soft = Color(0xFF363D49),
        outline = Color(0xFF8D97AA),
        outlineVariant = Color(0xFF485160)
    ) else PaletteTokens(
        primary = Color(0xFF304669),
        onPrimary = Color(0xFFFFFDF8),
        selected = Color(0xFFD7E0EF),
        secondary = Color(0xFF9B5130),
        onSecondary = Color(0xFFFFFDF8),
        details = Color(0xFFF0CAA5),
        onDetails = Color(0xFF603F26),
        canvas = Color(0xFFEDECE7),
        paper = Color(0xFFFBF8F0),
        ink = Color(0xFF283044),
        muted = Color(0xFF636363),
        soft = Color(0xFFE8E1D5),
        outline = Color(0xFF7C7D86),
        outlineVariant = Color(0xFFD5D4CB)
    )
    WardrobePalette.TEAL_HONEY -> if (dark) PaletteTokens(
        primary = Color(0xFFAFD6CB),
        onPrimary = Color(0xFF173C37),
        selected = Color(0xFF294A42),
        secondary = Color(0xFFE5C773),
        onSecondary = Color(0xFF3F3013),
        details = Color(0xFFCEBA79),
        onDetails = Color(0xFF352A14),
        canvas = Color(0xFF1E2825),
        paper = Color(0xFF27352F),
        ink = Color(0xFFEBF3EB),
        muted = Color(0xFFB4C9BD),
        soft = Color(0xFF334239),
        outline = Color(0xFF839E8E),
        outlineVariant = Color(0xFF41594D)
    ) else PaletteTokens(
        primary = Color(0xFF1F5A57),
        onPrimary = Color(0xFFFFFDF8),
        selected = Color(0xFFCBDDD6),
        secondary = Color(0xFF825619),
        onSecondary = Color(0xFFFFFDF8),
        details = Color(0xFFE5C773),
        onDetails = Color(0xFF493A1C),
        canvas = Color(0xFFE6EFEB),
        paper = Color(0xFFF6FAF5),
        ink = Color(0xFF253D36),
        muted = Color(0xFF506158),
        soft = Color(0xFFDAE4D8),
        outline = Color(0xFF687D71),
        outlineVariant = Color(0xFFBBD0C6)
    )
    WardrobePalette.BURGUNDY_LINEN -> if (dark) PaletteTokens(
        primary = Color(0xFFE7BBCA),
        onPrimary = Color(0xFF482637),
        selected = Color(0xFF50313D),
        secondary = Color(0xFFC1CDB2),
        onSecondary = Color(0xFF2E3B25),
        details = Color(0xFFD9BE9C),
        onDetails = Color(0xFF4F3C29),
        canvas = Color(0xFF2B2223),
        paper = Color(0xFF382D2C),
        ink = Color(0xFFF5EBE3),
        muted = Color(0xFFCDBCB2),
        soft = Color(0xFF453735),
        outline = Color(0xFFA38D83),
        outlineVariant = Color(0xFF5A4545)
    ) else PaletteTokens(
        primary = Color(0xFF743E50),
        onPrimary = Color(0xFFFFFDF8),
        selected = Color(0xFFE5CDD2),
        secondary = Color(0xFF55634E),
        onSecondary = Color(0xFFFFFDF8),
        details = Color(0xFFD9BE9C),
        onDetails = Color(0xFF4F3C29),
        canvas = Color(0xFFEFE5DD),
        paper = Color(0xFFFBF5ED),
        ink = Color(0xFF3B2D30),
        muted = Color(0xFF64554D),
        soft = Color(0xFFE6DED0),
        outline = Color(0xFF867367),
        outlineVariant = Color(0xFFD8C8BC)
    )
    WardrobePalette.COBALT_CORAL -> if (dark) PaletteTokens(
        primary = Color(0xFFBBCDFC),
        onPrimary = Color(0xFF233971),
        selected = Color(0xFF314777),
        secondary = Color(0xFFF0BAB1),
        onSecondary = Color(0xFF512724),
        details = Color(0xFFDDB7BA),
        onDetails = Color(0xFF4A2A2D),
        canvas = Color(0xFF222635),
        paper = Color(0xFF2B3245),
        ink = Color(0xFFEFF0FA),
        muted = Color(0xFFBEC4DA),
        soft = Color(0xFF373C52),
        outline = Color(0xFF949CB7),
        outlineVariant = Color(0xFF484E69)
    ) else PaletteTokens(
        primary = Color(0xFF3559A6),
        onPrimary = Color(0xFFFFFDF8),
        selected = Color(0xFFD4DDF3),
        secondary = Color(0xFFAD453E),
        onSecondary = Color(0xFFFFFDF8),
        details = Color(0xFFEDB6AB),
        onDetails = Color(0xFF55332E),
        canvas = Color(0xFFE9ECF5),
        paper = Color(0xFFF9F8FC),
        ink = Color(0xFF2E344A),
        muted = Color(0xFF5C5C72),
        soft = Color(0xFFE7DDE4),
        outline = Color(0xFF7C7B8C),
        outlineVariant = Color(0xFFD1D5E4)
    )
    WardrobePalette.BLUE -> if (dark) legacyPaletteTokens(Color(0xFFC1D9E5), Color(0xFF22343D), Color(0xFF34434B), dark)
        else legacyPaletteTokens(Color(0xFF38596C), Color(0xFFFFFFFF), Color(0xFFE7EEF2), dark)
    WardrobePalette.SAGE -> if (dark) legacyPaletteTokens(Color(0xFFBFD3BC), Color(0xFF223529), Color(0xFF344236), dark)
        else legacyPaletteTokens(Color(0xFF42634D), Color(0xFFFFFFFF), Color(0xFFE9EEE6), dark)
    WardrobePalette.TERRACOTTA -> if (dark) legacyPaletteTokens(Color(0xFFEFB49C), Color(0xFF45291E), Color(0xFF49372E), dark)
        else legacyPaletteTokens(Color(0xFF914C36), Color(0xFFFFFFFF), Color(0xFFF6EAE4), dark)
    WardrobePalette.PLUM -> if (dark) legacyPaletteTokens(Color(0xFFD8BFD9), Color(0xFF372738), Color(0xFF423545), dark)
        else legacyPaletteTokens(Color(0xFF71536F), Color(0xFFFFFFFF), Color(0xFFEFE8EF), dark)
    WardrobePalette.GRAPHITE -> if (dark) legacyPaletteTokens(Color(0xFFD1D4D8), Color(0xFF282B2F), Color(0xFF393C40), dark)
        else legacyPaletteTokens(Color(0xFF484B4F), Color(0xFFFFFFFF), Color(0xFFEBEDEF), dark)
}

internal fun wardrobeColorScheme(palette: WardrobePalette, dark: Boolean): ColorScheme {
    val tokens = paletteTokens(palette, dark)
    val classic = palette in listOf(WardrobePalette.BLUE, WardrobePalette.SAGE, WardrobePalette.TERRACOTTA,
        WardrobePalette.PLUM, WardrobePalette.GRAPHITE)
    // Neutral image backgrounds preserve the perceived colours of garments.
    val photo = if (dark) Color(0xFF353634) else Color(0xFFEEEFED)
    return (if (dark) darkColorScheme() else lightColorScheme()).copy(
        primary = tokens.primary, onPrimary = tokens.onPrimary,
        primaryContainer = tokens.selected, onPrimaryContainer = tokens.ink,
        inversePrimary = paletteTokens(palette, !dark).primary,
        secondary = tokens.secondary, onSecondary = tokens.onSecondary,
        secondaryContainer = tokens.details, onSecondaryContainer = tokens.onDetails,
        tertiary = if (classic) tokens.primary else if (dark) tokens.details else tokens.onDetails,
        onTertiary = if (classic) tokens.onPrimary else if (dark) tokens.onDetails else tokens.details,
        tertiaryContainer = tokens.details, onTertiaryContainer = tokens.onDetails,
        background = tokens.canvas, onBackground = tokens.ink,
        surface = tokens.paper, onSurface = tokens.ink,
        surfaceVariant = photo, onSurfaceVariant = tokens.muted,
        surfaceTint = Color.Transparent,
        surfaceDim = tokens.soft, surfaceBright = tokens.paper,
        surfaceContainerLowest = tokens.canvas, surfaceContainerLow = tokens.paper,
        surfaceContainer = tokens.paper, surfaceContainerHigh = tokens.soft, surfaceContainerHighest = tokens.soft,
        outline = tokens.outline, outlineVariant = tokens.outlineVariant,
        inverseSurface = if (dark) Color(0xFFF3F0EB) else Color(0xFF292B2D),
        inverseOnSurface = if (dark) Color(0xFF292B2D) else Color(0xFFF3F0EB),
        error = if (dark) Color(0xFFFFB7AE) else Color(0xFF9E302C),
        onError = if (dark) Color(0xFF45211F) else Color.White,
        errorContainer = if (dark) Color(0xFF422D2A) else Color(0xFFFFF0ED),
        onErrorContainer = if (dark) Color(0xFFFFB7AE) else Color(0xFF9E302C),
        scrim = Color.Black
    )
}

private val WardrobeTypography = Typography(
    headlineSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 30.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 24.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 14.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 12.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp)
)

@Composable
fun WardrobeTheme(settings: AppearanceSettings = AppearanceSettings(), content: @Composable () -> Unit) {
    val dark = settings.mode.usesDark(isSystemInDarkTheme())
    val colors = remember(settings.palette, dark) { wardrobeColorScheme(settings.palette, dark) }
    MaterialTheme(
        colorScheme = colors,
        typography = WardrobeTypography,
        shapes = Shapes(RoundedCornerShape(6.dp), RoundedCornerShape(10.dp), RoundedCornerShape(14.dp), RoundedCornerShape(18.dp), RoundedCornerShape(24.dp)),
        content = content
    )
}
