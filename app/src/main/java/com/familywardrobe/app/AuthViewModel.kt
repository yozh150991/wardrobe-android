package com.familywardrobe.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal enum class AuthPage { LOGIN, SIGNUP, RESET, RESET_SENT, OPENING, CLAIM, STORAGE_ERROR, REAUTH }
internal data class AuthUiState(
    val page: AuthPage = AuthPage.LOGIN,
    val email: String = "",
    val error: Int? = null,
    val emailError: Boolean = false,
    val passwordError: Boolean = false,
    val confirmationError: Boolean = false,
    val formEpoch: Int = 0,
    val session: AccountSession? = null,
    val locked: Boolean = false
)

internal class AuthViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as WardrobeApplication
    private val backend = app.authBackend
    private val _ui = MutableStateFlow(AuthUiState())
    val ui = _ui.asStateFlow()
    val requests = backend.requests
    val configured = backend.configured
    val network = app.network
    private var pending: AuthIdentity? = null
    private var openingGeneration = 0
    private var checking = false
    private var sessionGeneration = 0
    private var cleared = false
    private val authListener = FirebaseAuth.AuthStateListener {
        val state = _ui.value
        if (!cleared && state.session != null && !state.locked && backend.signedInIdentity()?.key != state.session.identity.key) lockSession()
    }

    init {
        backend.addListener(authListener)
        // Restore only the SDK's existing identity; verify it before opening any private UI.
        backend.signedInIdentity()?.let { identity ->
            val generation = ++openingGeneration
            _ui.value = AuthUiState(page = AuthPage.OPENING, email = identity.email)
            backend.checkSession(identity) { valid ->
                if (!cleared && generation == openingGeneration) {
                    if (valid && backend.signedInIdentity()?.key == identity.key) {
                        pending = identity; openAccount()
                    } else {
                        backend.signOut()
                        _ui.value = AuthUiState(email = identity.email, error = R.string.auth_credentials_error)
                    }
                }
            }
        }
    }

    fun editEmail(value: String) {
        if (_ui.value.session != null || requests.value.busy) return
        _ui.value = _ui.value.copy(email = value.take(254), emailError = false, error = null)
    }
    fun clearFieldErrors() {
        _ui.value = _ui.value.copy(passwordError = false, confirmationError = false, error = null)
    }
    fun show(page: AuthPage) {
        if (requests.value.busy || page !in setOf(AuthPage.LOGIN, AuthPage.SIGNUP, AuthPage.RESET)) return
        if (page == AuthPage.SIGNUP && _ui.value.session != null) return
        _ui.value = _ui.value.copy(page = page, error = null, emailError = false, passwordError = false,
            confirmationError = false, formEpoch = _ui.value.formEpoch + 1)
    }

    /** Credentials remain only in the form and SDK request; never in saved state or this model. */
    fun submit(password: String, confirmation: String, language: String): Boolean {
        val state = _ui.value
        if (requests.value.busy || !configured) return false
        val action = when (state.page) {
            AuthPage.LOGIN -> AuthAction.LOGIN
            AuthPage.SIGNUP -> AuthAction.SIGNUP
            AuthPage.RESET -> AuthAction.RESET
            else -> return false
        }
        val email = AuthRules.email(state.email)
        val emailError = !AuthRules.validEmail(email)
        val passwordError = when (action) {
            AuthAction.LOGIN -> password.isEmpty()
            AuthAction.SIGNUP -> !AuthRules.validNewPassword(password)
            AuthAction.RESET -> false
        }
        val confirmationError = action == AuthAction.SIGNUP && password != confirmation
        _ui.value = state.copy(email = email, error = null, emailError = emailError,
            passwordError = passwordError, confirmationError = confirmationError)
        if (emailError || passwordError || confirmationError) return false
        backend.submit(action, email, password, language) { result ->
            if (result.error != null) _ui.value = _ui.value.copy(error = result.error, formEpoch = _ui.value.formEpoch + 1)
            else if (action == AuthAction.RESET) _ui.value = _ui.value.copy(page = AuthPage.RESET_SENT)
            else result.identity?.let { identity ->
                val existing = _ui.value.session
                if (existing != null) {
                    if (identity.key == existing.identity.key) {
                        sessionGeneration++
                        _ui.value = _ui.value.copy(page = AuthPage.LOGIN, locked = false, error = null)
                    }
                    else { backend.signOut(); _ui.value = _ui.value.copy(error = R.string.auth_wrong_account) }
                } else { pending = identity; openAccount() }
            }
        }
        return true
    }

    private fun openAccount(claim: Boolean? = null) {
        val identity = pending ?: return
        val generation = ++openingGeneration
        _ui.value = _ui.value.copy(page = AuthPage.OPENING, error = null)
        viewModelScope.launch {
            try {
                // A rapid return to the same UID must not race the old session's cache cleanup.
                app.accountClosures[identity.key]?.let { cleanup ->
                    cleanup.join()
                    if (app.accountClosures[identity.key] === cleanup) app.accountClosures.remove(identity.key)
                }
                if (generation != openingGeneration) return@launch
                val needsChoice = withContext(Dispatchers.IO) { app.accountRegistry.needsLegacyChoice(identity.key) }
                if (generation != openingGeneration) return@launch
                if (needsChoice && claim == null) {
                    _ui.value = _ui.value.copy(page = AuthPage.CLAIM)
                    return@launch
                }
                val location = withContext(Dispatchers.IO) { app.accountRegistry.location(identity.key, claim == true) }
                if (generation != openingGeneration) return@launch
                if (backend.signedInIdentity()?.key != identity.key) { cancel(); return@launch }
                val session = AccountSession(identity, app, location)
                pending = null
                sessionGeneration++
                _ui.value = AuthUiState(email = identity.email, session = session)
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) {
                if (generation == openingGeneration) _ui.value = _ui.value.copy(page = AuthPage.STORAGE_ERROR)
            }
        }
    }
    fun chooseLegacy(claim: Boolean) { if (_ui.value.page == AuthPage.CLAIM) openAccount(claim) }
    fun retryStorage() { if (_ui.value.page == AuthPage.STORAGE_ERROR) openAccount() }
    fun cancel() {
        openingGeneration++
        pending = null
        val old = _ui.value
        _ui.value = old.copy(page = AuthPage.LOGIN, error = null, emailError = false,
            passwordError = false, confirmationError = false, formEpoch = old.formEpoch + 1)
        backend.cancel()
    }
    fun signOut() {
        sessionGeneration++
        openingGeneration++
        pending = null
        val session = _ui.value.session
        _ui.value = AuthUiState(formEpoch = _ui.value.formEpoch + 1)
        backend.signOut()
        session?.close()
    }
    private fun lockSession() {
        val state = _ui.value
        val session = state.session ?: return
        if (state.locked || cleared) return
        sessionGeneration++
        _ui.value = state.copy(page = AuthPage.REAUTH, email = session.identity.email,
            locked = true, error = null, formEpoch = state.formEpoch + 1)
        backend.signOut()
    }
    fun checkSession() {
        val state = _ui.value
        val session = state.session ?: return
        if (state.locked || checking || requests.value.busy) return
        checking = true
        val generation = sessionGeneration
        backend.checkSession(session.identity) { valid ->
            checking = false
            if (!cleared && generation == sessionGeneration && !valid && _ui.value.session?.id == session.id && !_ui.value.locked) lockSession()
        }
    }
    override fun onCleared() {
        cleared = true
        sessionGeneration++
        openingGeneration++
        backend.removeListener(authListener)
        if (requests.value.busy) backend.cancel()
        _ui.value.session?.close()
    }
}
