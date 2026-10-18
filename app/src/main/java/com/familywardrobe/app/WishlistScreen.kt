@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.familywardrobe.app

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.json.JSONObject
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

val wishCurrencies = listOf("PLN", "EUR", "USD", "UAH", "GBP")
fun moneyInput(minor: Long?): String = minor?.let { BigDecimal.valueOf(it, 2).toPlainString() }.orEmpty()
fun moneyLabel(minor: Long?, currency: String, locale: Locale): String = minor?.let {
    NumberFormat.getCurrencyInstance(locale).apply { this.currency = Currency.getInstance(currency) }.format(BigDecimal.valueOf(it, 2))
}.orEmpty()
fun validateWish(w: Wish) {
    require(w.name.trim().length in 1..120 && w.category in Matching.categories && w.kind in listOf("need", "product"))
    require(WishRules.validMinor(w.priceMinor) && WishRules.validMinor(w.budgetMinor))
    require(w.currency in wishCurrencies && w.budgetCurrency in wishCurrencies && WishRules.validLink(w.link))
    require(w.color.isEmpty() || w.color in Matching.colors)
    require(w.inspiration.length <= 2000 && w.requirements.length <= 2000 && w.needName.length <= 120)
    require(w.photoId.isEmpty() || PhotoStore.ID.matches(w.photoId))
    require(w.ownedId.isEmpty() || (PhotoStore.ID.matches(w.ownedId) && w.kind == "product"))
    require(w.needCategory.isEmpty() || w.needCategory in Matching.categories)
    require(w.kind != "need" || (w.priceMinor == null && w.color.isEmpty()))
    require(w.needPhotoId.isEmpty() || PhotoStore.ID.matches(w.needPhotoId))
    require(WishRules.validLink(w.needLink) && w.needInspiration.length <= 2000)
}

@Composable
fun Wishlist(s: Snapshot, wardrobe: String, vm: WardrobeViewModel, add: (String) -> Unit, foundProduct: (Wish) -> Unit = {}, importProduct: () -> Unit = {}, open: (String) -> Unit) {
    val busy by vm.busy.collectAsStateWithLifecycle()
    val saved by vm.savedWish.collectAsStateWithLifecycle()
    var choosing by rememberSaveable { mutableStateOf(false) }
    val locale = LocalConfiguration.current.locales[0]
    var kind by rememberSaveable { mutableStateOf("") }
    var query by rememberSaveable { mutableStateOf("") }
    val wishes = s.wishes.filter { it.wardrobeId == wardrobe && it.ownedId.isBlank() }
    val categoryNames = Matching.categories.associateWith { stringResource(label(it)) }
    val found = wishes.filter { (kind.isBlank() || it.kind == kind) && (it.name + " " + it.requirements + " " + it.inspiration + " " + categoryNames[it.category]).contains(query.trim(), ignoreCase = true) }
    LazyVerticalGrid(GridCells.Fixed(if (LocalConfiguration.current.fontScale >= 1.5f) 1 else 2),
        contentPadding = PaddingValues(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.wishlist_intro), color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(query, { query = it.take(120) }, label = { Text(stringResource(R.string.picker_search)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("" to R.string.all, "need" to R.string.wishlist_needs, "product" to R.string.wishlist_products).forEach { (code,title) -> FilterChip(kind == code, { kind = code }, label = { Text(stringResource(title)) }) }
                }
                Button(onClick = { choosing = true }, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Icon(Icons.Outlined.Add, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.add_wish))
                }
                saved?.takeIf { it.wardrobeId == wardrobe }?.let { wish ->
                    OutlinedCard {
                        Column(Modifier.padding(12.dp)) {
                            Text(stringResource(R.string.wish_saved, wish.name))
                            Row { TextButton(onClick = { open(wish.id) }) { Text(stringResource(R.string.open_wardrobe)) }
                                TextButton(onClick = { choosing = true }) { Text(stringResource(R.string.add_more)) } }
                        }
                    }
                }
            }
        }
        if (found.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
            Column(Modifier.padding(vertical = 24.dp)) {
                Text(stringResource(if (wishes.isEmpty()) R.string.empty_wishlist_new else R.string.no_results))
                if (wishes.isNotEmpty()) TextButton(onClick = { query = ""; kind = "" }) { Text(stringResource(R.string.reset_filters)) }
            }
        }
        items(found, key = { it.id }) { wish ->
            Card(onClick = { open(wish.id) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
                if (wish.kind == "product" && wish.photoId.isNotBlank()) Box {
                    ZoomableItemPhoto(wish.photoId, vm, Modifier.fillMaxWidth().height(170.dp), title = wish.name, garmentColour = wish.color)
                    Surface(Modifier.padding(8.dp), shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.tertiaryContainer, contentColor = MaterialTheme.colorScheme.onTertiaryContainer) {
                        Text(stringResource(if (wish.kind == "need") R.string.wish_need_badge else R.string.shopping), Modifier.padding(horizontal = 8.dp, vertical = 5.dp), style = MaterialTheme.typography.labelMedium)
                    }
                }
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    if (wish.kind == "need" || wish.photoId.isBlank()) Text(stringResource(if (wish.kind == "need") R.string.wish_need_badge else R.string.wish_product), style = MaterialTheme.typography.labelMedium)
                    Text(wish.name, style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(label(wish.category)), style = MaterialTheme.typography.bodySmall)
                    val amount = if (wish.kind == "need") wish.budgetMinor else wish.priceMinor
                    if (amount != null) Text(stringResource(if (wish.kind == "need") R.string.wish_budget_value else R.string.wish_price_value,
                        moneyLabel(amount, if (wish.kind == "need") wish.budgetCurrency else wish.currency, locale)), style = MaterialTheme.typography.bodySmall)
                    if (wish.kind == "need") TextButton(onClick = { foundProduct(wish) }, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.found_candidate)) }
                }
            }
        }
    }
    if (choosing) AlertDialog(onDismissRequest = { choosing = false }, title = { Text(stringResource(R.string.add_wish)) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.wish_kind_hint))
            OutlinedButton(onClick = { choosing = false; importProduct() }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.shop_title)) }
        } },
        confirmButton = { TextButton(onClick = { choosing = false; add("product") }) { Text(stringResource(R.string.wish_product)) } },
        dismissButton = { TextButton(onClick = { choosing = false; add("need") }) { Text(stringResource(R.string.wish_need)) } })
}

@Composable
fun WishDetail(w: Wish, vm: WardrobeViewModel, edit: () -> Unit, found: () -> Unit, check: () -> Unit, compatibility: () -> Unit, removed: () -> Unit, bought: () -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    val uriHandler = LocalUriHandler.current
    val busy by vm.busy.collectAsStateWithLifecycle()
    var deleting by remember { mutableStateOf(false) }
    var returning by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (w.photoId.isNotBlank()) ZoomableItemPhoto(w.photoId, vm, Modifier.fillMaxWidth().height(250.dp).clip(RoundedCornerShape(20.dp)), title = w.name, garmentColour = w.color)
        Text(w.name, style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(if (w.kind == "need") R.string.wish_need_badge else R.string.wish_product))
        Info(R.string.category, stringResource(label(w.category)))
        if (w.kind == "need") {
            NeedHistory(w, locale)
            if (w.inspiration.isNotBlank()) Info(R.string.wish_inspiration, w.inspiration)
            if (w.link.isNotBlank()) OutlinedButton(onClick = { runCatching { uriHandler.openUri(w.link) }.onFailure { vm.notice.value = R.string.wish_link_failed } }) { Text(stringResource(R.string.open_link)) }
        } else {
            Info(R.string.wish_price, moneyLabel(w.priceMinor, w.currency, locale).ifEmpty { stringResource(R.string.not_specified) })
            if (w.color.isNotBlank()) Info(R.string.color, stringResource(label(w.color)))
            if (w.inspiration.isNotBlank()) Info(R.string.wish_inspiration, w.inspiration)
            if (w.link.isNotBlank()) OutlinedButton(onClick = { runCatching { uriHandler.openUri(w.link) }.onFailure { vm.notice.value = R.string.wish_link_failed } }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.open_link)) }
            if (w.needName.isNotBlank()) NeedHistory(w, locale)
        }
        if (w.kind == "need") Button(onClick = found, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.found_candidate)) }
        if (w.kind == "product") Button(onClick = bought, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.i_bought_it)) }
        if (w.kind == "product") OutlinedButton(onClick = compatibility, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.compatibility_title)) }
        if (w.kind == "product" && w.needName.isNotBlank() && w.ownedId.isBlank()) OutlinedButton(onClick = { returning = true }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.candidate_undo)) }
        WishTimeline(w.id, vm)
        OutlinedButton(onClick = edit, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.edit)) }
        TextButton(onClick = check, enabled = !busy) { Text(stringResource(R.string.check_owned)) }
        TextButton(onClick = { deleting = true }, enabled = !busy) { Text(stringResource(R.string.delete)) }
    }
    if (deleting) AlertDialog(onDismissRequest = { deleting = false }, title = { Text(stringResource(R.string.remove_wish_title)) },
        text = { Text(w.name) }, confirmButton = { TextButton(onClick = { deleting = false; vm.deleteWish(w.id, removed) }) { Text(stringResource(R.string.delete)) } },
        dismissButton = { TextButton(onClick = { deleting = false }) { Text(stringResource(R.string.cancel)) } })
    if (returning) AlertDialog(onDismissRequest = { returning = false }, text = { Text(stringResource(R.string.candidate_undo_confirm)) },
        confirmButton = { TextButton(onClick = { returning = false; vm.returnToNeed(w.id) }, enabled = !busy) { Text(stringResource(R.string.candidate_undo)) } },
        dismissButton = { TextButton(onClick = { returning = false }) { Text(stringResource(R.string.cancel)) } })
}

@Composable
fun NeedHistory(w: Wish, locale: Locale) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.original_need), style = MaterialTheme.typography.titleSmall)
            Text(w.needName.ifBlank { w.name })
            if (w.requirements.isNotBlank()) Info(R.string.wish_requirements, w.requirements)
            Info(R.string.wish_budget, moneyLabel(w.budgetMinor, w.budgetCurrency, locale).ifEmpty { stringResource(R.string.not_specified) })
        }
    }
}

@Composable
fun WishEditor(original: Wish?, wardrobe: String, kind: String, converting: Boolean, vm: WardrobeViewModel,
    onDirty: (Boolean) -> Unit, done: (String) -> Unit) {
    val restored = remember { vm.draftFor("wish", wardrobe, original?.id.orEmpty()) }
    val id = rememberSaveable { restored?.optString("id")?.takeIf { it.isNotBlank() } ?: original?.id ?: newId() }
    fun initial(key: String, fallback: String) = restored?.optString(key, fallback) ?: fallback
    val product = kind == "product" || converting
    var name by rememberSaveable { mutableStateOf(initial("name", original?.name.orEmpty())) }
    var category by rememberSaveable { mutableStateOf(initial("category", original?.category.orEmpty())) }
    var price by rememberSaveable { mutableStateOf(initial("price", moneyInput(original?.priceMinor))) }
    var currency by rememberSaveable { mutableStateOf(initial("currency", original?.currency ?: "PLN")) }
    var budget by rememberSaveable { mutableStateOf(initial("budget", moneyInput(original?.budgetMinor))) }
    var budgetCurrency by rememberSaveable { mutableStateOf(initial("budgetCurrency", original?.budgetCurrency ?: "PLN")) }
    var requirements by rememberSaveable { mutableStateOf(initial("requirements", original?.requirements.orEmpty())) }
    var link by rememberSaveable { mutableStateOf(initial("link", original?.link.orEmpty())) }
    var color by rememberSaveable { mutableStateOf(initial("color", original?.color.orEmpty())) }
    var inspiration by rememberSaveable { mutableStateOf(initial("inspiration", original?.inspiration.orEmpty())) }
    var photo by rememberSaveable { mutableStateOf(initial("photo", original?.photoId.orEmpty())) }
    val photoKey = "$wardrobe:$id"
    val pendingPhoto by remember(vm, photoKey) { vm.editorPhoto(photoKey) }.collectAsStateWithLifecycle(initialValue = null)
    LaunchedEffect(pendingPhoto) { pendingPhoto?.let { photo = it } }
    fun receivePhoto(next: String) { vm.rememberEditorPhoto(photoKey, next); photo = next }
    val busy by vm.busy.collectAsStateWithLifecycle()
    var advanced by rememberSaveable { mutableStateOf(restored?.optBoolean("advanced", false) ?: false) }
    var validate by remember { mutableStateOf(false) }
    val nameFocus = remember { FocusRequester() }; val categoryFocus = remember { FocusRequester() }
    val amountFocus = remember { FocusRequester() }; val linkFocus = remember { FocusRequester() }
    val amountValid = runCatching { WishRules.money(if (product) price else budget) }.isSuccess
    val linkValid = WishRules.validLink(link)
    val payload = JSONObject().apply {
        put("id", id); put("name", name); put("category", category); put("kind", if (product) "product" else "need")
        put("converting", converting); put("price", price); put("currency", currency); put("budget", budget); put("budgetCurrency", budgetCurrency)
        put("requirements", requirements); put("link", link); put("color", color); put("inspiration", inspiration); put("photo", photo); put("advanced", advanced)
    }.toString()
    LaunchedEffect(advanced, validate, linkValid) {
        if (advanced && validate && !linkValid && name.isNotBlank() && category.isNotBlank() && amountValid) linkFocus.requestFocus()
    }
    val baseline = remember { payload }
    LaunchedEffect(payload) { onDirty(payload != baseline || restored != null); vm.keepDraft("wish", wardrobe, original?.id.orEmpty(), JSONObject(payload)) }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.importReferencePhoto(uri, ::receivePhoto)
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (restored != null) Text(stringResource(R.string.draft_restored), color = MaterialTheme.colorScheme.primary)
        Text(stringResource(if (converting) R.string.found_candidate_hint else if (product) R.string.wish_product_hint else R.string.wish_need_hint), color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (converting && original != null) NeedHistory(original, LocalConfiguration.current.locales[0])
        run {
            if (photo.isNotBlank()) ItemPhoto(photo, vm, Modifier.fillMaxWidth().height(190.dp).clip(RoundedCornerShape(18.dp)), fit = true)
            OutlinedButton(onClick = { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.wish_photo_optional)) }
            if (photo.isNotBlank()) TextButton(onClick = { vm.rememberEditorPhoto(photoKey, ""); photo = "" }, enabled = !busy) { Text(stringResource(R.string.remove_photo)) }
        }
        OutlinedTextField(name, { name = it.take(120) }, label = { Text(stringResource(R.string.name_required)) }, singleLine = true, enabled = !busy,
            isError = validate && name.isBlank(), supportingText = { if (validate && name.isBlank()) Text(stringResource(R.string.wish_name_required)) }, modifier = Modifier.fillMaxWidth().focusRequester(nameFocus))
        CategoryChoice(category, { category = it }, title = R.string.category_required, enabled = !busy,
            modifier = Modifier.fillMaxWidth().focusRequester(categoryFocus))
        if (validate && category.isBlank()) Text(stringResource(R.string.wish_category_required), color = MaterialTheme.colorScheme.error)
        MoneyField(if (product) price else budget, { if (product) price = it else budget = it }, if (product) currency else budgetCurrency,
            { if (product) currency = it else budgetCurrency = it }, if (product) R.string.wish_price else R.string.wish_budget,
            busy, validate && !amountValid, amountFocus)
        if (product) {
            TextButton(onClick = { advanced = !advanced }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(stringResource(R.string.additional_details)); Icon(if (advanced) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null) }
            if (advanced) {
                OutlinedTextField(link, { link = it.take(2000) }, label = { Text(stringResource(R.string.wish_link_optional)) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), isError = validate && !linkValid, enabled = !busy,
                    supportingText = { if (validate && !linkValid) Text(stringResource(R.string.wish_link_error)) }, modifier = Modifier.fillMaxWidth().focusRequester(linkFocus))
                Choice(color, listOf("") + Matching.colors, { color = it }, R.string.wish_color_optional, enabled = !busy, emptyLabel = R.string.not_specified)
                OutlinedTextField(inspiration, { inspiration = it.take(2000) }, label = { Text(stringResource(R.string.wish_inspiration_optional)) }, minLines = 2, enabled = !busy, modifier = Modifier.fillMaxWidth())
            }
            if (!converting && original?.needName?.isNotBlank() == true) NeedHistory(original, LocalConfiguration.current.locales[0])
        } else {
            OutlinedTextField(requirements, { requirements = it.take(2000) }, label = { Text(stringResource(R.string.wish_requirements)) }, minLines = 2, enabled = !busy, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(link, { link = it.take(2000) }, label = { Text(stringResource(R.string.wish_link_optional)) }, enabled = !busy,
                isError = validate && !linkValid, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), modifier = Modifier.fillMaxWidth().focusRequester(linkFocus))
            OutlinedTextField(inspiration, { inspiration = it.take(2000) }, label = { Text(stringResource(R.string.wish_inspiration_optional)) }, enabled = !busy, modifier = Modifier.fillMaxWidth())
        }
        Button(onClick = {
            validate = true
            when {
                name.isBlank() -> nameFocus.requestFocus()
                category.isBlank() -> categoryFocus.requestFocus()
                !amountValid -> amountFocus.requestFocus()
                !linkValid -> { advanced = true; if (!product) linkFocus.requestFocus() }
                else -> {
                    val next = (original ?: Wish(id, wardrobe, name, category)).copy(name = name.trim(), category = category,
                        kind = if (product) "product" else "need", priceMinor = if (product) WishRules.money(price) else null, currency = currency,
                        budgetMinor = if (!product) WishRules.money(budget) else original?.budgetMinor, budgetCurrency = budgetCurrency,
                        needName = if (!product) name.trim() else original?.needName.orEmpty(), needCategory = if (!product) category else original?.needCategory.orEmpty(),
                        requirements = requirements.trim(), link = link.trim(), color = if (product) color else "",
                        inspiration = inspiration.trim(), photoId = photo)
                    vm.saveWish(next, converting) { onDirty(false); done(id) }
                }
            }
        }, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(if (converting) R.string.save_candidate else R.string.save)) }
    }
}

@Composable
fun MoneyField(value: String, change: (String) -> Unit, currency: String, setCurrency: (String) -> Unit,
    title: Int, busy: Boolean, error: Boolean, focus: FocusRequester) {
    var expanded by remember { mutableStateOf(false) }
    val amount: @Composable (Modifier) -> Unit = { modifier ->
        OutlinedTextField(value, { change(it.take(20)) }, label = { Text(stringResource(title)) }, singleLine = true, enabled = !busy,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), isError = error,
            supportingText = { if (error) Text(stringResource(R.string.wish_amount_error)) }, modifier = modifier.focusRequester(focus))
    }
    val selector: @Composable () -> Unit = {
        Box {
            OutlinedButton(onClick = { expanded = true }, enabled = !busy, modifier = Modifier.heightIn(min = 56.dp)) { Text(currency); Icon(Icons.Outlined.ArrowDropDown, null) }
            DropdownMenu(expanded, onDismissRequest = { expanded = false }) { wishCurrencies.forEach { code ->
                DropdownMenuItem(text = { Text(code) }, onClick = { expanded = false; setCurrency(code) }) } }
        }
    }
    if (LocalConfiguration.current.fontScale >= 1.4f) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { amount(Modifier.fillMaxWidth()); selector() }
    else Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { amount(Modifier.weight(1f)); selector() }
}

@Composable
fun PurchaseHistory(wish: Wish, vm: WardrobeViewModel) {
    val locale = LocalConfiguration.current.locales[0]
    val uri = LocalUriHandler.current
    var expanded by rememberSaveable { mutableStateOf(false) }
    var confirmUndo by remember { mutableStateOf(false) }
    val snapshot by vm.state.collectAsStateWithLifecycle()
    val receipt = snapshot.purchaseUndos.find { it.wishId == wish.id }
    val undoReason = purchaseUndoReason(wish, receipt, snapshot)
    val busy by vm.busy.collectAsStateWithLifecycle()
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { expanded = !expanded }) {
                Text(stringResource(R.string.purchase_history)); Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null) }
            if (expanded) {
                Info(R.string.wish_price, moneyLabel(wish.priceMinor, wish.currency, locale).ifEmpty { stringResource(R.string.not_specified) })
                if (wish.link.isNotBlank()) TextButton(onClick = { runCatching { uri.openUri(wish.link) }.onFailure { vm.notice.value = R.string.wish_link_failed } }) { Text(stringResource(R.string.open_link)) }
                if (wish.inspiration.isNotBlank()) Info(R.string.wish_inspiration, wish.inspiration)
                if (wish.needName.isNotBlank()) NeedHistory(wish, locale)
                WishTimeline(wish.id, vm)
                OutlinedButton(onClick = { confirmUndo = true }, enabled = !busy && undoReason == null) { Text(stringResource(R.string.purchase_undo_persistent)) }
                if (undoReason != null) Text(stringResource(undoReason), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
    if (confirmUndo) AlertDialog(onDismissRequest = { confirmUndo = false }, text = { Text(stringResource(R.string.purchase_undo_confirm)) },
        confirmButton = { TextButton(onClick = { confirmUndo = false; vm.undoStoredPurchase(wish.id) }, enabled = !busy) { Text(stringResource(R.string.undo_action)) } },
        dismissButton = { TextButton(onClick = { confirmUndo = false }) { Text(stringResource(R.string.cancel)) } })
}
