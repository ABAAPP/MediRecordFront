package com.aba.medirecord

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import com.aba.medirecord.data.local.TokenManager
import com.aba.medirecord.ui.navigation.AppNavGraph
import com.aba.medirecord.ui.theme.MediRecordTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

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
}
