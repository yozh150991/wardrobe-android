package com.familywardrobe.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

@Composable
internal fun FamilyOutfitQueuePanel(queue: List<FamilyOutfitDraft>, enabled: Boolean, select: (FamilyOutfitDraft) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { expanded = !expanded }, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.family_media_queue_count, queue.size, FamilyOutfitQueue.LIMIT))
    }
    if (expanded) Column(Modifier.heightIn(max = 160.dp).verticalScroll(rememberScrollState())) {
        queue.forEachIndexed { index, draft ->
            TextButton(onClick = { select(draft); expanded = false }, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.family_queue_item, index + 1, draft.change.name))
            }
        }
    }
}
