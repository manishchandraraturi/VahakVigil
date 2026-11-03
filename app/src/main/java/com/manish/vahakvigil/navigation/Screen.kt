package com.manish.vahakvigil.navigation

sealed class Screen(val route: String) {
    data object Startup : Screen("startup")
    data object Onboarding : Screen("onboarding")
    data object Readiness : Screen("readiness")
    data object Home : Screen("home")
    data object EmergencyContacts : Screen("emergency_contacts")
    data object AddContact : Screen("add_contact")
    data object EditContact : Screen("edit_contact/{contactId}") {
        const val ARG_CONTACT_ID = "contactId"
        fun createRoute(contactId: Long) = "edit_contact/$contactId"
    }
    data object MedicalProfile : Screen("medical_profile")
    data object RideHistory : Screen("ride_history")
    data object RideCompletion : Screen("ride_completion")
    data object LiveRideMap : Screen("live_ride_map")
    data object RideDetail : Screen("ride_detail/{sessionId}") {
        const val ARG_SESSION_ID = "sessionId"
        fun createRoute(sessionId: String) = "ride_detail/$sessionId"
    }
    data object IncidentHistory : Screen("incident_history")
    data object IncidentDetail : Screen("incident_detail/{incidentId}") {
        const val ARG_INCIDENT_ID = "incidentId"
        fun createRoute(incidentId: String) = "incident_detail/$incidentId"
    }
    data object Settings : Screen("settings")
    data object Profile : Screen("profile")
    data object Emergency : Screen("emergency")
    data object Bystander : Screen("bystander")
}
