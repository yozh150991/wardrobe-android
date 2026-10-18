package com.familywardrobe.app

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.DateFormat
import java.util.Date

@Composable
fun WishTimeline(wishId: String, vm: WardrobeViewModel) {
    val snapshot by vm.state.collectAsStateWithLifecycle()
    val events = snapshot.wishEvents.filter { it.wishId == wishId }.sortedWith(compareBy<WishEvent> { it.createdAt }.thenBy { it.id })
    var expanded by rememberSaveable(wishId) { mutableStateOf(false) }
    val locale = LocalConfiguration.current.locales[0]
    val uri = LocalUriHandler.current
    if (events.isNotEmpty()) OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { expanded = !expanded }) { Text(stringResource(R.string.wish_timeline)) }
            if (expanded) events.forEach { event ->
                val label = when(event.kind) {
                    "candidate" -> R.string.wish_event_candidate
                    "candidate_cancelled" -> R.string.wish_event_candidate_cancelled
                    "purchased" -> R.string.wish_event_purchased
                    else -> R.string.wish_event_purchase_cancelled
                }
                HorizontalDivider()
                Text(stringResource(label), style = MaterialTheme.typography.titleSmall)
                Text(DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, locale).format(Date(event.createdAt)), style = MaterialTheme.typography.bodySmall)
                Text(event.name)
                if (event.photoId.isNotBlank()) ItemPhoto(event.photoId, vm, Modifier.fillMaxWidth().height(120.dp), description = event.name)
                if (event.priceMinor != null) Text(moneyLabel(event.priceMinor, event.currency, locale))
                if (event.inspiration.isNotBlank()) Text(event.inspiration)
                if (event.link.isNotBlank()) TextButton(onClick = { runCatching { uri.openUri(event.link) }.onFailure { vm.notice.value = R.string.wish_link_failed } }) { Text(stringResource(R.string.open_link)) }
            }
        }
    }
}
