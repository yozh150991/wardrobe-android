package com.familywardrobe.app

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

@Composable
fun inspirationPieces(): List<LookPiece> = InspirationCatalog.all().map { idea ->
    val name = when(idea.name) {
        "shirt" -> R.string.inspiration_shirt; "tee" -> R.string.inspiration_tee
        "knit" -> R.string.inspiration_knit; "sporttop" -> R.string.inspiration_sporttop
        "trousers" -> R.string.inspiration_trousers; "jeans" -> R.string.inspiration_jeans
        "shorts" -> R.string.inspiration_shorts; "joggers" -> R.string.inspiration_joggers
        "formaldress" -> R.string.inspiration_formaldress; "sneakers" -> R.string.inspiration_sneakers
        "loafers" -> R.string.inspiration_loafers; "boots" -> R.string.inspiration_boots
        "trainers" -> R.string.inspiration_trainers; "jacket" -> R.string.inspiration_jacket
        "coat" -> R.string.inspiration_coat; "raincoat" -> R.string.inspiration_raincoat
        "cardigan" -> R.string.inspiration_cardigan; "blazer" -> R.string.inspiration_blazer
        "scarf" -> R.string.inspiration_scarf; else -> label(idea.category)
    }
    LookPiece(idea.key, idea.category, stringResource(name) + " · " + stringResource(label(idea.color)),
        color = idea.color, season = idea.season)
}
