@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.familywardrobe.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.json.JSONArray
import org.json.JSONObject

internal data class ShopEditorTarget(val wardrobe: String, val kind: String, val original: String)

@Composable
internal fun ShopImportScreen(s: Snapshot, currentWardrobe: String, vm: WardrobeViewModel,
    onDirty: (Boolean) -> Unit, openEditor: (ShopEditorTarget) -> Unit, openExisting: (Wish) -> Unit) {
    val restored = remember { vm.draftFor("shop", currentWardrobe, "") ?: JSONObject() }
    val id = rememberSaveable { restored.optString("id").ifBlank { newId() } }
    val sharedText = remember { restored.optString("sharedText") }
    val sharedLinks = remember(sharedText) { ShopImportRules.urls(sharedText) }
    var url by rememberSaveable { mutableStateOf(restored.optString("input")) }
    var results by rememberSaveable { mutableStateOf(restored.optString("results", "[]")) }
    var selected by rememberSaveable { mutableIntStateOf(restored.optInt("selected", -1)) }
    var imageIndex by rememberSaveable { mutableIntStateOf(restored.optInt("imageIndex", 0)) }
    var photo by rememberSaveable { mutableStateOf(restored.optString("photo")) }
    var target by rememberSaveable { mutableStateOf(restored.optString("target", "wish")) }
    var targetWardrobe by rememberSaveable { mutableStateOf(restored.optString("targetWardrobe", currentWardrobe)) }
    var needId by rememberSaveable { mutableStateOf(restored.optString("needId")) }
    var duplicateConfirm by remember { mutableStateOf(false) }
    val busy by vm.busy.collectAsStateWithLifecycle()
    val options = remember(results) { runCatching { JSONArray(results) }.getOrDefault(JSONArray()) }
    val product = options.optJSONObject(selected)
    val images = product?.optJSONArray("images") ?: JSONArray()
    val link = product?.optString("url")?.ifBlank { url.trim() } ?: url.trim()
    val validLink = ShopImportRules.webUrl(link)
    val duplicates = s.wishes.filter { it.wardrobeId == targetWardrobe && it.id != needId && validLink.isNotBlank() &&
        ShopImportRules.linkKey(it.link) == ShopImportRules.linkKey(validLink) }
    val payload = JSONObject().put("id", id).put("input", url).put("results", results).put("selected", selected)
        .put("imageIndex", imageIndex).put("photo", photo).put("target", target).put("targetWardrobe", targetWardrobe)
        .put("needId", needId).put("sharedText", sharedText).put("shareId", restored.optString("shareId")).toString()
    LaunchedEffect(payload) { onDirty(true); vm.keepDraft("shop", currentWardrobe, "", JSONObject(payload)) }
    fun loadPhoto() {
        val image = images.optString(imageIndex)
        if (image.isNotBlank()) vm.loadShopPhoto(image) { photo = it }
    }
    fun proceed() {
        val name = product?.optString("name").orEmpty().ifBlank {
            if (sharedLinks.isEmpty()) TextLimits.take(sharedText, 120) else ""
        }
        vm.prepareShopEditor(targetWardrobe, target, needId, name, validLink, photo,
            product?.optString("price").orEmpty(), product?.optString("currency").orEmpty())?.let(openEditor)
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.shop_intro), color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (sharedLinks.size > 1) {
            Text(stringResource(R.string.shop_choose_link))
            sharedLinks.forEach { value -> TextButton(onClick = { url = value; results = "[]"; selected = -1; photo = "" }, enabled = !busy) { Text(value) } }
        }
        OutlinedTextField(url, { url = TextLimits.input(it, 2000); results = "[]"; selected = -1; photo = "" },
            label = { Text(stringResource(R.string.wish_link_optional)) }, enabled = !busy,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), modifier = Modifier.fillMaxWidth(),
            isError = url.isNotBlank() && ShopImportRules.webUrl(url).isBlank(),
            supportingText = { if (url.isNotBlank() && ShopImportRules.webUrl(url).isBlank()) Text(stringResource(R.string.wish_link_error)) })
        OutlinedButton(onClick = {
            vm.loadShopMetadata(url.trim()) { rows ->
                val data = JSONArray()
                rows.forEach { p -> data.put(JSONObject().put("name", p.name).put("url", p.url).put("price", p.price)
                    .put("currency", p.currency).put("images", JSONArray(p.images)).put("uncertainPrice", p.uncertainPrice)) }
                results = data.toString(); selected = -1; photo = ""; imageIndex = 0
            }
        }, enabled = !busy && ShopImportRules.webUrl(url).isNotBlank(), modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.shop_load))
        }
        Text(stringResource(R.string.shop_fallback), style = MaterialTheme.typography.bodySmall)
        if (options.length() > 0) {
            Text(stringResource(R.string.shop_choose_product), style = MaterialTheme.typography.titleMedium)
            repeat(options.length()) { index ->
                val p = options.getJSONObject(index)
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SelectionRow(p.optString("name").ifBlank { stringResource(R.string.shop_unnamed) }, selected == index, !busy) {
                            selected = index; imageIndex = 0; photo = ""
                            p.optJSONArray("images")?.optString(0)?.takeIf { it.isNotBlank() }?.let { image -> vm.loadShopPhoto(image) { photo = it } }
                        }
                        if (p.optString("price").isNotBlank()) Text(p.optString("price") + " " + p.optString("currency"))
                        if (p.optBoolean("uncertainPrice")) Text(stringResource(R.string.shop_price_unknown), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        if (selected >= 0) {
            if (photo.isNotBlank()) ItemPhoto(photo, vm, Modifier.fillMaxWidth().height(190.dp), fit = true)
            if (images.length() > 1) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(images.length()) { index -> FilterChip(selected = imageIndex == index,
                    onClick = { imageIndex = index; photo = ""; vm.loadShopPhoto(images.getString(index)) { photo = it } }, enabled = !busy,
                    label = { Text(stringResource(R.string.shop_photo_number, index + 1)) }) }
            }
            if (images.length() > 0) TextButton(onClick = ::loadPhoto, enabled = !busy) { Text(stringResource(R.string.shop_retry_photo)) }
            if (photo.isNotBlank()) TextButton(onClick = { photo = "" }, enabled = !busy) { Text(stringResource(R.string.remove_photo)) }
        }
        ShopDropdown(stringResource(R.string.shop_wardrobe), s.wardrobes.find { it.id == targetWardrobe }?.displayName().orEmpty(), !busy,
            s.wardrobes.map { it.id to it.displayName() }) { targetWardrobe = it; needId = "" }
        Text(stringResource(R.string.shop_destination), style = MaterialTheme.typography.titleMedium)
        SelectionRow(stringResource(R.string.shop_wanted), target == "wish", !busy) { target = "wish" }
        SelectionRow(stringResource(R.string.shop_owned), target == "garment", !busy) { target = "garment"; needId = "" }
        if (target == "wish") {
            val needs = s.wishes.filter { it.wardrobeId == targetWardrobe && it.kind == "need" && it.ownedId.isBlank() }
            if (needs.isNotEmpty()) ShopDropdown(stringResource(R.string.shop_attach_need), needs.find { it.id == needId }?.name ?: stringResource(R.string.shop_new_product),
                !busy, listOf("" to stringResource(R.string.shop_new_product)) + needs.map { it.id to it.name }) { needId = it }
        }
        if (duplicates.isNotEmpty()) {
            Text(stringResource(R.string.shop_duplicate), color = MaterialTheme.colorScheme.primary)
            duplicates.take(5).forEach { wish -> TextButton(onClick = { openExisting(wish) }, enabled = !busy) { Text(wish.name) } }
        }
        Button(onClick = { if (duplicates.isNotEmpty()) duplicateConfirm = true else proceed() },
            enabled = !busy && s.wardrobes.any { it.id == targetWardrobe } && (url.isBlank() || validLink.isNotBlank()) &&
                (url.isNotBlank() || sharedLinks.isEmpty()) &&
                (options.length() == 0 || product != null), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text(stringResource(R.string.shop_review))
        }
        Text(stringResource(R.string.shop_review_hint), style = MaterialTheme.typography.bodySmall)
    }
    if (duplicateConfirm) AlertDialog(onDismissRequest = { duplicateConfirm = false },
        text = { Text(stringResource(R.string.shop_duplicate_confirm)) },
        confirmButton = { TextButton(onClick = { duplicateConfirm = false; proceed() }, enabled = !busy) { Text(stringResource(R.string.shop_continue)) } },
        dismissButton = { TextButton(onClick = { duplicateConfirm = false }) { Text(stringResource(R.string.cancel)) } })
}

@Composable
private fun ShopDropdown(title: String, value: String, enabled: Boolean, options: List<Pair<String, String>>, choose: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Text(title, style = MaterialTheme.typography.labelLarge)
        Box {
            OutlinedButton(onClick = { expanded = true }, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(value, modifier = Modifier.weight(1f)); Icon(Icons.Outlined.ArrowDropDown, null)
            }
            DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
                options.forEach { (key, label) -> DropdownMenuItem(text = { Text(label) }, onClick = { expanded = false; choose(key) }) }
            }
        }
    }
}
