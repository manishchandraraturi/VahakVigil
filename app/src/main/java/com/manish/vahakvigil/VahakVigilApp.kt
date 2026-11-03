package com.manish.vahakvigil

import android.app.Application
import com.manish.vahakvigil.data.local.db.AppDatabase
import com.manish.vahakvigil.data.repository.ContactRepositoryImpl
import com.manish.vahakvigil.data.repository.MedicalProfileRepositoryImpl
import com.manish.vahakvigil.data.repository.RideSessionRepositoryImpl
import com.manish.vahakvigil.data.repository.EmergencyIncidentRepositoryImpl
import com.manish.vahakvigil.data.repository.IncidentEvidenceStore
import com.manish.vahakvigil.domain.repository.ContactRepository
import com.manish.vahakvigil.domain.repository.MedicalProfileRepository

/**
 * Application class for VahakVigil.
 *
 * Provides lazy manual dependency injection for database and repository singletons.
 * Designed so that transitioning to Hilt later requires replacing these properties
 * with Hilt modules while keeping repositories and ViewModels unchanged.
 */
class VahakVigilApp : Application() {

    val database: AppDatabase by lazy {
        AppDatabase.getInstance(this)
    }

    val medicalProfileRepository: MedicalProfileRepository by lazy {
        MedicalProfileRepositoryImpl(database.medicalProfileDao())
    }

    val contactRepository: ContactRepository by lazy {
        ContactRepositoryImpl(database.contactDao())
    }

    val rideSessionRepository: com.manish.vahakvigil.domain.repository.RideSessionRepository by lazy {
        RideSessionRepositoryImpl(database, database.rideSessionDao())
    }

    val emergencyIncidentRepository: com.manish.vahakvigil.domain.repository.EmergencyIncidentRepository by lazy {
        EmergencyIncidentRepositoryImpl(database.emergencyIncidentDao())
    }

    val incidentEvidenceStore: IncidentEvidenceStore by lazy { IncidentEvidenceStore(this) }

    val sensorDataSource: com.manish.vahakvigil.data.sensor.SensorDataSource by lazy {
        com.manish.vahakvigil.data.sensor.AndroidSensorDataSource(this)
    }

    val sensorRepository: com.manish.vahakvigil.domain.repository.SensorRepository by lazy {
        com.manish.vahakvigil.data.repository.SensorRepositoryImpl(sensorDataSource)
    }

    val locationProvider: com.manish.vahakvigil.domain.location.LocationProvider by lazy {
        com.manish.vahakvigil.data.location.AndroidLocationProvider(this)
    }

    val destinationSearchRepository: com.manish.vahakvigil.domain.repository.DestinationSearchRepository by lazy {
        com.manish.vahakvigil.data.location.AndroidDestinationSearchRepository(this)
    }

    val ridePlanningCoordinator: com.manish.vahakvigil.domain.location.RidePlanningCoordinator by lazy {
        com.manish.vahakvigil.domain.location.RidePlanningCoordinator(
            destinationSearchRepository,
            com.manish.vahakvigil.domain.repository.UnconfiguredRideRoutingRepository()
        )
    }

    val smsSender: com.manish.vahakvigil.domain.sms.EmergencySmsSender by lazy {
        com.manish.vahakvigil.data.sms.AndroidEmergencySmsSender(this)
    }

    val timezoneManager: com.manish.vahakvigil.data.preferences.TimezoneManager by lazy {
        com.manish.vahakvigil.data.preferences.TimezoneManager(this)
    }

    val hospitalDiscoveryProvider: com.manish.vahakvigil.domain.hospital.HospitalDiscoveryProvider by lazy {
        com.manish.vahakvigil.data.hospital.GooglePlacesHospitalDiscoveryProvider()
    }

    val hospitalRanker: com.manish.vahakvigil.domain.hospital.HospitalRanker by lazy {
        com.manish.vahakvigil.domain.hospital.HospitalRanker()
    }

    val hospitalCommunicationProvider: com.manish.vahakvigil.domain.hospital.EmergencyHospitalCommunicationProvider by lazy {
        com.manish.vahakvigil.data.hospital.AndroidHospitalCommunicationProvider(this)
    }

    val ambulanceRequestProvider: com.manish.vahakvigil.domain.ambulance.AmbulanceRequestProvider by lazy {
        com.manish.vahakvigil.domain.ambulance.UnconfiguredAmbulanceRequestProvider()
    }

    val voiceCommunicationProvider: com.manish.vahakvigil.domain.telephony.EmergencyVoiceCommunicationProvider by lazy {
        com.manish.vahakvigil.domain.telephony.UnconfiguredVoiceCommunicationProvider()
    }

    val backendAuditProvider: com.manish.vahakvigil.domain.backend.EmergencyBackendAuditProvider by lazy {
        com.manish.vahakvigil.domain.backend.LocalOnlyBackendAuditProvider()
    }

    val emergencyFeedbackController: com.manish.vahakvigil.domain.feedback.EmergencyFeedbackController by lazy {
        com.manish.vahakvigil.data.feedback.AndroidEmergencyFeedbackController(this)
    }

    val crashDetectionPipeline: com.manish.vahakvigil.domain.pipeline.CrashDetectionPipeline by lazy {
        val compiler = com.manish.vahakvigil.domain.compiler.EmergencyDispatchDataCompiler(
            medicalProfileRepository = medicalProfileRepository,
            contactRepository = contactRepository,
            locationProvider = locationProvider,
            hospitalDiscoveryProvider = hospitalDiscoveryProvider,
            hospitalRanker = hospitalRanker
        )
        val orchestrator = com.manish.vahakvigil.domain.orchestration.EmergencyActionOrchestrator(
            actionExecutor = com.manish.vahakvigil.domain.orchestration.SmsEmergencyActionExecutor(smsSender)
        )
        com.manish.vahakvigil.domain.pipeline.CrashDetectionPipeline(
            sensorRepository = sensorRepository,
            dispatchDataCompiler = compiler,
            actionOrchestrator = orchestrator,
            locationProvider = locationProvider
        )
    }

    override fun onCreate() {
        super.onCreate()
        try {
            if (com.google.firebase.FirebaseApp.getApps(this).isEmpty()) {
                com.google.firebase.FirebaseApp.initializeApp(this)
            }
        } catch (_: Exception) {
            // Handled gracefully if google-services config is missing or invalid in testing
        }
    }
}
