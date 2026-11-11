package com.familywardrobe.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** STYLE-03: one swatch per base colour (a custom shade shows its base). Outlined, so white and black read on any theme. */
internal fun swatch(colour: String): Color = when (colour) {
    "white" -> Color(0xFFF6F3EC); "black" -> Color(0xFF1C1C1C); "grey" -> Color(0xFF9A9A9A); "beige" -> Color(0xFFD9C4A1)
    "navy" -> Color(0xFF1E2C5A); "brown" -> Color(0xFF5C3B2A); "olive" -> Color(0xFF6B6A36); "blue" -> Color(0xFF3B6DB3)
    "red" -> Color(0xFFB3363A); "cream" -> Color(0xFFF1E6CC); "camel" -> Color(0xFFC19A6B); "khaki" -> Color(0xFFA39867)
    "light_blue" -> Color(0xFF9EC5E8); "green" -> Color(0xFF2E7D4F); "burgundy" -> Color(0xFF6D1F2C); "pink" -> Color(0xFFE8A0B4)
    "orange" -> Color(0xFFE07A2E); "yellow" -> Color(0xFFE9C83A); "purple" -> Color(0xFF6B3FA0); else -> Color(0xFFB9B1A7)
}

/** Overlapping colour dots, like a palette card; read aloud as the colour names. */
@Composable
internal fun ColourDots(colours: List<String>, size: Dp = 28.dp) {
    if (colours.isEmpty()) return
    val names = colours.map { stringResource(label(it)) }.joinToString(", ")
    val step = size * 2 / 3
    val outline = MaterialTheme.colorScheme.outline
    Box(Modifier.width(size + step * (colours.size - 1)).height(size).clearAndSetSemantics { contentDescription = names }) {
        colours.forEachIndexed { index, colour ->
            Box(Modifier.offset(x = step * index).size(size).clip(CircleShape).background(swatch(colour)).border(1.dp, outline, CircleShape))
        }
    }
}

private fun labelText(label: String): Int = when (label) {
    StyleRules.GREAT -> R.string.style_label_great
    StyleRules.GOOD -> R.string.style_label_good
    StyleRules.FAIR -> R.string.style_label_fair
    else -> R.string.style_label_weak
}

/** «8 /10 Добре» with a ring, as one element for TalkBack. */
@Composable
internal fun ScoreBadge(rating: StyleRules.Rating) {
    val label = stringResource(labelText(rating.label()))
    val description = stringResource(R.string.style_score_description, rating.score, label)
    Row(Modifier.clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surface)
        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(50))
        .padding(start = 4.dp, end = 12.dp, top = 4.dp, bottom = 4.dp).clearAndSetSemantics { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(contentAlignment = Alignment.Center) {
            CircularProgressIndicator(progress = { rating.score / 10f }, modifier = Modifier.size(36.dp), strokeWidth = 3.dp,
                trackColor = MaterialTheme.colorScheme.surfaceVariant)
            Text("${rating.score}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        }
        Text("/10", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
    }
}

/**
 * The outfit's items as a grid of light tiles (3 per row; 2 with a large font). A tap opens the item's actions;
 * pinned items show a lock.
 */
@Composable
internal fun OutfitGrid(items: List<Garment>, vm: WardrobeViewModel, locks: Set<String>, enabled: Boolean, onSelect: (Garment) -> Unit) {
    val columns = if (LocalConfiguration.current.fontScale >= 1.4f) 2 else 3
    val sorted = items.sortedBy { Matching.categories.indexOf(it.category).let { i -> if (i < 0) Int.MAX_VALUE else i } }
    val locked = stringResource(R.string.look_locked)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        sorted.chunked(columns).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { g ->
                    Column(Modifier.weight(1f).clickable(enabled = enabled, role = Role.Button) { onSelect(g) }
                        .semantics(mergeDescendants = true) { if (g.id in locks) contentDescription = "${g.name}, $locked" },
                        verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
                            ItemPhoto(g.photoId, vm, Modifier.fillMaxSize().padding(6.dp).clip(RoundedCornerShape(10.dp)),
                                description = g.name, garmentColour = g.color)
                            if (g.id in locks) Icon(Icons.Outlined.Lock, contentDescription = null,
                                modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(18.dp))
                        }
                        Text(g.name, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

private fun pairText(code: String): Int = when (code) {
    StyleRules.WARM_COOL -> R.string.style_pair_warm_cool
    StyleRules.ACCENT_CONTRAST -> R.string.style_pair_accent_contrast
    StyleRules.CONTRAST -> R.string.style_pair_contrast
    StyleRules.ACCENT -> R.string.style_pair_accent
    StyleRules.CALM -> R.string.style_pair_calm
    StyleRules.TONAL -> R.string.style_pair_tonal
    StyleRules.SAME_ACCENT -> R.string.style_pair_same_accent
    StyleRules.BLACK_BROWN -> R.string.style_pair_black_brown
    StyleRules.DARK_DARK -> R.string.style_pair_dark_dark
    StyleRules.TWO_ACCENTS -> R.string.style_pair_two_accents
    StyleRules.ANALOGOUS -> R.string.style_pair_analogous
    StyleRules.COMPLEMENTARY -> R.string.style_pair_complementary
    else -> R.string.style_pair_unknown
}

@Composable
internal fun pairLine(a: String, b: String, code: String): String =
    stringResource(R.string.style_pair_line, stringResource(label(a)), stringResource(label(b)), stringResource(pairText(code)))

@Composable
private fun ReasonLine(good: Boolean, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
        Icon(if (good) Icons.Outlined.Check else Icons.Outlined.ErrorOutline, contentDescription = null, modifier = Modifier.size(18.dp),
            tint = if (good) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary)
        Text(text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
    }
}

/**
 * Why the outfit got its score: colours, style and weather, each in words the person can check. PERS-03: then which of
 * its colours are the person's own and which they wear less — why the variant moved up or down, not part of the score.
 */
@Composable
internal fun StyleReasons(rating: StyleRules.Rating, items: List<Garment>, weather: String, target: String,
    palette: PersonalChoices = PersonalChoices()) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (rating.pair.isNotEmpty()) ReasonLine(StyleRules.pairPoints(rating.pair) >= 3, pairLine(rating.pairA, rating.pairB, rating.pair))
        if (rating.clash.isNotEmpty() && !(rating.clashA == rating.pairA && rating.clashB == rating.pairB))
            ReasonLine(false, pairLine(rating.clashA, rating.clashB, rating.clash))
        // The three-colour rule (neutrals included).
        if (rating.colours > StyleRules.MAX_COLOURS) ReasonLine(false, stringResource(R.string.style_many_colours, rating.colours))
        val style = stringResource(label(rating.dominant))
        ReasonLine(rating.sharing == rating.counted && rating.targetStyle, when {
            rating.sharing == rating.counted && rating.targetStyle -> stringResource(R.string.style_one_style, style)
            rating.sharing == rating.counted -> stringResource(R.string.style_one_style_other, style, stringResource(label(target)))
            else -> stringResource(R.string.style_mixed, style, rating.sharing, rating.counted)
        })
        val weatherName = stringResource(label(weather))
        when (rating.weatherCode) {
            StyleRules.WEATHER_OK -> ReasonLine(true, stringResource(R.string.style_weather_ok, weatherName))
            StyleRules.NO_OUTERWEAR -> ReasonLine(false, stringResource(R.string.style_weather_no_outerwear, weatherName))
            else -> ReasonLine(false, stringResource(R.string.style_weather_season, items.find { it.id == rating.seasonItem }?.name.orEmpty(), weatherName))
        }
        val colours = items.map { it.color }.distinct()
        val mine = colours.filter { palette.palette(it) == PersonalChoices.LIKED }.map { stringResource(label(it)) }
        val less = colours.filter { palette.palette(it) == PersonalChoices.AVOIDED }.map { stringResource(label(it)) }
        if (mine.isNotEmpty()) ReasonLine(true, stringResource(R.string.style_colours_mine, mine.joinToString(", ")))
        if (less.isNotEmpty()) ReasonLine(false, stringResource(R.string.style_colours_less, less.joinToString(", ")))
    }
}

/** «Добре → Краще»: the main colours now and after one change, what to change and why it is better. */
@Composable
internal fun BetterBlock(swap: BetterSwap, enabled: Boolean, apply: () -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    ColourDots(listOf(swap.before.pairA, swap.before.pairB).filter { it.isNotEmpty() }, 22.dp)
                    Text(stringResource(R.string.style_now, swap.before.score), style = MaterialTheme.typography.labelMedium)
                }
                Icon(Icons.Outlined.ChevronRight, contentDescription = null)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    ColourDots(listOf(swap.after.pairA, swap.after.pairB).filter { it.isNotEmpty() }, 22.dp)
                    Text(stringResource(R.string.style_better, swap.after.score), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }
            }
            Text(stringResource(R.string.style_better_change, swap.new.name, swap.old.name), style = MaterialTheme.typography.bodyMedium)
            // The reason: the colour pair, or the style or weather, whichever the change improves.
            Text(when {
                swap.after.colour > swap.before.colour && swap.after.pair.isNotEmpty() -> pairLine(swap.after.pairA, swap.after.pairB, swap.after.pair)
                swap.after.style > swap.before.style -> stringResource(R.string.style_better_style)
                else -> stringResource(R.string.style_better_weather)
            }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = apply, enabled = enabled, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.style_apply)) }
        }
    }
}
