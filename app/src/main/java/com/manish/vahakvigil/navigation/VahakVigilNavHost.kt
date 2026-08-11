package com.manish.vahakvigil.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.manish.vahakvigil.ui.screens.EmergencyContactsScreen
import com.manish.vahakvigil.ui.screens.HomeScreen
import com.manish.vahakvigil.ui.screens.MedicalProfileScreen
import com.manish.vahakvigil.ui.screens.ProfileScreen
import com.manish.vahakvigil.ui.screens.RideHistoryScreen
import com.manish.vahakvigil.ui.screens.SettingsScreen

@Composable
fun VahakVigilNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    isDarkTheme: Boolean,
    onThemeChange: (Boolean) -> Unit
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route,
        modifier = modifier
    ) {
        composable(Screen.Home.route) {
            HomeScreen(
                onStartMonitoringClick = {
                    // TODO: Add monitoring flow when sensor/business logic is ready.
                },
                onEmergencyContactsClick = {
                    navController.navigate(Screen.EmergencyContacts.route)
                },
                onMedicalClick = {
                    navController.navigate(Screen.MedicalProfile.route)
                },
                onHistoryClick = {
                    navController.navigate(Screen.RideHistory.route)
                },
                onSettingsClick = {
                    navController.navigate(Screen.Settings.route)
                },
                onProfileClick = {
                    navController.navigate(Screen.Profile.route)
                },
                isDarkTheme = isDarkTheme,
                onThemeChange = onThemeChange
            )
        }

        composable(Screen.EmergencyContacts.route) {
            EmergencyContactsScreen(
                onBackClick = { navController.popBackStack() }
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                onBackClick = { navController.popBackStack() },
                isDarkTheme = isDarkTheme,
                onThemeChange = onThemeChange
            )
        }

        composable(Screen.Profile.route) {
            ProfileScreen(
                onBackClick = { navController.popBackStack() }
            )
        }

        composable(Screen.MedicalProfile.route) {
            MedicalProfileScreen(
                onBackClick = { navController.popBackStack() }
            )
        }

        composable(Screen.RideHistory.route) {
            RideHistoryScreen(
                onBackClick = { navController.popBackStack() }
            )
        }
    }
}
