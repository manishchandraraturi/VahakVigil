package com.manish.vahakvigil.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import android.Manifest
import android.speech.RecognizerIntent
import android.graphics.BitmapFactory
import com.manish.vahakvigil.data.preferences.LanguageManager
import com.manish.vahakvigil.data.repository.IncidentEvidenceStore
import com.manish.vahakvigil.R
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.platform.LocalContext
import com.manish.vahakvigil.domain.feedback.EmergencyFeedbackController
import com.manish.vahakvigil.domain.repository.EmergencyIncidentRepository
import kotlinx.coroutines.launch
import com.manish.vahakvigil.domain.model.EmergencyCapabilityStatus
import com.manish.vahakvigil.domain.model.EmergencyDispatchUiState
import com.manish.vahakvigil.domain.model.EmergencyLocation
import com.manish.vahakvigil.domain.model.EmergencyLocationSource
import com.manish.vahakvigil.domain.model.EmergencyHospital
import com.manish.vahakvigil.ui.components.SlideCommit
import com.manish.vahakvigil.ui.screens.home.HomeViewModel

/**
 * Dedicated high-priority emergency countdown and post-expiration dispatch screen.
 *
 * Guarantees:
 * - Observes [HomeViewModel.uiState.emergencyCountdownRemainingMillis] as the sole source of truth during countdown.
 * - Activates [EmergencyFeedbackController] strictly during countdown & dispatching, terminating on outcome or exit.
 * - During countdown, allows cancellation via "I'm Fine" and distress acknowledgment via "I Need Help".
 * - When countdown expires, the UI transitions to a distinct post-expiration dispatch status screen
 *   ([EmergencyDispatchUiState.DISPATCHING], [EmergencyDispatchUiState.DISPATCH_TRIGGERED], or [EmergencyDispatchUiState.DISPATCH_FAILED]).
 * - Never falsely claims "SMS delivered" or "Help is on the way". Accurately reports "Emergency alert initiated".
 * - Never exposes phone numbers, SMS text, GPS coordinates, or medical data in the UI.
 * - Post-expiration views never display an "I'm Fine" button to prevent implying that already-triggered dispatches can be recalled.
 */
@Composable
fun EmergencyScreen(
    viewModel: HomeViewModel,
    feedbackController: EmergencyFeedbackController,
    incidentRepository: EmergencyIncidentRepository? = null,
    onDismiss: () -> Unit,
    onBystanderClick: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val evidenceStore = androidx.compose.runtime.remember(context) { IncidentEvidenceStore(context) }
    val incidentId = IncidentEvidenceStore.incidentId(uiState.latestQualifiedIncident?.windowEndMs)
    val evidencePersistenceScope = androidx.compose.runtime.rememberCoroutineScope()
    fun persistEvidence(id: Long) {
        val saved = evidenceStore.read(id)
        evidencePersistenceScope.launch {
            runCatching { incidentRepository?.updateEvidence(id.toString(), saved.note, saved.photo?.absolutePath) }
        }
    }
    var evidence by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(IncidentEvidenceStore.Evidence(null, null)) }
    var noteDraft by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
    var pendingPhotoUri by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<Uri?>(null) }
    var pendingPhotoIncidentId by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<Long?>(null) }
    var evidenceMessage by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
    val languageTag = androidx.compose.runtime.remember(context) { LanguageManager(context).languageTag() }
    androidx.compose.runtime.LaunchedEffect(incidentId) {
        pendingPhotoIncidentId?.takeIf { it != incidentId }?.let(evidenceStore::cancelCapture)
        evidence = incidentId?.let(evidenceStore::read) ?: IncidentEvidenceStore.Evidence(null, null)
        noteDraft = null
        pendingPhotoUri = null
        pendingPhotoIncidentId = null
    }
    val speechResult = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val recognized = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (result.resultCode == android.app.Activity.RESULT_OK && !recognized.isNullOrBlank()) {
            noteDraft = recognized
        } else if (result.resultCode == android.app.Activity.RESULT_CANCELED) {
            evidenceMessage = "Voice capture cancelled. No note was saved."
        } else {
            evidenceMessage = "Speech recognition did not return a note. Emergency monitoring continues."
        }
    }
    val microphonePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) launchIncidentSpeech(speechResult, languageTag) { evidenceMessage = it }
        else evidenceMessage = "Microphone permission denied. Emergency monitoring continues."
    }
    val takePhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val id = pendingPhotoIncidentId
        pendingPhotoIncidentId = null
        pendingPhotoUri = null
        if (saved && id != null && evidenceStore.commitCapture(id)) {
            evidence = evidenceStore.read(id)
            persistEvidence(id)
            evidenceMessage = context.getString(R.string.evidence_photo_saved)
        } else {
            pendingPhotoUri = null
            if (id != null) evidenceStore.cancelCapture(id)
            evidenceMessage = "Photo was not saved. Emergency monitoring continues."
        }
    }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val id = incidentId
        if (granted && id != null) {
            try {
                val uri = evidenceStore.createCaptureUri(id)
                pendingPhotoUri = uri
                pendingPhotoIncidentId = id
                takePhoto.launch(uri)
            } catch (_: Exception) {
                evidenceMessage = "Camera storage is unavailable. Emergency monitoring continues."
            }
        } else evidenceMessage = "Camera permission denied. Emergency monitoring continues."
    }

    val onCallHospitalClick: (String) -> Unit = { phone ->
        try {
            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {}
    }

    val onOpenHospitalMapClick: (String) -> Unit = { mapUrl ->
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(mapUrl)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {}
    }

    // Multi-sensory feedback alert: Active only during countdown & initial dispatching
    LaunchedEffect(uiState.emergencyDispatchUiState) {
        when (uiState.emergencyDispatchUiState) {
            EmergencyDispatchUiState.COUNTING_DOWN,
            EmergencyDispatchUiState.EXPIRED,
            EmergencyDispatchUiState.DISPATCHING -> {
                feedbackController.startAlert()
            }
            EmergencyDispatchUiState.DISPATCH_TRIGGERED,
            EmergencyDispatchUiState.DISPATCH_FAILED,
            EmergencyDispatchUiState.DISPATCH_UNKNOWN,
            EmergencyDispatchUiState.CANCELLED,
            EmergencyDispatchUiState.IDLE -> {
                feedbackController.stopAlert()
            }
        }
    }

    DisposableEffect(feedbackController) {
        onDispose {
            if (!viewModel.uiState.value.isEmergencyModeActive) {
                feedbackController.stopAlert()
            }
        }
    }

    // Intercept hardware/system back button safely depending on stage:
    // During active countdown (COUNTING_DOWN), prevent accidental Back/predictive-back cancellation.
    // The explicit "I'M FINE" button remains the intentional cancellation path.
    BackHandler(enabled = uiState.isEmergencyModeActive) {
        if (EmergencyBackPolicy.shouldDisableBack(uiState.isEmergencyModeActive, uiState.emergencyDispatchUiState)) {
            // Intentionally no-op to disable Back cancellation during active countdown.
            return@BackHandler
        }
        viewModel.dismissEmergencyMode()
        onDismiss()
    }

    // Automatically dismiss if emergency mode is completely disengaged
    LaunchedEffect(uiState.isEmergencyModeActive) {
        if (!uiState.isEmergencyModeActive) {
            onDismiss()
        }
    }

    val remainingSeconds = (uiState.emergencyCountdownRemainingMillis / 1000).coerceAtLeast(0)
    val maxCountdownMillis = 75_000f
    val progress = (uiState.emergencyCountdownRemainingMillis / maxCountdownMillis).coerceIn(0f, 1f)

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    val emergencyBackground = Brush.verticalGradient(
        colors = listOf(
            Color(0xFF2A0808),
            Color(0xFF190404),
            Color(0xFF0D0202)
        )
    )

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .background(emergencyBackground),
        color = Color.Transparent
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            when (uiState.emergencyDispatchUiState) {
                EmergencyDispatchUiState.COUNTING_DOWN -> {
                    // Header Section
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(top = 16.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .scale(pulseScale)
                                .size(72.dp)
                                .background(Color(0xFFD32F2F), CircleShape)
                                .border(2.dp, Color(0xFFFF8A80), CircleShape)
                        ) {
                            Text(
                                text = "!",
                                fontSize = 44.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.White
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = stringResource(R.string.emergency_detected),
                            fontSize = 24.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFFFF5252),
                            letterSpacing = 1.5.sp,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = stringResource(R.string.emergency_prompt),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFFE0E0E0),
                            textAlign = TextAlign.Center,
                            lineHeight = 22.sp,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }

                    // Central Countdown Section
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(vertical = 24.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(200.dp)
                                .semantics {
                                    contentDescription = "Emergency countdown: $remainingSeconds seconds remaining"
                                }
                        ) {
                            CircularProgressIndicator(
                                progress = { progress },
                                modifier = Modifier.fillMaxSize(),
                                color = Color(0xFFFF5252),
                                trackColor = Color(0xFF3E1414),
                                strokeWidth = 10.dp
                            )

                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "$remainingSeconds",
                                    fontSize = 64.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color.White
                                )
                                Text(
                                    text = stringResource(R.string.emergency_countdown_seconds),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFFF8A80),
                                    letterSpacing = 2.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = stringResource(R.string.emergency_dispatch_countdown, remainingSeconds),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Normal,
                            color = Color(0xFFB0BEC5),
                            textAlign = TextAlign.Center
                        )

                        if (uiState.userAcknowledgedNeedHelp) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFF331414), RoundedCornerShape(8.dp))
                                    .border(1.dp, Color(0xFFFF5252), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = stringResource(R.string.emergency_help_requested),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFFFF8A80),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }

                    // Action Buttons Section
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Button(
                            onClick = {
                                viewModel.cancelEmergencyCountdown()
                                onDismiss()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(64.dp)
                                .semantics {
                                    contentDescription = "I am fine, cancel emergency alert"
                                },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF2E7D32),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.emergency_cancel),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                        }

                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            SlideCommit(
                                onConfirm = {
                                    viewModel.confirmEmergencyImmediately()
                                },
                                isConfirmed = uiState.userAcknowledgedNeedHelp ||
                                        uiState.emergencyCountdownState == com.manish.vahakvigil.domain.model.EmergencyCountdownState.EXPIRED
                            )
                        }

                        OutlinedButton(
                            onClick = onBystanderClick,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .semantics {
                                    contentDescription = "Open bystander assistance and first-aid guide"
                                },
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Color(0xFF81D4FA)
                            ),
                            shape = RoundedCornerShape(16.dp),
                            border = ButtonDefaults.outlinedButtonBorder(enabled = true)
                        ) {
                            Text(
                                text = stringResource(R.string.emergency_help_guide),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                        }

                        Text(
                            text = stringResource(R.string.emergency_contacts_timer),
                            fontSize = 12.sp,
                            color = Color(0xFF78909C),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(8.dp))
                        IncidentEvidenceCard(
                            evidence = evidence,
                            message = evidenceMessage,
                            onAddNote = {
                                when {
                                    incidentId == null -> evidenceMessage = "Incident details are not available yet; no note was saved."
                                    ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED -> launchIncidentSpeech(speechResult, languageTag) { evidenceMessage = it }
                                    else -> microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            },
                            onCapturePhoto = {
                                when {
                                    incidentId == null -> evidenceMessage = "Incident details are not available yet; no photo was saved."
                                    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED -> {
                                        try {
                                            val uri = evidenceStore.createCaptureUri(incidentId)
                                            pendingPhotoUri = uri
                                            pendingPhotoIncidentId = incidentId
                                            takePhoto.launch(uri)
                                        } catch (_: Exception) {
                                            evidenceMessage = "Camera storage is unavailable. Emergency monitoring continues."
                                        }
                                    }
                                    else -> cameraPermission.launch(Manifest.permission.CAMERA)
                                }
                            },
                            onDeleteNote = {
                                incidentId?.let { id -> evidenceStore.deleteNote(id); persistEvidence(id) }
                                evidence = incidentId?.let(evidenceStore::read) ?: IncidentEvidenceStore.Evidence(null, null)
                                evidenceMessage = null
                            },
                            onDeletePhoto = {
                                incidentId?.let { id -> evidenceStore.deletePhoto(id); persistEvidence(id) }
                                evidence = incidentId?.let(evidenceStore::read) ?: IncidentEvidenceStore.Evidence(null, null)
                                evidenceMessage = null
                            },
                            onRetakePhoto = {
                                val id = incidentId
                                if (id != null && ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                                    try {
                                        val uri = evidenceStore.createCaptureUri(id)
                                        pendingPhotoUri = uri
                                        pendingPhotoIncidentId = id
                                        takePhoto.launch(uri)
                                    } catch (_: Exception) {
                                        evidenceMessage = "Camera storage is unavailable. Emergency monitoring continues."
                                    }
                                } else cameraPermission.launch(Manifest.permission.CAMERA)
                            }
                        )
                    }
                }

                EmergencyDispatchUiState.EXPIRED,
                EmergencyDispatchUiState.DISPATCHING -> {
                    // Post-Expiration: Dispatching State
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(top = 24.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(72.dp)
                                .background(Color(0xFFD32F2F), CircleShape)
                        ) {
                            Text(
                                text = "!",
                                fontSize = 44.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.White
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = stringResource(R.string.emergency_response_active),
                            fontSize = 22.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFFFF5252),
                            letterSpacing = 1.2.sp,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = stringResource(R.string.emergency_processing),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFFE0E0E0),
                            textAlign = TextAlign.Center
                        )
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(vertical = 40.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(72.dp),
                            color = Color(0xFFFF5252),
                            strokeWidth = 6.dp
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        Text(
                            text = stringResource(R.string.emergency_sending),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = stringResource(R.string.emergency_preparing),
                            fontSize = 13.sp,
                            color = Color(0xFFB0BEC5),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        OutlinedButton(
                            onClick = onBystanderClick,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .semantics {
                                    contentDescription = "Open bystander assistance and first-aid guide"
                                },
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Color(0xFF81D4FA)
                            ),
                            shape = RoundedCornerShape(16.dp),
                            border = ButtonDefaults.outlinedButtonBorder(enabled = true)
                        ) {
                            Text(
                                text = stringResource(R.string.emergency_help_guide),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = stringResource(R.string.emergency_dispatch_underway),
                            fontSize = 12.sp,
                            color = Color(0xFF78909C),
                            textAlign = TextAlign.Center
                        )
                    }
                }

                EmergencyDispatchUiState.DISPATCH_TRIGGERED -> {
                    // Post-Expiration: Dispatch Triggered Successfully
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(top = 24.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(72.dp)
                                .background(Color(0xFF2E7D32), CircleShape)
                                .border(2.dp, Color(0xFF81C784), CircleShape)
                        ) {
                            Text(
                                text = "✓",
                                fontSize = 42.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.White
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = stringResource(R.string.emergency_alert_initiated),
                            fontSize = 22.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF81C784),
                            letterSpacing = 1.2.sp,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = stringResource(R.string.emergency_alert_initiated_body),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFFE0E0E0),
                            textAlign = TextAlign.Center,
                            lineHeight = 22.sp,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp)
                    ) {
                        EmergencyHospitalSection(
                            hospital = uiState.latestEmergencyDispatchPayload?.hospital,
                            onCallClick = onCallHospitalClick,
                            onOpenMapClick = onOpenHospitalMapClick
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF1B2E1E), RoundedCornerShape(12.dp))
                                .border(1.dp, Color(0xFF388E3C), RoundedCornerShape(12.dp))
                                .padding(16.dp)
                        ) {
                            Column {
                                Text(
                                    text = stringResource(R.string.emergency_status_triggered),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF81C784)
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = stringResource(R.string.emergency_delivery_note),
                                    fontSize = 13.sp,
                                    color = Color(0xFFB0BEC5),
                                    lineHeight = 18.sp
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                EmergencyLocationStatus(uiState.latestEmergencyDispatchPayload?.location)
                            }
                        }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        OutlinedButton(
                            onClick = onBystanderClick,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .semantics {
                                    contentDescription = "Open bystander assistance and first-aid guide"
                                },
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Color(0xFF81D4FA)
                            ),
                            shape = RoundedCornerShape(16.dp),
                            border = ButtonDefaults.outlinedButtonBorder(enabled = true)
                        ) {
                            Text(
                                text = stringResource(R.string.emergency_help_guide),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                        }

                        Button(
                            onClick = {
                                viewModel.dismissEmergencyMode()
                                onDismiss()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .semantics {
                                    contentDescription = "Return to home screen"
                                },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF37474F),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.action_return_home),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                        }

                        Text(
                            text = stringResource(R.string.emergency_call_direct),
                            fontSize = 12.sp,
                            color = Color(0xFF78909C),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                EmergencyDispatchUiState.DISPATCH_FAILED,
                EmergencyDispatchUiState.DISPATCH_UNKNOWN -> {
                    val isOutcomeUnknown = uiState.emergencyDispatchUiState == EmergencyDispatchUiState.DISPATCH_UNKNOWN
                    // Post-Expiration: Dispatch Failed
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(top = 24.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(72.dp)
                                .background(Color(0xFFC62828), CircleShape)
                                .border(2.dp, Color(0xFFFF8A80), CircleShape)
                        ) {
                            Text(
                                text = "✕",
                                fontSize = 40.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.White
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = stringResource(if (isOutcomeUnknown) R.string.emergency_dispatch_unknown_title else R.string.emergency_not_sent),
                            fontSize = 22.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFFFF5252),
                            letterSpacing = 1.2.sp,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = stringResource(if (isOutcomeUnknown) R.string.emergency_dispatch_unknown_body else R.string.emergency_failed),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFFE0E0E0),
                            textAlign = TextAlign.Center
                        )
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp)
                    ) {
                        EmergencyHospitalSection(
                            hospital = uiState.latestEmergencyDispatchPayload?.hospital,
                            onCallClick = onCallHospitalClick,
                            onOpenMapClick = onOpenHospitalMapClick
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF331414), RoundedCornerShape(12.dp))
                                .border(1.dp, Color(0xFFFF5252), RoundedCornerShape(12.dp))
                                .padding(16.dp)
                        ) {
                            Column {
                                Text(
                                text = stringResource(if (isOutcomeUnknown) R.string.emergency_dispatch_unknown_title else R.string.emergency_dispatch_failed_title),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFFF8A80)
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                text = stringResource(if (isOutcomeUnknown) R.string.emergency_dispatch_unknown_body else R.string.emergency_dispatch_failed_body),
                                    fontSize = 13.sp,
                                    color = Color(0xFFB0BEC5),
                                    lineHeight = 18.sp
                                )
                            }
                        }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        if (!isOutcomeUnknown) Button(
                            onClick = {
                                viewModel.retryEmergencyDispatch()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .semantics {
                                    contentDescription = "Retry emergency dispatch"
                                },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFD32F2F),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.emergency_retry),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                        }

                        OutlinedButton(
                            onClick = onBystanderClick,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .semantics {
                                    contentDescription = "Open bystander assistance and first-aid guide"
                                },
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Color(0xFF81D4FA)
                            ),
                            shape = RoundedCornerShape(16.dp),
                            border = ButtonDefaults.outlinedButtonBorder(enabled = true)
                        ) {
                            Text(
                                text = stringResource(R.string.emergency_help_guide),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                        }

                        OutlinedButton(
                            onClick = {
                                viewModel.dismissEmergencyMode()
                                onDismiss()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .semantics {
                                    contentDescription = "Dismiss failure and return to home"
                                },
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Color(0xFFCFD8DC)
                            ),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.action_return_home),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                                letterSpacing = 1.sp
                            )
                        }

                        Text(
                            text = stringResource(R.string.emergency_dispatch_failed_body),
                            fontSize = 12.sp,
                            color = Color(0xFF78909C),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                EmergencyDispatchUiState.CANCELLED,
                EmergencyDispatchUiState.IDLE -> {
                    // Safe fallback empty space while auto-dismissing
                    Spacer(modifier = Modifier.height(1.dp))
                }
            }
        }
    }

    noteDraft?.let { draft ->
        AlertDialog(
            onDismissRequest = { noteDraft = null },
            title = { Text(stringResource(R.string.emergency_review_note)) },
            text = { Text(draft) },
            confirmButton = {
                TextButton(onClick = {
                    incidentId?.let { id ->
                        try {
                            evidenceStore.saveNote(id, draft)
                            persistEvidence(id)
                            evidence = evidenceStore.read(id)
                            evidenceMessage = context.getString(R.string.evidence_note_saved)
                        } catch (_: Exception) {
                            evidenceMessage = "The note could not be saved. Emergency monitoring continues."
                        }
                    }
                    noteDraft = null
                }) { Text(stringResource(R.string.evidence_save)) }
            },
            dismissButton = {
                TextButton(onClick = { noteDraft = null }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }
}

@Composable
private fun EmergencyLocationStatus(location: EmergencyLocation?) {
    val text = when (location) {
        is EmergencyLocation.Available -> if (location.source == EmergencyLocationSource.LAST_KNOWN) {
            val capturedAt = location.timestampMs?.let {
                java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT).format(java.util.Date(it))
            }.orEmpty()
            stringResource(R.string.emergency_location_last_known, capturedAt)
        } else stringResource(R.string.emergency_location_current)
        else -> stringResource(R.string.emergency_location_unavailable)
    }
    Text(text, fontSize = 12.sp, color = Color(0xFFB0BEC5), lineHeight = 17.sp)
}

private fun launchIncidentSpeech(
    launcher: androidx.activity.result.ActivityResultLauncher<Intent>,
    languageTag: String,
    onUnavailable: (String) -> Unit
) {
    try {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Describe the incident. Review before saving.")
        }
        launcher.launch(intent)
    } catch (_: android.content.ActivityNotFoundException) {
        onUnavailable("Speech recognition is unavailable. Emergency monitoring continues.")
    } catch (_: Exception) {
        onUnavailable("Speech recognition failed. Emergency monitoring continues.")
    }
}

@Composable
private fun IncidentEvidenceCard(
    evidence: IncidentEvidenceStore.Evidence,
    message: String?,
    onAddNote: () -> Unit,
    onCapturePhoto: () -> Unit,
    onDeleteNote: () -> Unit,
    onDeletePhoto: () -> Unit,
    onRetakePhoto: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF211818), RoundedCornerShape(14.dp))
            .border(1.dp, Color(0xFF493333), RoundedCornerShape(14.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(stringResource(R.string.emergency_optional_details), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        Text(stringResource(R.string.emergency_voice_disclosure), color = Color(0xFFD8C8C8), fontSize = 12.sp, lineHeight = 17.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onAddNote, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.emergency_voice_note), fontSize = 12.sp)
            }
            OutlinedButton(onClick = onCapturePhoto, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.emergency_photo), fontSize = 12.sp)
            }
        }
        evidence.note?.let { note ->
            Text(stringResource(R.string.evidence_saved_note_prefix, note), color = Color.White, fontSize = 13.sp)
            TextButton(onClick = onDeleteNote) { Text(stringResource(R.string.evidence_delete)) }
        }
        evidence.photo?.let { photo ->
            val preview = androidx.compose.runtime.remember(photo.lastModified()) {
                decodeEvidencePreview(photo)
            }
            if (preview != null) {
                Image(
                    bitmap = preview.asImageBitmap(),
                    contentDescription = "Incident photo preview",
                    modifier = Modifier.fillMaxWidth().height(180.dp),
                    contentScale = ContentScale.Crop
                )
            } else Text(stringResource(R.string.evidence_preview_unavailable), color = Color.White, fontSize = 12.sp)
            Row {
                TextButton(onClick = onRetakePhoto) { Text(stringResource(R.string.evidence_retake)) }
                TextButton(onClick = onDeletePhoto) { Text(stringResource(R.string.evidence_delete)) }
            }
        }
        message?.let { Text(it, color = Color(0xFFB8D9FF), fontSize = 12.sp) }
        Text(stringResource(R.string.emergency_photo_disclosure), color = Color(0xFFD8C8C8), fontSize = 12.sp, lineHeight = 17.sp)
    }
}

private fun decodeEvidencePreview(file: java.io.File): android.graphics.Bitmap? = try {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, bounds)
    var sampleSize = 1
    while (bounds.outWidth / sampleSize > 1200 || bounds.outHeight / sampleSize > 1200) sampleSize *= 2
    BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sampleSize })
} catch (_: Exception) {
    null
}

/**
 * Compact emergency hospital display card.
 *
 * Shows hospital name, distance, address, verified capability status,
 * call action (if phone is available), and Maps navigation action.
 * If hospital is unavailable, shows a safe fallback message without crashing.
 */
@Composable
private fun EmergencyHospitalSection(
    hospital: EmergencyHospital?,
    onCallClick: (String) -> Unit,
    onOpenMapClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF1E2429), RoundedCornerShape(12.dp))
            .border(1.dp, Color(0xFF37474F), RoundedCornerShape(12.dp))
            .padding(16.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "EMERGENCY HOSPITAL",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                color = Color(0xFF81D4FA)
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (hospital != null) {
                Text(
                    text = hospital.name,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                hospital.distanceMeters?.let { dist ->
                    val distText = if (dist >= 1000) String.format(java.util.Locale.US, "%.1f km away", dist / 1000.0)
                    else "${dist.toInt()} m away"
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = distText,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFFFFB74D)
                    )
                }

                if (hospital.address.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = hospital.address,
                        fontSize = 12.sp,
                        color = Color(0xFFB0BEC5),
                        lineHeight = 16.sp
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                val capabilityText = if (hospital.emergencyCapabilityStatus == EmergencyCapabilityStatus.VERIFIED) {
                    "Emergency/trauma capability verified"
                } else {
                    "Emergency capability not verified"
                }
                Text(
                    text = capabilityText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (hospital.emergencyCapabilityStatus == EmergencyCapabilityStatus.VERIFIED) Color(0xFF81C784) else Color(0xFFFFCC80)
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val phone = hospital.phoneNumber
                    if (!phone.isNullOrBlank()) {
                        Button(
                            onClick = { onCallClick(phone) },
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                        ) {
                            Text("CALL HOSPITAL", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .background(Color(0xFF2A2A2A), RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Hospital phone unavailable",
                                fontSize = 10.sp,
                                color = Color(0xFF9E9E9E),
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = { onOpenMapClick(hospital.mapsUrl) },
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF81D4FA))
                    ) {
                        Text("OPEN MAP", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                Text(
                    text = "Emergency hospital could not be identified.",
                    fontSize = 13.sp,
                    color = Color(0xFFB0BEC5)
                )
            }
        }
    }
}

/**
 * Policy for handling Back navigation during emergency situations (MP-01).
 *
 * During active countdown (COUNTING_DOWN), accidental Back / predictive-back gestures
 * must be disabled / consumed without cancelling the emergency.
 * The explicit "I'M FINE" button remains the sole intentional cancellation path.
 * When emergency mode is resolved or expired, normal navigation behavior is preserved.
 */
object EmergencyBackPolicy {
    fun shouldDisableBack(isEmergencyActive: Boolean, dispatchUiState: EmergencyDispatchUiState): Boolean {
        return isEmergencyActive && dispatchUiState == EmergencyDispatchUiState.COUNTING_DOWN
    }
}
