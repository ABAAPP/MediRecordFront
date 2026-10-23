package com.aba.medirecord

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.aba.medirecord.data.local.TokenManager
import com.aba.medirecord.notifications.ReminderNotificationManager
import com.aba.medirecord.ui.navigation.AppNavGraph
import com.aba.medirecord.ui.theme.MediRecordTheme

class MainActivity : ComponentActivity() {

    private val requestNotificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { _ ->
            ReminderNotificationManager.configureNotificationChannel(this)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        ReminderNotificationManager.configureNotificationChannel(this)
        requestNotificationPermissionIfNeeded()

        val tokenManager = TokenManager(this)

        // Simulación: Guardar sesión previa por defecto para ingresar directo a la pantalla principal
        if (!tokenManager.isLoggedIn()) {
            tokenManager.saveToken(
                token = "jwt_demo_token_12345",
                email = "paciente@medirecord.com"
            )
        }

        setContent {
            MediRecordTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    AppNavGraph(
                        tokenManager = tokenManager,
                        modifier = Modifier.padding(innerPadding),
                    )
                }
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permissionStatus = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            )
            if (permissionStatus != PackageManager.PERMISSION_GRANTED) {
                requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}
