package com.familywardrobe.app

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

@Composable
internal fun FamilyMediaQueuePanel(queue: List<FamilyAddDraft>, enabled: Boolean, select: (FamilyAddDraft) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedButton(onClick = { expanded = !expanded }, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.family_media_queue_count, queue.size, FamilyMediaQueue.LIMIT))
        }
        if (expanded) queue.forEachIndexed { index, pending ->
            OutlinedButton(onClick = { select(pending); expanded = false }, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.family_queue_item, index + 1, pending.name))
            }
        }
    }
}
