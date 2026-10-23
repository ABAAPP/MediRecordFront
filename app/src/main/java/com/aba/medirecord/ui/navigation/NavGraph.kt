package com.aba.medirecord.ui.navigation

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.aba.medirecord.data.local.TokenManager
import com.aba.medirecord.ui.auth.AuthScreen
import com.aba.medirecord.ui.home.HomeScreen
import com.aba.medirecord.ui.medication.AddMedicationScreen
import com.aba.medirecord.ui.reminders.RemindersScreen
import com.aba.medirecord.ui.schedule.ScheduleConfigScreen

object Screen {
    const val AUTH = "auth"
    const val HOME = "home"
    const val ADD_MEDICATION = "add_medication"
    const val REMINDERS = "reminders"
    const val CONFIGURE_SCHEDULE = "configure_schedule"
    const val CONFIGURE_SCHEDULE_ROUTE = "configure_schedule?medicationId={medicationId}"

    fun configureScheduleRoute(medicationId: String? = null): String {
        return if (medicationId.isNullOrBlank()) {
            CONFIGURE_SCHEDULE
        } else {
            "$CONFIGURE_SCHEDULE?medicationId=$medicationId"
        }
    }
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
                onNavigateToAddMedication = {
                    navController.navigate(Screen.ADD_MEDICATION)
                },
                onNavigateToConfigureSchedule = { medicationId ->
                    navController.navigate(Screen.configureScheduleRoute(medicationId))
                },
                onNavigateToReminders = {
                    navController.navigate(Screen.REMINDERS)
                },
                onLogout = {
                    tokenManager.clearSession()
                    Toast.makeText(context, "Sesión cerrada", Toast.LENGTH_SHORT).show()
                    navController.navigate(Screen.AUTH) {
                        popUpTo(Screen.HOME) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.ADD_MEDICATION) {
            AddMedicationScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onMedicationAdded = {
                    Toast.makeText(context, "Medicamento guardado en tu agenda", Toast.LENGTH_SHORT).show()
                    navController.popBackStack()
                }
            )
        }

        composable(Screen.REMINDERS) {
            RemindersScreen(
                userEmail = tokenManager.getUserEmail(),
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToConfigureSchedule = { medicationId ->
                    navController.navigate(Screen.configureScheduleRoute(medicationId))
                }
            )
        }

        composable(
            route = Screen.CONFIGURE_SCHEDULE_ROUTE,
            arguments = listOf(
                navArgument("medicationId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val medicationId = backStackEntry.arguments?.getString("medicationId")
            ScheduleConfigScreen(
                preselectedMedicationId = medicationId,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onScheduleSaved = {
                    Toast.makeText(context, "Horario configurado correctamente", Toast.LENGTH_SHORT).show()
                    navController.popBackStack()
                }
            )
        }
    }
}
