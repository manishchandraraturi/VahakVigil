package com.manish.vahakvigil.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.manish.vahakvigil.VahakVigilApp
import com.manish.vahakvigil.ui.screens.EmergencyContactsScreen
import com.manish.vahakvigil.ui.screens.HomeScreen
import com.manish.vahakvigil.ui.screens.MedicalProfileScreen
import com.manish.vahakvigil.ui.screens.ProfileScreen
import com.manish.vahakvigil.ui.screens.RideHistoryScreen
import com.manish.vahakvigil.ui.screens.RideDetailScreen
import com.manish.vahakvigil.ui.screens.SettingsScreen
import com.manish.vahakvigil.ui.screens.contacts.AddEditContactScreen
import com.manish.vahakvigil.ui.screens.contacts.AddEditContactViewModel
import com.manish.vahakvigil.ui.screens.contacts.EmergencyContactsViewModel
import com.manish.vahakvigil.ui.screens.medical.MedicalProfileViewModel

@Composable
fun VahakVigilNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    isDarkTheme: Boolean,
    onThemeChange: (Boolean) -> Unit,
    startDestination: String = Screen.Startup.route
) {
    val app = LocalContext.current.applicationContext as VahakVigilApp
    val activity = LocalContext.current as? androidx.activity.ComponentActivity
    val homeViewModel: com.manish.vahakvigil.ui.screens.home.HomeViewModel = if (activity != null) {
        viewModel(
            viewModelStoreOwner = activity,
            factory = com.manish.vahakvigil.ui.screens.home.HomeViewModel.provideFactory(
                repository = app.sensorRepository,
                medicalProfileRepository = app.medicalProfileRepository,
                contactRepository = app.contactRepository,
                locationProvider = app.locationProvider,
                smsSender = app.smsSender,
                timezoneManager = app.timezoneManager,
                pipeline = app.crashDetectionPipeline,
                ridePlanningCoordinator = app.ridePlanningCoordinator,
                rideSessionRepository = app.rideSessionRepository,
                emergencyIncidentRepository = app.emergencyIncidentRepository
            )
        )
    } else {
        viewModel(
            factory = com.manish.vahakvigil.ui.screens.home.HomeViewModel.provideFactory(
                repository = app.sensorRepository,
                medicalProfileRepository = app.medicalProfileRepository,
                contactRepository = app.contactRepository,
                locationProvider = app.locationProvider,
                smsSender = app.smsSender,
                timezoneManager = app.timezoneManager,
                pipeline = app.crashDetectionPipeline,
                ridePlanningCoordinator = app.ridePlanningCoordinator,
                rideSessionRepository = app.rideSessionRepository,
                emergencyIncidentRepository = app.emergencyIncidentRepository
            )
        )
    }

    val feedbackController = app.emergencyFeedbackController
    val homeUiState by homeViewModel.uiState.collectAsStateWithLifecycle()

    val isEmergencyActiveInitially = androidx.compose.runtime.remember {
        app.crashDetectionPipeline.state.value.isEmergencyModeActive
    }
    val onboardingStore = androidx.compose.runtime.remember { com.manish.vahakvigil.data.preferences.OnboardingStore(app) }
    val requestedStartDestination = if (startDestination == Screen.Startup.route && !onboardingStore.isComplete()) {
        Screen.Onboarding.route
    } else startDestination
    val effectiveStartDestination = resolveStartDestination(
        isEmergencyActive = isEmergencyActiveInitially,
        defaultStartDestination = requestedStartDestination,
        onboardingComplete = onboardingStore.isComplete()
    )

    // Global Emergency Navigation: navigate to Screen.Emergency from ANY screen when emergency mode becomes active (CB-03)
    androidx.compose.runtime.LaunchedEffect(homeUiState.isEmergencyModeActive) {
        if (homeUiState.isEmergencyModeActive) {
            val currentRoute = navController.currentDestination?.route
            if (currentRoute != Screen.Emergency.route && currentRoute != Screen.Bystander.route) {
                navController.navigate(Screen.Emergency.route) {
                    launchSingleTop = true
                }
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = effectiveStartDestination,
        modifier = modifier
    ) {
        composable(Screen.Onboarding.route) {
            com.manish.vahakvigil.ui.screens.OnboardingScreen(onComplete = {
                onboardingStore.markComplete()
                navController.navigate(Screen.Startup.route) {
                    popUpTo(Screen.Onboarding.route) { inclusive = true }
                }
            })
        }

        composable(
            route = Screen.Startup.route,
            exitTransition = { androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(400)) }
        ) {
            com.manish.vahakvigil.ui.screens.startup.StartupAnimationScreen(
                onAnimationFinished = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Startup.route) { inclusive = true }
                    }
                }
            )
        }

        composable(
            route = Screen.Home.route,
            enterTransition = { androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(400)) }
        ) {
            HomeScreen(
                viewModel = homeViewModel,
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
                onReadinessClick = { navController.navigate(Screen.Readiness.route) },
                onOpenLiveMap = { navController.navigate(Screen.LiveRideMap.route) },
                onRideStopped = { navController.navigate(Screen.RideCompletion.route) },
                onProfileClick = {
                    navController.navigate(Screen.Profile.route)
                },
                onEmergencyModeTriggered = {
                    navController.navigate(Screen.Emergency.route) {
                        launchSingleTop = true
                    }
                },
                isDarkTheme = isDarkTheme,
                onThemeChange = onThemeChange
            )
        }

        composable(Screen.Emergency.route) {
            com.manish.vahakvigil.ui.screens.EmergencyScreen(
                viewModel = homeViewModel,
                feedbackController = feedbackController,
                incidentRepository = app.emergencyIncidentRepository,
                onDismiss = {
                    if (navController.previousBackStackEntry != null) {
                        navController.popBackStack(Screen.Home.route, inclusive = false)
                    } else {
                        navController.navigate(Screen.Home.route) {
                            popUpTo(Screen.Emergency.route) { inclusive = true }
                        }
                    }
                },
                onBystanderClick = {
                    navController.navigate(Screen.Bystander.route)
                }
            )
        }

        composable(Screen.Bystander.route) {
            val app = LocalContext.current.applicationContext as VahakVigilApp
            val bystanderViewModel: com.manish.vahakvigil.ui.screens.bystander.BystanderViewModel = viewModel(
                factory = com.manish.vahakvigil.ui.screens.bystander.BystanderViewModel.provideFactory(
                    contactRepository = app.contactRepository,
                    medicalProfileRepository = app.medicalProfileRepository,
                    emergencyActionTrigger = {
                        homeViewModel.acknowledgeNeedHelp()
                    },
                    isInitialAlertTriggered = homeViewModel.uiState.value.emergencyDispatchUiState == com.manish.vahakvigil.domain.model.EmergencyDispatchUiState.DISPATCH_TRIGGERED
                )
            )
            com.manish.vahakvigil.ui.screens.bystander.BystanderScreen(
                viewModel = bystanderViewModel,
                onBackClick = { navController.popBackStack() }
            )
        }

        composable(Screen.EmergencyContacts.route) {
            val app = LocalContext.current.applicationContext as VahakVigilApp
            val contactsViewModel: EmergencyContactsViewModel = viewModel(
                factory = EmergencyContactsViewModel.provideFactory(app.contactRepository)
            )
            EmergencyContactsScreen(
                onBackClick = { navController.popBackStack() },
                onAddContactClick = {
                    navController.navigate(Screen.AddContact.route)
                },
                onContactClick = { contact ->
                    navController.navigate(Screen.EditContact.createRoute(contact.id))
                },
                viewModel = contactsViewModel
            )
        }

        composable(Screen.AddContact.route) {
            val app = LocalContext.current.applicationContext as VahakVigilApp
            val addViewModel: AddEditContactViewModel = viewModel(
                factory = AddEditContactViewModel.provideFactory(
                    repository = app.contactRepository,
                    contactId = 0L
                )
            )
            AddEditContactScreen(
                onBackClick = { navController.popBackStack() },
                viewModel = addViewModel
            )
        }

        composable(
            route = Screen.EditContact.route,
            arguments = listOf(
                navArgument(Screen.EditContact.ARG_CONTACT_ID) {
                    type = NavType.LongType
                    defaultValue = 0L
                }
            )
        ) { backStackEntry ->
            val contactId = backStackEntry.arguments?.getLong(Screen.EditContact.ARG_CONTACT_ID) ?: 0L
            val app = LocalContext.current.applicationContext as VahakVigilApp
            val editViewModel: AddEditContactViewModel = viewModel(
                key = "edit_contact_$contactId",
                factory = AddEditContactViewModel.provideFactory(
                    repository = app.contactRepository,
                    contactId = contactId
                )
            )
            AddEditContactScreen(
                onBackClick = { navController.popBackStack() },
                viewModel = editViewModel
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                onBackClick = { navController.popBackStack() },
                isDarkTheme = isDarkTheme,
                onThemeChange = onThemeChange,
                timezoneManager = app.timezoneManager,
                onLanguageChanged = { activity?.recreate() },
                onReadinessClick = { navController.navigate(Screen.Readiness.route) },
                onContactsClick = { navController.navigate(Screen.EmergencyContacts.route) },
                onMedicalClick = { navController.navigate(Screen.MedicalProfile.route) }
            )
        }

        composable(Screen.Readiness.route) {
            com.manish.vahakvigil.ui.screens.ReadinessScreen(
                app = app,
                onBack = { navController.popBackStack() },
                onContacts = { navController.navigate(Screen.EmergencyContacts.route) },
                onMedical = { navController.navigate(Screen.MedicalProfile.route) },
                onSettings = { navController.navigate(Screen.Settings.route) }
            )
        }

        composable(Screen.Profile.route) {
            ProfileScreen(
                onBackClick = { navController.popBackStack() }
            )
        }

        composable(Screen.MedicalProfile.route) {
            val app = LocalContext.current.applicationContext as VahakVigilApp
            val medicalViewModel: MedicalProfileViewModel = viewModel(
                factory = MedicalProfileViewModel.provideFactory(app.medicalProfileRepository)
            )
            MedicalProfileScreen(
                onBackClick = { navController.popBackStack() },
                viewModel = medicalViewModel
            )
        }

        composable(Screen.RideHistory.route) {
            val rideHistoryViewModel: com.manish.vahakvigil.ui.screens.ridehistory.RideHistoryViewModel = viewModel(
                factory = com.manish.vahakvigil.ui.screens.ridehistory.RideHistoryViewModel.provideFactory(app.rideSessionRepository)
            )
            RideHistoryScreen(
                viewModel = rideHistoryViewModel,
                onBackClick = { navController.popBackStack() },
                onRideClick = { navController.navigate(Screen.RideDetail.createRoute(it)) },
                onIncidentsClick = { navController.navigate(Screen.IncidentHistory.route) }
            )
        }

        composable(Screen.RideCompletion.route) {
            val rideHistoryViewModel: com.manish.vahakvigil.ui.screens.ridehistory.RideHistoryViewModel = viewModel(
                factory = com.manish.vahakvigil.ui.screens.ridehistory.RideHistoryViewModel.provideFactory(app.rideSessionRepository)
            )
            com.manish.vahakvigil.ui.screens.RideCompletionScreen(
                viewModel = rideHistoryViewModel,
                onHome = { navController.navigate(Screen.Home.route) { popUpTo(Screen.Home.route) { inclusive = false } } },
                onDetails = { id -> navController.navigate(Screen.RideDetail.createRoute(id)) }
            )
        }

        composable(Screen.LiveRideMap.route) {
            com.manish.vahakvigil.ui.screens.LiveRideMapScreen(
                uiState = homeUiState,
                onBackClick = { navController.popBackStack() },
                onDestinationClick = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.RideDetail.route,
            arguments = listOf(navArgument(Screen.RideDetail.ARG_SESSION_ID) { type = NavType.StringType })
        ) { backStackEntry ->
            val rideHistoryViewModel: com.manish.vahakvigil.ui.screens.ridehistory.RideHistoryViewModel = viewModel(
                factory = com.manish.vahakvigil.ui.screens.ridehistory.RideHistoryViewModel.provideFactory(app.rideSessionRepository)
            )
            RideDetailScreen(
                viewModel = rideHistoryViewModel,
                rideId = backStackEntry.arguments?.getString(Screen.RideDetail.ARG_SESSION_ID).orEmpty(),
                onBackClick = { navController.popBackStack() }
            )
        }

        composable(Screen.IncidentHistory.route) {
            val incidentHistoryViewModel: com.manish.vahakvigil.ui.screens.incidents.EmergencyIncidentViewModel = viewModel(
                factory = com.manish.vahakvigil.ui.screens.incidents.EmergencyIncidentViewModel.provideFactory(
                    app.emergencyIncidentRepository,
                    app.incidentEvidenceStore
                )
            )
            com.manish.vahakvigil.ui.screens.incidents.EmergencyIncidentHistoryScreen(
                viewModel = incidentHistoryViewModel,
                onBackClick = { navController.popBackStack() },
                onIncidentClick = { navController.navigate(Screen.IncidentDetail.createRoute(it)) }
            )
        }

        composable(
            route = Screen.IncidentDetail.route,
            arguments = listOf(navArgument(Screen.IncidentDetail.ARG_INCIDENT_ID) { type = NavType.StringType })
        ) { backStackEntry ->
            val incidentHistoryViewModel: com.manish.vahakvigil.ui.screens.incidents.EmergencyIncidentViewModel = viewModel(
                factory = com.manish.vahakvigil.ui.screens.incidents.EmergencyIncidentViewModel.provideFactory(
                    app.emergencyIncidentRepository,
                    app.incidentEvidenceStore
                )
            )
            com.manish.vahakvigil.ui.screens.incidents.EmergencyIncidentDetailScreen(
                viewModel = incidentHistoryViewModel,
                incidentId = backStackEntry.arguments?.getString(Screen.IncidentDetail.ARG_INCIDENT_ID).orEmpty(),
                onBackClick = { navController.popBackStack() }
            )
        }
    }
}

/**
 * Resolves the start destination for VahakVigil (HP-03).
 * If emergency mode is already active when the app or activity launches/recreates,
 * routes directly to [Screen.Emergency.route], completely bypassing the startup animation.
 */
fun resolveStartDestination(
    isEmergencyActive: Boolean,
    defaultStartDestination: String = Screen.Startup.route,
    onboardingComplete: Boolean = false
): String {
    return if (isEmergencyActive) {
        Screen.Emergency.route
    } else if (defaultStartDestination != Screen.Startup.route) {
        defaultStartDestination
    } else if (onboardingComplete) {
        Screen.Home.route
    } else {
        Screen.Startup.route
    }
}
