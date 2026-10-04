package com.aba.medirecord.ui.navigation

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.aba.medirecord.data.local.TokenManager
import com.aba.medirecord.ui.auth.AuthScreen
import com.aba.medirecord.ui.home.HomeScreen

object Screen {
    const val AUTH = "auth"
    const val HOME = "home"
}

@Composable
fun AppNavGraph(
    tokenManager: TokenManager,
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    startDestination: String = if (tokenManager.isLoggedIn()) Screen.HOME else Screen.AUTH,
) {
    val context = LocalContext.current

    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier
    ) {
        composable(Screen.AUTH) {
            AuthScreen(
                onAuthSuccess = { message ->
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                    navController.navigate(Screen.HOME) {
                        popUpTo(Screen.AUTH) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.HOME) {
            HomeScreen(
                userEmail = tokenManager.getUserEmail(),
                onLogout = {
                    tokenManager.clearSession()
                    Toast.makeText(context, "Sesión cerrada", Toast.LENGTH_SHORT).show()
                    navController.navigate(Screen.AUTH) {
                        popUpTo(Screen.HOME) { inclusive = true }
                    }
                }
            )
        }
    }
}
