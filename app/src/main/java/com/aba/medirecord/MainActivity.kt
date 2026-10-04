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
