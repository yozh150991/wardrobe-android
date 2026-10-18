package com.familywardrobe.app

import android.os.Bundle
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
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

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) receiveShare(intent)
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
