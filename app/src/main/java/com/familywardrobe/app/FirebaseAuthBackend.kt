package com.familywardrobe.app

import android.content.Context
import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal data class AuthIdentity(val project: String, val uid: String, val email: String) {
    val key: String get() = AuthRules.accountKey(project, uid)
}
internal enum class AuthAction { LOGIN, SIGNUP, RESET }
internal data class AuthReply(val identity: AuthIdentity? = null, val error: Int? = null)
internal data class AuthRequestState(val busy: Boolean = false, val cancelling: Boolean = false)

/** The SDK manages credentials/tokens. No Activity-bound listener: every late result is settled. */
internal class FirebaseAuthBackend(context: Context) {
    private val firebase = runCatching {
        val app = FirebaseApp.initializeApp(context.applicationContext)
        app?.takeIf { !it.options.projectId.isNullOrBlank() }?.let { FirebaseAuth.getInstance(it) }
    }.getOrNull()
    val configured: Boolean get() = firebase != null
    private val gate = AuthAttemptGate()
    private val _requests = MutableStateFlow(AuthRequestState())
    val requests = _requests.asStateFlow()

    fun signedInIdentity(): AuthIdentity? {
        val auth = firebase ?: return null
        val user = auth.currentUser ?: return null
        return AuthIdentity(requireNotNull(auth.app.options.projectId), user.uid, user.email.orEmpty())
    }
    fun addListener(listener: FirebaseAuth.AuthStateListener) { firebase?.addAuthStateListener(listener) }
    fun removeListener(listener: FirebaseAuth.AuthStateListener) { firebase?.removeAuthStateListener(listener) }

    fun submit(action: AuthAction, email: String, password: String, language: String, reply: (AuthReply) -> Unit) {
        val auth = firebase ?: run { reply(AuthReply(error = R.string.auth_not_configured)); return }
        val attempt = gate.begin()
        if (attempt == 0L) return
        _requests.value = AuthRequestState(busy = true)
        // No account state listener can unlock UI. Only this accepted request may do so.
        try {
            auth.setLanguageCode(language.takeIf { it in setOf("uk", "en", "pl") } ?: "en")
            val task: Task<*> = when (action) {
                AuthAction.LOGIN -> auth.signInWithEmailAndPassword(email, password)
                AuthAction.SIGNUP -> auth.createUserWithEmailAndPassword(email, password)
                AuthAction.RESET -> auth.sendPasswordResetEmail(email)
            }
            task.addOnCompleteListener { result ->
                val accepted = gate.finish(attempt)
                if (!accepted) auth.signOut()
                else if (result.isSuccessful) {
                    if (action == AuthAction.RESET) reply(AuthReply())
                    else reply(signedInIdentity()?.let { AuthReply(identity = it) }
                        ?: AuthReply(error = R.string.auth_unavailable))
                } else {
                    // Deliberately identical reset response for an unknown/disabled account.
                    val exception = result.exception
                    if (action == AuthAction.RESET && exception is FirebaseAuthInvalidUserException) reply(AuthReply())
                    else reply(AuthReply(error = errorMessage(action, exception)))
                }
                _requests.value = AuthRequestState()
            }
        } catch (e: Exception) {
            if (gate.finish(attempt)) reply(AuthReply(error = errorMessage(action, e)))
            else auth.signOut()
            _requests.value = AuthRequestState()
        }
    }
    fun cancel() {
        gate.cancel()
        firebase?.signOut()
        _requests.value = AuthRequestState(gate.isBusy(), gate.isCancelling())
    }
    fun signOut() = cancel()

    fun checkSession(identity: AuthIdentity, result: (Boolean) -> Unit) {
        val user = firebase?.currentUser
        if (user == null || signedInIdentity()?.key != identity.key) { result(false); return }
        user.getIdToken(true).addOnCompleteListener { task ->
            // Keep an authenticated local session during network failure. Never use this path for new login.
            val code = (task.exception as? FirebaseAuthException)?.errorCode
            val invalid = task.exception is FirebaseAuthInvalidUserException ||
                code in setOf("ERROR_USER_TOKEN_EXPIRED", "ERROR_INVALID_USER_TOKEN", "ERROR_USER_DISABLED", "ERROR_USER_NOT_FOUND")
            result(!invalid)
        }
    }

    private fun errorMessage(action: AuthAction, error: Exception?): Int = when (error) {
        is FirebaseNetworkException -> R.string.auth_network_error
        is FirebaseTooManyRequestsException -> R.string.auth_rate_limited
        is FirebaseAuthWeakPasswordException -> R.string.auth_password_policy
        is FirebaseAuthUserCollisionException -> R.string.auth_signup_failed
        is FirebaseAuthInvalidCredentialsException, is FirebaseAuthInvalidUserException ->
            if (action == AuthAction.LOGIN) R.string.auth_credentials_error else R.string.auth_signup_failed
        else -> R.string.auth_unavailable
    }
}
