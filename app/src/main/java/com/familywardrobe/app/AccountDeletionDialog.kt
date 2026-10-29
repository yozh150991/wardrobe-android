package com.familywardrobe.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** FAM-04 (0.14.25): full account deletion from Settings. */
@Composable
internal fun AccountDeletionControl(vm: WardrobeViewModel, accountEmail: String, onDeleted: () -> Unit) {
    val busy by vm.busy.collectAsStateWithLifecycle()
    var open by rememberSaveable(vm.sessionToken) { mutableStateOf(false) }
    OutlinedButton(onClick = { open = true }, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
        Text(stringResource(R.string.account_delete))
    }
    if (open) AccountDeletionDialog(vm, accountEmail, onDeleted) { open = false }
}

@Composable
private fun AccountDeletionDialog(vm: WardrobeViewModel, accountEmail: String, onDeleted: () -> Unit, dismiss: () -> Unit) {
    val controller = vm.accountDeletion
    val state by controller.state.collectAsStateWithLifecycle()
    val content by vm.state.collectAsStateWithLifecycle()
    // The password stays in this form only: never in saved state or a view model.
    var password by remember { mutableStateOf("") }
    var understood by rememberSaveable(vm.sessionToken) { mutableStateOf(false) }
    var choices by remember { mutableStateOf(mapOf<String, String>()) }
    LaunchedEffect(vm.sessionToken) { controller.load() }
    LaunchedEffect(state.done) { if (state.done) onDeleted() }
    LaunchedEffect(state.problem) {
        if (state.problem in setOf(AccountDeletionProblem.PASSWORD, AccountDeletionProblem.REAUTH)) password = ""
    }
    val close = { if (!state.running && !state.done) { controller.reset(); dismiss() } }
    val owners = FamilyOwnership.owners(state.owned, choices)
    val working = state.loading || state.running || state.done
    Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.account_delete_title), style = MaterialTheme.typography.titleLarge)
                if (working) LinearProgressIndicator(Modifier.fillMaxWidth())
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item {
                        Text(stringResource(R.string.account_delete_intro, accountEmail))
                        Spacer(Modifier.height(6.dp))
                        Text(stringResource(R.string.account_delete_keeps), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (state.loaded) {
                        item { Text(stringResource(R.string.account_delete_owned), style = MaterialTheme.typography.titleMedium) }
                        if (state.owned.isEmpty()) item { Text(stringResource(R.string.account_delete_no_owned)) }
                        items(state.owned, key = { "owned:${it.remoteId}" }) { wardrobe ->
                            Card(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(12.dp).selectableGroup(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    val local = content.wardrobes.find { it.id == wardrobe.wardrobeId }?.displayName().orEmpty()
                                    Text(wardrobe.name.ifBlank { local }.ifBlank { stringResource(R.string.family_unnamed) },
                                        style = MaterialTheme.typography.titleMedium)
                                    when (wardrobe.editors.size) {
                                        0 -> Text(stringResource(R.string.account_delete_no_editors), color = MaterialTheme.colorScheme.error)
                                        1 -> Text(stringResource(R.string.account_delete_single, wardrobe.editors.single().email))
                                        else -> {
                                            Text(stringResource(R.string.account_delete_choose))
                                            wardrobe.editors.forEach { editor ->
                                                val selected = choices[wardrobe.remoteId] == editor.uid
                                                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(selected, enabled = !working, role = Role.RadioButton) {
                                                    choices = choices + (wardrobe.remoteId to editor.uid)
                                                }, verticalAlignment = Alignment.CenterVertically) {
                                                    RadioButton(selected = selected, onClick = null, enabled = !working)
                                                    Spacer(Modifier.width(8.dp)); Text(editor.email)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        if (state.memberships > 0) item { Text(stringResource(R.string.account_delete_memberships, state.memberships)) }
                    }
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            state.problem?.let { problem ->
                                Text(stringResource(when (problem) {
                                    AccountDeletionProblem.PASSWORD -> R.string.account_delete_wrong_password
                                    AccountDeletionProblem.RATE -> R.string.auth_rate_limited
                                    AccountDeletionProblem.REAUTH -> R.string.account_delete_reauth
                                    AccountDeletionProblem.CHOICE -> R.string.account_delete_changed
                                    AccountDeletionProblem.EMAIL -> R.string.account_delete_email
                                    AccountDeletionProblem.CONFIG -> R.string.account_delete_config
                                    AccountDeletionProblem.NETWORK -> R.string.account_delete_network
                                    AccountDeletionProblem.RETRY -> R.string.account_delete_retry
                                }), color = MaterialTheme.colorScheme.error, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                                if (problem == AccountDeletionProblem.EMAIL) TextButton(onClick = vm::sendFamilyVerification, enabled = !working) {
                                    Text(stringResource(R.string.family_send_email))
                                }
                                if (!state.loaded && problem != AccountDeletionProblem.EMAIL) OutlinedButton(onClick = { controller.load() }, enabled = !working) {
                                    Text(stringResource(R.string.family_read_refresh))
                                }
                            }
                            if (state.loaded) {
                                OutlinedTextField(value = password, onValueChange = { password = it }, enabled = !working,
                                    label = { Text(stringResource(R.string.auth_password)) }, singleLine = true,
                                    isError = state.problem == AccountDeletionProblem.PASSWORD,
                                    visualTransformation = PasswordVisualTransformation(),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                    modifier = Modifier.fillMaxWidth().semantics { contentType = ContentType.Password })
                                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(understood, enabled = !working, role = Role.Checkbox) { understood = it },
                                    verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(checked = understood, onCheckedChange = null, enabled = !working)
                                    Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.account_delete_understood))
                                }
                                if (owners == null) Text(stringResource(R.string.account_delete_choose_all), style = MaterialTheme.typography.bodySmall)
                                Button(onClick = { controller.delete(password, choices) },
                                    enabled = !working && understood && password.isNotEmpty() && owners != null,
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error,
                                        contentColor = MaterialTheme.colorScheme.onError),
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                                    Text(stringResource(R.string.account_delete_action))
                                }
                            }
                            TextButton(onClick = close, enabled = !state.running && !state.done, modifier = Modifier.fillMaxWidth()) {
                                Text(stringResource(R.string.cancel))
                            }
                        }
                    }
                }
            }
        }
    }
}
