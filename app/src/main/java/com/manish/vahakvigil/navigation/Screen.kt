package com.manish.vahakvigil.navigation

sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object EmergencyContacts : Screen("emergency_contacts")
    data object Settings : Screen("settings")
    data object Profile : Screen("profile")
}
