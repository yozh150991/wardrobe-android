package com.familywardrobe.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job

/**
 * Shared-wardrobe actions wait for one account lock that a background batch may hold for a long upload.
 * The editors now show progress as soon as an action is queued (not only after the lock) and can cancel
 * every queued or running action (audit A-04). Cancellation keeps drafts: an interrupted send keeps its
 * submitted request and is confirmed by the same receipt on retry.
 */
internal class FamilyActions {
    private val jobs = mutableSetOf<Job>()
    /** [idle] runs once no tracked action is left. */
    fun track(job: Job, idle: () -> Unit) {
        synchronized(jobs) { jobs += job }
        job.invokeOnCompletion { if (synchronized(jobs) { jobs -= job; jobs.isEmpty() }) idle() }
    }
    fun cancel() { synchronized(jobs) { jobs.toList() }.forEach { it.cancel() } }
}

@Composable
internal fun FamilyBusyRow(waiting: Boolean, cancel: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        LinearProgressIndicator(Modifier.fillMaxWidth())
        if (waiting) Text(stringResource(R.string.family_waiting), style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = cancel) { Text(stringResource(R.string.cancel)) }
    }
}
