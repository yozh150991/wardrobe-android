package com.familywardrobe.app

import android.text.format.DateFormat
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.util.Date
import java.util.UUID

@Composable
internal fun FamilyAccessControl(vm: WardrobeViewModel) {
    var open by rememberSaveable(vm.sessionToken) { mutableStateOf(false) }
    OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
        Text(stringResource(R.string.family_access_open))
    }
    if (open) FamilyAccessDialog(vm) { open = false }
}

@Composable
private fun accessRole(role: String): String = stringResource(when (role) {
    "owner" -> R.string.family_role_owner
    "editor" -> R.string.family_role_editor
    else -> R.string.family_role_viewer
})

@Composable
private fun familyDirectoryName(entry: FamilyDirectoryEntry, wardrobes: List<Wardrobe>): String =
    entry.name.ifBlank { wardrobes.find { it.id == entry.wardrobeId }?.displayName().orEmpty() }

private data class FamilyAccessConfirmation(val text: String, val action: () -> Unit)

@Composable
private fun FamilyAccessDialog(vm: WardrobeViewModel, dismiss: () -> Unit) {
    val state by vm.familyAccess.state.collectAsStateWithLifecycle()
    val content by vm.state.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    var reading by rememberSaveable { mutableStateOf("") }
    var confirmation by remember { mutableStateOf<FamilyAccessConfirmation?>(null) }
    val enabled = !busy && !state.running
    val managed = state.wardrobes.find { it.remoteId == state.managedRemote && it.role == "owner" }
    LaunchedEffect(vm.sessionToken) { vm.familyAccess.refresh() }
    Dialog(onDismissRequest = dismiss, properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(16.dp)) {
                Text(stringResource(R.string.family_access_open), style = MaterialTheme.typography.titleLarge)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { vm.familyAccess.refresh() }, enabled = enabled, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.family_read_refresh))
                    }
                    TextButton(onClick = dismiss, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.close)) }
                }
                if (state.running) LinearProgressIndicator(Modifier.fillMaxWidth())
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item {
                        Text(stringResource(R.string.family_access_hint), style = MaterialTheme.typography.bodySmall)
                        state.notice?.let { notice ->
                            Text(stringResource(when (notice) {
                                FamilyAccessNotice.INVITED -> R.string.family_invite_created
                                FamilyAccessNotice.ACCEPTED -> R.string.family_invite_accepted
                                FamilyAccessNotice.DECLINED -> R.string.family_invite_declined
                                FamilyAccessNotice.UPDATED -> R.string.family_member_updated
                                FamilyAccessNotice.REVOKED -> R.string.family_member_revoked
                                FamilyAccessNotice.LEFT -> R.string.family_member_left
                            }), modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                        }
                        state.problem?.let { problem ->
                            Text(stringResource(when (problem) {
                                FamilyProblem.EMAIL -> R.string.family_email
                                FamilyProblem.ACCESS -> R.string.family_access
                                FamilyProblem.CONFIG -> R.string.family_members_config
                                FamilyProblem.CONFLICT -> R.string.family_members_conflict
                                FamilyProblem.EXPIRED -> R.string.family_invite_expired
                                FamilyProblem.LIMIT -> R.string.family_members_limit
                                FamilyProblem.ARCHIVE -> R.string.family_invite_invalid
                                else -> R.string.family_members_retry
                            }), color = MaterialTheme.colorScheme.error, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                            if (problem == FamilyProblem.EMAIL) TextButton(onClick = vm::sendFamilyVerification, enabled = enabled) {
                                Text(stringResource(R.string.family_send_email))
                            }
                        }
                    }
                    item { Text(stringResource(R.string.family_invitations), style = MaterialTheme.typography.titleMedium) }
                    if (state.loaded && state.invitations.isEmpty()) item { Text(stringResource(R.string.family_no_invites)) }
                    items(state.invitations, key = { "incoming:${it.inviteId}" }) { invite ->
                        FamilyAccessCard {
                            Text(invite.name, style = MaterialTheme.typography.titleMedium)
                            Text(stringResource(R.string.family_invite_from, invite.ownerEmail))
                            Text(accessRole(invite.role))
                            Text(stringResource(R.string.family_invite_until, DateFormat.getDateFormat(androidx.compose.ui.platform.LocalContext.current).format(Date(invite.expiresAt))))
                            val acceptText = stringResource(R.string.family_accept_confirm, invite.name, invite.ownerEmail, accessRole(invite.role))
                            OutlinedButton(onClick = { confirmation = FamilyAccessConfirmation(acceptText) { vm.familyAccess.respond(invite, true) } }, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
                                Text(stringResource(R.string.family_invite_accept))
                            }
                            TextButton(onClick = { vm.familyAccess.respond(invite, false) }, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
                                Text(stringResource(R.string.family_invite_decline))
                            }
                        }
                    }
                    item { Text(stringResource(R.string.family_available), style = MaterialTheme.typography.titleMedium) }
                    if (state.loaded && state.wardrobes.isEmpty()) item { Text(stringResource(R.string.family_no_available)) }
                    items(state.wardrobes, key = { "wardrobe:${it.remoteId}" }) { entry ->
                        FamilyAccessCard {
                            Text(familyDirectoryName(entry, content.wardrobes).ifBlank { stringResource(R.string.family_unnamed) }, style = MaterialTheme.typography.titleMedium)
                            if (entry.ownerEmail.isNotBlank()) Text(stringResource(R.string.family_invite_from, entry.ownerEmail))
                            Text(accessRole(entry.role))
                            if (entry.ready) OutlinedButton(onClick = { reading = entry.remoteId; vm.readFamily(entry.remoteId, refresh = true) }, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
                                Text(stringResource(R.string.family_read_open))
                            } else Text(stringResource(R.string.family_read_empty_head))
                            if (entry.role == "owner") TextButton(onClick = { vm.familyAccess.manage(entry.remoteId) }, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
                                Text(stringResource(R.string.family_manage))
                            } else {
                                val leaveText = stringResource(R.string.family_leave_confirm)
                                TextButton(onClick = { val request = UUID.randomUUID().toString(); confirmation = FamilyAccessConfirmation(leaveText) { vm.familyAccess.leave(entry.remoteId, request) } }, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
                                    Text(stringResource(R.string.family_leave))
                                }
                            }
                        }
                    }
                    if (managed != null) {
                        item {
                            HorizontalDivider()
                            Text(stringResource(R.string.family_manage_name, familyDirectoryName(managed, content.wardrobes).ifBlank { stringResource(R.string.family_unnamed) }), style = MaterialTheme.typography.titleMedium)
                            FamilyInviteForm(vm, managed, familyDirectoryName(managed, content.wardrobes), state.completedRequest, enabled)
                        }
                        if (state.members.isEmpty()) item { Text(stringResource(R.string.family_no_members)) }
                        items(state.members, key = { "member:${it.uid}" }) { member ->
                            FamilyAccessCard {
                                Text(member.email); Text(accessRole(member.role))
                                val nextRole = if (member.role == "viewer") "editor" else "viewer"
                                val changeText = stringResource(R.string.family_role_confirm, member.email, accessRole(nextRole))
                                OutlinedButton(onClick = { val request = UUID.randomUUID().toString(); confirmation = FamilyAccessConfirmation(changeText) { vm.familyAccess.change(managed.remoteId, member, nextRole, request) } }, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
                                    Text(stringResource(R.string.family_change_role, accessRole(nextRole)))
                                }
                                val revokeText = stringResource(R.string.family_revoke_confirm, member.email)
                                TextButton(onClick = { val request = UUID.randomUUID().toString(); confirmation = FamilyAccessConfirmation(revokeText) { vm.familyAccess.change(managed.remoteId, member, "revoked", request) } }, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
                                    Text(stringResource(R.string.family_revoke))
                                }
                            }
                        }
                        items(state.pending, key = { "pending:${it.inviteId}" }) { invite ->
                            FamilyAccessCard {
                                Text(invite.recipientEmail); Text(accessRole(invite.role))
                                Text(stringResource(R.string.family_invite_pending))
                                TextButton(onClick = { vm.familyAccess.cancel(invite) }, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
                                    Text(stringResource(R.string.family_cancel_invite))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    confirmation?.let { current ->
        AlertDialog(onDismissRequest = { confirmation = null }, text = { Text(current.text) },
            confirmButton = { TextButton(onClick = { confirmation = null; current.action() }, enabled = enabled) { Text(stringResource(R.string.family_confirm_action)) } },
            dismissButton = { TextButton(onClick = { confirmation = null }) { Text(stringResource(R.string.cancel)) } })
    }
    if (reading.isNotBlank()) FamilyReaderDialog(vm, reading) { reading = "" }
}

@Composable
private fun FamilyAccessCard(content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp), content = content) }
}

@Composable
private fun FamilyInviteForm(vm: WardrobeViewModel, entry: FamilyDirectoryEntry, initialName: String, completed: String, enabled: Boolean) {
    var email by rememberSaveable(entry.remoteId) { mutableStateOf("") }
    var name by rememberSaveable(entry.remoteId) { mutableStateOf(initialName) }
    var editor by rememberSaveable(entry.remoteId) { mutableStateOf(false) }
    var request by rememberSaveable(entry.remoteId) { mutableStateOf(UUID.randomUUID().toString()) }
    LaunchedEffect(completed) {
        if (completed == request) { email = ""; request = UUID.randomUUID().toString() }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.family_invite_hint), style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(value = name, onValueChange = { name = it.take(80); request = UUID.randomUUID().toString() }, enabled = enabled,
            label = { Text(stringResource(R.string.family_shared_name)) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(value = email, onValueChange = { email = it.take(254); request = UUID.randomUUID().toString() }, enabled = enabled,
            label = { Text(stringResource(R.string.auth_email)) }, modifier = Modifier.fillMaxWidth(), singleLine = true,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Email))
        OutlinedButton(onClick = { editor = !editor; request = UUID.randomUUID().toString() }, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.family_change_role, accessRole(if (editor) "viewer" else "editor")))
        }
        Text(accessRole(if (editor) "editor" else "viewer"))
        if (editor) Text(stringResource(R.string.family_editor_hint), style = MaterialTheme.typography.bodySmall)
        Button(onClick = { vm.familyAccess.invite(entry.remoteId, request, email, if (editor) "editor" else "viewer", name) },
            enabled = enabled && email.contains('@') && name.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.family_invite_create))
        }
    }
}
