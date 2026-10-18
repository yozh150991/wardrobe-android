package com.familywardrobe.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Checkroom
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
internal fun AuthRoot(vm: AuthViewModel, appearance: AppearanceViewModel) {
    val state by vm.ui.collectAsStateWithLifecycle()
    val holder = rememberSaveableStateHolder()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, vm) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_START) vm.checkSession() }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    var previousSessionId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(state.session?.id) {
        previousSessionId?.takeIf { it != state.session?.id }?.let(holder::removeState)
        previousSessionId = state.session?.id
    }
    val session = state.session
    if (session != null && !state.locked) {
        holder.SaveableStateProvider(session.id) {
            val factory = remember(session.id) { object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    require(modelClass == WardrobeViewModel::class.java)
                    return WardrobeViewModel(vm.getApplication(), session) as T
                }
            } }
            val wardrobe: WardrobeViewModel = viewModel(viewModelStoreOwner = session, factory = factory)
            WardrobeApp(wardrobe, appearance, session.identity.email, vm::signOut, vm::checkSession)
        }
    } else AuthScreen(vm, state)
}

@Composable
private fun AuthScreen(vm: AuthViewModel, state: AuthUiState) {
    val request by vm.requests.collectAsStateWithLifecycle()
    val online by vm.network.online.collectAsStateWithLifecycle()
    val language = LocalConfiguration.current.locales[0].language
    var password by remember(state.page, state.formEpoch) { mutableStateOf("") }
    var confirmation by remember(state.page, state.formEpoch) { mutableStateOf("") }
    var reveal by remember(state.page, state.formEpoch) { mutableStateOf(false) }
    var confirmSwitch by remember { mutableStateOf(false) }
    val busy = request.busy || state.page == AuthPage.OPENING
    val emailFocus = remember { FocusRequester() }
    val passwordFocus = remember { FocusRequester() }
    val confirmationFocus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    fun submit() {
        if (vm.submit(password, confirmation, language)) { reveal = false; focusManager.clearFocus() }
        else {
            val invalid = vm.ui.value
            when {
                invalid.emailError -> emailFocus.requestFocus()
                invalid.passwordError -> passwordFocus.requestFocus()
                invalid.confirmationError -> confirmationFocus.requestFocus()
            }
        }
    }
    BackHandler((request.busy && !request.cancelling) || state.page != AuthPage.LOGIN || state.locked) {
        if (request.cancelling) return@BackHandler
        if (request.busy || state.page in setOf(AuthPage.OPENING, AuthPage.CLAIM, AuthPage.STORAGE_ERROR)) vm.cancel()
        else if (state.page != AuthPage.LOGIN) vm.show(AuthPage.LOGIN)
        else confirmSwitch = true
    }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize().safeDrawingPadding().imePadding(), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 480.dp).fillMaxHeight().verticalScroll(rememberScrollState()).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Spacer(Modifier.height(16.dp))
                Icon(if (state.page == AuthPage.REAUTH) Icons.Outlined.Lock else Icons.Outlined.Checkroom, null, modifier = Modifier.size(44.dp), tint = MaterialTheme.colorScheme.primary)
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleMedium)
                val title = when (state.page) {
                    AuthPage.SIGNUP -> R.string.auth_signup_title
                    AuthPage.RESET -> R.string.auth_reset_title
                    AuthPage.RESET_SENT -> R.string.auth_reset_sent_title
                    AuthPage.CLAIM -> R.string.auth_claim_title
                    AuthPage.STORAGE_ERROR -> R.string.auth_storage_title
                    AuthPage.OPENING -> R.string.auth_opening
                    AuthPage.REAUTH -> R.string.auth_reauth_title
                    AuthPage.LOGIN -> if (state.locked) R.string.auth_reauth_title else R.string.auth_login_title
                }
                Text(stringResource(title), style = MaterialTheme.typography.headlineLarge)
                when (state.page) {
                    AuthPage.REAUTH -> {
                        Text(stringResource(R.string.reauth_draft_hint), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Button(onClick = { vm.show(AuthPage.LOGIN) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.auth_sign_in)) }
                        if (!online) AuthMessage(R.string.offline_login_hint, error = true)
                    }
                    AuthPage.OPENING -> {
                        CircularProgressIndicator()
                        Text(stringResource(R.string.auth_opening_hint))
                        TextButton(onClick = vm::cancel) { Text(stringResource(R.string.cancel)) }
                    }
                    AuthPage.CLAIM -> {
                        Text(stringResource(R.string.auth_claim_hint, state.email))
                        Button(onClick = { vm.chooseLegacy(true) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.auth_claim)) }
                        OutlinedButton(onClick = { vm.chooseLegacy(false) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.auth_start_empty)) }
                        TextButton(onClick = vm::cancel) { Text(stringResource(R.string.cancel)) }
                    }
                    AuthPage.STORAGE_ERROR -> {
                        AuthMessage(R.string.auth_storage_error, error = true)
                        Button(onClick = vm::retryStorage, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.retry)) }
                        TextButton(onClick = vm::cancel) { Text(stringResource(R.string.cancel)) }
                    }
                    AuthPage.RESET_SENT -> {
                        Icon(Icons.Outlined.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                        AuthMessage(R.string.auth_reset_sent)
                        TextButton(onClick = { vm.show(AuthPage.RESET) }) { Text(stringResource(R.string.auth_another_email)) }
                        Button(onClick = { vm.show(AuthPage.LOGIN) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.auth_back_to_login)) }
                    }
                    else -> {
                        Text(stringResource(when {
                            state.page == AuthPage.RESET -> R.string.auth_reset_hint
                            state.locked -> R.string.auth_reauth_hint
                            state.page == AuthPage.SIGNUP -> R.string.auth_signup_hint
                            else -> R.string.auth_login_hint
                        }), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (!vm.configured) AuthMessage(R.string.auth_not_configured, error = true)
                        if (!online) AuthMessage(R.string.offline_login_hint, error = true)
                        OutlinedTextField(value = state.email, onValueChange = vm::editEmail,
                            label = { Text(stringResource(R.string.auth_email)) }, singleLine = true,
                            readOnly = state.session != null, enabled = !busy,
                            isError = state.emailError, modifier = Modifier.fillMaxWidth().focusRequester(emailFocus).semantics {
                                contentType = if (state.page == AuthPage.SIGNUP) ContentType.NewUsername + ContentType.EmailAddress
                                    else ContentType.Username + ContentType.EmailAddress
                            },
                            supportingText = if (state.emailError) {{ Text(stringResource(R.string.auth_email_error)) }} else null,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email,
                                imeAction = if (state.page == AuthPage.RESET) ImeAction.Done else ImeAction.Next),
                            keyboardActions = KeyboardActions(onDone = { submit() }))
                        if (state.page != AuthPage.RESET) {
                            OutlinedTextField(value = password, onValueChange = { password = it; vm.clearFieldErrors() },
                                label = { Text(stringResource(R.string.auth_password)) }, singleLine = true,
                                enabled = !busy, isError = state.passwordError, modifier = Modifier.fillMaxWidth().focusRequester(passwordFocus).semantics {
                                    contentType = if (state.page == AuthPage.SIGNUP) ContentType.NewPassword else ContentType.Password
                                },
                                visualTransformation = if (reveal) VisualTransformation.None else PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password,
                                    imeAction = if (state.page == AuthPage.SIGNUP) ImeAction.Next else ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = { submit() }),
                                supportingText = if (state.passwordError || state.page == AuthPage.SIGNUP) {{
                                    Text(stringResource(if (state.page == AuthPage.SIGNUP) R.string.auth_password_hint else R.string.auth_password_required))
                                }} else null,
                                trailingIcon = { IconButton(onClick = { reveal = !reveal }, enabled = !busy) {
                                    Icon(if (reveal) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                        stringResource(if (reveal) R.string.auth_hide_password else R.string.auth_show_password))
                                } })
                        }
                        if (state.page == AuthPage.SIGNUP) OutlinedTextField(value = confirmation,
                            onValueChange = { confirmation = it; vm.clearFieldErrors() },
                            label = { Text(stringResource(R.string.auth_confirm_password)) }, singleLine = true,
                            enabled = !busy, isError = state.confirmationError,
                            modifier = Modifier.fillMaxWidth().focusRequester(confirmationFocus).semantics { contentType = ContentType.NewPassword },
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { submit() }),
                            supportingText = if (state.confirmationError) {{ Text(stringResource(R.string.auth_password_mismatch)) }} else null)
                        state.error?.let { AuthMessage(it, error = true) }
                        if (busy) {
                            LinearProgressIndicator(Modifier.fillMaxWidth())
                            AuthMessage(if (request.cancelling) R.string.auth_cancelling else R.string.working)
                            if (!request.cancelling) TextButton(onClick = vm::cancel) { Text(stringResource(R.string.cancel)) }
                        }
                        Button(onClick = ::submit, enabled = !busy && vm.configured, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                            Text(stringResource(when (state.page) {
                                AuthPage.SIGNUP -> R.string.auth_create_account
                                AuthPage.RESET -> R.string.auth_send_link
                                else -> R.string.auth_sign_in
                            }))
                        }
                        if (state.page == AuthPage.LOGIN) {
                            TextButton(onClick = { vm.show(AuthPage.RESET) }, enabled = !busy) { Text(stringResource(R.string.auth_forgot_password)) }
                            if (!state.locked) OutlinedButton(onClick = { vm.show(AuthPage.SIGNUP) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                                Text(stringResource(R.string.auth_create_account))
                            }
                        } else TextButton(onClick = { vm.show(AuthPage.LOGIN) }, enabled = !busy) { Text(stringResource(R.string.auth_back_to_login)) }
                        if (state.locked) TextButton(onClick = { confirmSwitch = true }, enabled = !busy) { Text(stringResource(R.string.auth_switch_account)) }
                        Text(stringResource(R.string.auth_data_hint), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
    if (confirmSwitch) AlertDialog(onDismissRequest = { confirmSwitch = false },
        title = { Text(stringResource(R.string.auth_switch_account)) },
        text = { Text(stringResource(R.string.auth_discard_session)) },
        confirmButton = { TextButton(onClick = { confirmSwitch = false; vm.signOut() }) { Text(stringResource(R.string.auth_sign_out)) } },
        dismissButton = { TextButton(onClick = { confirmSwitch = false }) { Text(stringResource(R.string.cancel)) } })
}

@Composable
private fun AuthMessage(message: Int, error: Boolean = false) {
    Text(stringResource(message), modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
}
