package com.familywardrobe.app

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.familywardrobe.app.auth.AuthRoot
import com.familywardrobe.app.auth.AuthViewModel
import com.familywardrobe.app.ui.AppearanceSettings
import com.familywardrobe.app.ui.AppearanceViewModel
import com.familywardrobe.app.ui.WardrobeTheme

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) { receiveShare(intent); receiveWidget(intent) }
        // Root Back keeps the task like Android 12+ does: finishing the Activity would clear the
        // account session and cancel running exports, family transfers and photo work on Android 8–11.
        // Compose BackHandlers are registered later and take priority for nested screens and dialogs.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { moveTaskToBack(true) }
        })
        enableEdgeToEdge()
        setContent {
            val vm: AppearanceViewModel = viewModel()
            val auth: AuthViewModel = viewModel()
            val appearance by vm.appearance.collectAsStateWithLifecycle()
            val settings = appearance ?: AppearanceSettings()
            val dark = settings.mode.usesDark(isSystemInDarkTheme())
            WardrobeTheme(settings) {
                val navigationColor = MaterialTheme.colorScheme.surface.toArgb()
                SideEffect {
                    enableEdgeToEdge(
                        statusBarStyle = if (dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                            else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
                        navigationBarStyle = if (dark) SystemBarStyle.dark(navigationColor)
                            else SystemBarStyle.light(navigationColor, navigationColor)
                    )
                }
                if (appearance == null) Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                } else AuthRoot(auth, vm)
            }
        }
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        receiveShare(intent)
        receiveWidget(intent)
    }
    /** WID-01: a widget tap opens the outfit or the daily picks, only for the account the widget was drawn for. */
    private fun receiveWidget(value: Intent) {
        val kind = when (value.action) { TodayWidgets.ACTION_OPEN -> WidgetRequest.OUTFIT; TodayWidgets.ACTION_DAILY -> WidgetRequest.DAILY; else -> return }
        val app = application as WardrobeApplication
        val account = value.getStringExtra(TodayWidgets.EXTRA_ACCOUNT).orEmpty()
        if (account.isNotEmpty() && account == app.authBackend.signedInIdentity()?.key)
            app.widgetRequests.value = WidgetRequest(kind, value.getStringExtra(TodayWidgets.EXTRA_OUTFIT).orEmpty())
        intent = Intent(this, MainActivity::class.java).setAction(Intent.ACTION_MAIN)
    }
    private fun receiveShare(value: Intent) {
        if (value.action != Intent.ACTION_SEND) return
        val app = application as WardrobeApplication
        val owner = app.authBackend.signedInIdentity()?.key.orEmpty()
        if (!app.sharedImports.receive(value, owner)) Toast.makeText(this, R.string.shop_share_failed, Toast.LENGTH_LONG).show()
        // Do not replay the payload after an ordinary configuration change.
        intent = Intent(this, MainActivity::class.java).setAction(Intent.ACTION_MAIN)
    }
}
