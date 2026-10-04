package com.aba.medirecord

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.aba.medirecord.data.local.TokenManager
import com.aba.medirecord.data.network.ApiClient
import com.aba.medirecord.ui.auth.AuthScreen
import com.aba.medirecord.ui.home.HomeScreen
import com.aba.medirecord.ui.theme.MediRecordTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ApiClient.init(applicationContext)

        val tokenManager = TokenManager(this)

        setContent {
            MediRecordTheme {
                var isLoggedIn by remember { mutableStateOf(tokenManager.isLoggedIn()) }

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    if (isLoggedIn) {
                        HomeScreen(
                            userEmail = tokenManager.getUserEmail(),
                            onLogout = {
                                tokenManager.clearSession()
                                isLoggedIn = false
                                Toast.makeText(this, "Sesión cerrada", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.padding(innerPadding)
                        )
                    } else {
                        AuthScreen(
                            onAuthSuccess = { message ->
                                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                                isLoggedIn = tokenManager.isLoggedIn()
                            },
                            modifier = Modifier.padding(innerPadding)
                        )
                    }
                }
            }
        }
    }
}
