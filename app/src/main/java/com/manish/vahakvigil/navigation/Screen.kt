package com.manish.vahakvigil.navigation

sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object EmergencyContacts : Screen("emergency_contacts")
    data object MedicalProfile : Screen("medical_profile")
    data object RideHistory : Screen("ride_history")
    data object Settings : Screen("settings")
    data object Profile : Screen("profile")
}
