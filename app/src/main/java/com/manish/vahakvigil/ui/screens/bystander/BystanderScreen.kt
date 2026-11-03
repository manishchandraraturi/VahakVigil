package com.manish.vahakvigil.ui.screens.bystander

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.manish.vahakvigil.R
import com.manish.vahakvigil.domain.model.Contact
import com.manish.vahakvigil.domain.model.MedicalProfile
import com.manish.vahakvigil.ui.components.AppCard
import com.manish.vahakvigil.ui.components.AppScreen
import com.manish.vahakvigil.ui.components.AppTopBar
import com.manish.vahakvigil.ui.components.SectionTitle
import com.manish.vahakvigil.ui.components.SoftDivider
import com.manish.vahakvigil.ui.theme.vahakColors
import androidx.compose.ui.res.stringResource

/**
 * Visual-First, High-Contrast Roadside Bystander Assistance Screen (V2).
 *
 * Designed for immediate action in high-stress roadside emergencies:
 * - LOOK → UNDERSTAND → ACT (2-3 second comprehension)
 * - Dominant, bold vector illustrations (no abstract icons or text-heavy essays)
 * - Huge touch-target YES / NO decision buttons
 * - Persistent, high-contrast Call 112 emergency dialer throughout the entire flow
 * - Structured, ultra-concise Action Summary (DO NOW / DO NOT)
 * - Compact medical info displaying populated fields only
 * - One-tap direct dialer for designated emergency contacts
 */
@Composable
fun BystanderScreen(
    viewModel: BystanderViewModel,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    AppScreen(modifier = modifier) {
        AppTopBar(
            title = stringResource(R.string.bystander_title),
            onBackClick = onBackClick
        )

        Spacer(modifier = Modifier.height(14.dp))

        when (uiState.currentStep) {
            BystanderTriageStep.EMERGENCY_PROMPT -> {
                EmergencyPromptView(
                    uiState = uiState,
                    onStartQuickCheck = viewModel::startQuickCheck,
                    onDial112 = { dialPhone(context, "112") },
                    onDialContact = { dialPhone(context, it) },
                    onTriggerAlert = viewModel::triggerEmergencyAlert,
                    onBackClick = onBackClick
                )
            }
            BystanderTriageStep.CONSCIOUSNESS -> {
                TriageQuestionStepView(
                    stepNumber = 1,
                    totalSteps = 4,
                    illustrationRes = R.drawable.ic_bystander_conscious,
                    question = stringResource(R.string.triage_awake_question),
                    cue = stringResource(R.string.triage_awake_cue),
                    yesLabel = stringResource(R.string.answer_yes_awake),
                    noLabel = stringResource(R.string.answer_no_unresponsive),
                    yesColor = Color(0xFF2E7D32),
                    noColor = Color(0xFFD32F2F),
                    onYes = { viewModel.answerConsciousness(true) },
                    onNo = { viewModel.answerConsciousness(false) },
                    onDial112 = { dialPhone(context, "112") },
                    onBack = { viewModel.goToStep(BystanderTriageStep.EMERGENCY_PROMPT) }
                )
            }
            BystanderTriageStep.BREATHING -> {
                TriageQuestionStepView(
                    stepNumber = 2,
                    totalSteps = 4,
                    illustrationRes = R.drawable.ic_bystander_breathing,
                    question = stringResource(R.string.triage_breathing_question),
                    cue = stringResource(R.string.triage_breathing_cue),
                    yesLabel = stringResource(R.string.answer_yes_breathing),
                    noLabel = stringResource(R.string.answer_no_breathing),
                    yesColor = Color(0xFF2E7D32),
                    noColor = Color(0xFFD32F2F),
                    onYes = { viewModel.answerBreathing(true) },
                    onNo = { viewModel.answerBreathing(false) },
                    onDial112 = { dialPhone(context, "112") },
                    onBack = { viewModel.goToStep(BystanderTriageStep.CONSCIOUSNESS) }
                )
            }
            BystanderTriageStep.HEAVY_BLEEDING -> {
                TriageQuestionStepView(
                    stepNumber = 3,
                    totalSteps = 4,
                    illustrationRes = R.drawable.ic_bystander_bleeding,
                    question = stringResource(R.string.triage_bleeding_question),
                    cue = stringResource(R.string.triage_bleeding_cue),
                    yesLabel = stringResource(R.string.answer_yes_bleeding),
                    noLabel = stringResource(R.string.answer_no_bleeding),
                    yesColor = Color(0xFFD32F2F),
                    noColor = Color(0xFF2E7D32),
                    onYes = { viewModel.answerHeavyBleeding(true) },
                    onNo = { viewModel.answerHeavyBleeding(false) },
                    onDial112 = { dialPhone(context, "112") },
                    onBack = { viewModel.goToStep(BystanderTriageStep.BREATHING) }
                )
            }
            BystanderTriageStep.SERIOUS_INJURY -> {
                TriageQuestionStepView(
                    stepNumber = 4,
                    totalSteps = 4,
                    illustrationRes = R.drawable.ic_bystander_injury,
                    question = stringResource(R.string.triage_injury_question),
                    cue = stringResource(R.string.triage_injury_cue),
                    yesLabel = stringResource(R.string.answer_yes_injury),
                    noLabel = stringResource(R.string.answer_no_injury),
                    yesColor = Color(0xFFD32F2F),
                    noColor = Color(0xFF2E7D32),
                    onYes = { viewModel.answerSeriousInjury(true) },
                    onNo = { viewModel.answerSeriousInjury(false) },
                    onDial112 = { dialPhone(context, "112") },
                    onBack = { viewModel.goToStep(BystanderTriageStep.HEAVY_BLEEDING) }
                )
            }
            BystanderTriageStep.ACTION_SUMMARY -> {
                ActionSummaryView(
                    uiState = uiState,
                    onRestartCheck = viewModel::resetAssessment,
                    onDial112 = { dialPhone(context, "112") },
                    onDialContact = { dialPhone(context, it) },
                    onTriggerAlert = viewModel::triggerEmergencyAlert,
                    onBackClick = onBackClick
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// SCREEN 1: Emergency Prompt
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun EmergencyPromptView(
    uiState: BystanderUiState,
    onStartQuickCheck: () -> Unit,
    onDial112: () -> Unit,
    onDialContact: (String) -> Unit,
    onTriggerAlert: () -> Unit,
    onBackClick: () -> Unit
) {
    val colors = MaterialTheme.vahakColors

    // Dominant Call 112 Action
    DominantCall112Button(onClick = onDial112)

    Spacer(modifier = Modifier.height(18.dp))

    Text(
        text = stringResource(R.string.bystander_assistance),
        color = colors.headingText,
        fontSize = 24.sp,
        lineHeight = 30.sp,
        fontWeight = FontWeight.Black
    )
    Spacer(modifier = Modifier.height(6.dp))
    Text(
        text = stringResource(R.string.bystander_intro),
        color = colors.mutedText,
        fontSize = 14.sp,
        lineHeight = 20.sp
    )

    Spacer(modifier = Modifier.height(18.dp))

    // Start Quick Check Button
    Button(
        onClick = onStartQuickCheck,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .semantics { contentDescription = "Start 30-second quick check" },
        colors = ButtonDefaults.buttonColors(
            containerColor = colors.primary,
            contentColor = Color.White
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Text(
            text = stringResource(R.string.bystander_start_check),
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp
        )
    }

    Spacer(modifier = Modifier.height(24.dp))

    // Distress SMS Alert Section
    SectionTitle(text = "Rider Distress Alert (SMS)")
    Spacer(modifier = Modifier.height(10.dp))
    EmergencySignalCard(
        isAlertTriggered = uiState.isEmergencyAlertTriggered,
        statusMessage = uiState.alertStatusMessage,
        onTriggerAlert = onTriggerAlert
    )

    // Rider's Emergency Contacts
    if (uiState.emergencyContacts.isNotEmpty()) {
        Spacer(modifier = Modifier.height(24.dp))
        SectionTitle(text = "Rider's Emergency Contacts")
        Spacer(modifier = Modifier.height(10.dp))
        EmergencyContactsCard(
            contacts = uiState.emergencyContacts,
            onDialContact = onDialContact
        )
    }

    // Rider's Medical Profile (populated fields only)
    if (uiState.medicalProfile != null && hasAnyMedicalInfo(uiState.medicalProfile)) {
        Spacer(modifier = Modifier.height(24.dp))
        SectionTitle(text = "Rider's Medical Profile")
        Spacer(modifier = Modifier.height(10.dp))
        MedicalProfileSummaryCard(profile = uiState.medicalProfile!!)
    }

    Spacer(modifier = Modifier.height(24.dp))

    // Return Button
    Button(
        onClick = onBackClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .semantics { contentDescription = "Return to previous screen" },
        colors = ButtonDefaults.buttonColors(
            containerColor = colors.cardHigh,
            contentColor = colors.headingText
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Text(
            text = "RETURN",
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
    }

    Spacer(modifier = Modifier.height(20.dp))
}

// ─────────────────────────────────────────────────────────────────────────────
// SCREENS 2-5: Single Decision Visual Triage Step
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun TriageQuestionStepView(
    stepNumber: Int,
    totalSteps: Int,
    illustrationRes: Int,
    question: String,
    cue: String,
    yesLabel: String,
    noLabel: String,
    yesColor: Color,
    noColor: Color,
    onYes: () -> Unit,
    onNo: () -> Unit,
    onDial112: () -> Unit,
    onBack: () -> Unit
) {
    val colors = MaterialTheme.vahakColors

    // Step Progress Indicator
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "STEP $stepNumber OF $totalSteps",
            color = colors.primary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.sp
        )
        Text(
            text = "Back",
            color = colors.mutedText,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .clickable(onClick = onBack)
                .padding(4.dp)
        )
    }

    Spacer(modifier = Modifier.height(12.dp))

    // Prominent Visual Illustration Card (occupies generous screen space)
    AppCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 24.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(id = illustrationRes),
                contentDescription = question,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(210.dp)
                    .padding(vertical = 6.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = question,
                color = colors.headingText,
                fontSize = 20.sp,
                lineHeight = 26.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = cue,
                color = colors.mutedText,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                textAlign = TextAlign.Center
            )
        }
    }

    Spacer(modifier = Modifier.height(20.dp))

    // Large YES / NO Touch Targets (min 64.dp height)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Button(
            onClick = onYes,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 64.dp)
                .semantics { contentDescription = yesLabel },
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = yesColor,
                contentColor = Color.White
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text(
                text = yesLabel,
                fontSize = 14.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center
            )
        }

        Button(
            onClick = onNo,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 64.dp)
                .semantics { contentDescription = noLabel },
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = noColor,
                contentColor = Color.White
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text(
                text = noLabel,
                fontSize = 14.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center
            )
        }
    }

    Spacer(modifier = Modifier.height(16.dp))

    // Persistent Call 112 Button
    OutlinedButton(
        onClick = onDial112,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .semantics { contentDescription = "Dial 112 emergency services" },
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = Color(0xFFD32F2F)
        ),
        border = BorderStroke(1.5.dp, Color(0xFFD32F2F))
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_bystander_call_112),
                contentDescription = null,
                modifier = Modifier.size(24.dp)
            )
            Text(
                        text = stringResource(R.string.bystander_call_112),
                fontSize = 14.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.5.sp
            )
        }
    }

    Spacer(modifier = Modifier.height(16.dp))
}

// ─────────────────────────────────────────────────────────────────────────────
// SCREEN 6: Concise Action Summary (DO NOW / DO NOT)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ActionSummaryView(
    uiState: BystanderUiState,
    onRestartCheck: () -> Unit,
    onDial112: () -> Unit,
    onDialContact: (String) -> Unit,
    onTriggerAlert: () -> Unit,
    onBackClick: () -> Unit
) {
    val colors = MaterialTheme.vahakColors
    val assessment = uiState.assessment
    val hasCriticalCondition = assessment.isBreathing == false ||
            assessment.isConscious == false ||
            assessment.hasHeavyBleeding == true ||
            assessment.hasSeriousInjury == true

    // Emergency Action Header
    if (hasCriticalCondition) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onDial112)
                .semantics { contentDescription = "Call 112 immediately" },
            color = Color(0xFFC62828),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_bystander_call_112),
                        contentDescription = null,
                        modifier = Modifier.size(32.dp)
                    )
                    Text(
                        text = stringResource(R.string.bystander_call_now),
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.5.sp
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.bystander_call_criteria),
                    color = Color.White.copy(alpha = 0.95f),
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onDial112,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color(0xFFC62828)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "DIAL 112",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }
        }
    } else {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color(0xFF1B3B2B),
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, Color(0xFF2E7D32))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(Color(0xFF2E7D32), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "✓", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.bystander_stable),
                        color = Color(0xFF81C784),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        text = stringResource(R.string.bystander_stable_advice),
                        color = Color(0xFFC8E6C9),
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                }
            }
        }
    }

    Spacer(modifier = Modifier.height(20.dp))

    // Section Header with Re-check Action
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.bystander_actions_required),
            color = colors.headingText,
            fontSize = 18.sp,
            fontWeight = FontWeight.Black
        )
        Text(
            text = stringResource(R.string.bystander_recheck),
            color = colors.primary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .clickable(onClick = onRestartCheck)
                .padding(4.dp)
        )
    }

    Spacer(modifier = Modifier.height(10.dp))

    // DO NOW CARD
    AppCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 20.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
            text = stringResource(R.string.bystander_do_now),
                color = Color(0xFF81C784),
                fontSize = 15.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.5.sp
            )
            Spacer(modifier = Modifier.height(8.dp))

            if (assessment.isBreathing == false) {
                ActionBullet(text = stringResource(R.string.aid_call_112))
            }
            if (assessment.isConscious == false && assessment.isBreathing != false) {
                ActionBullet(text = stringResource(R.string.aid_airway))
            }
            if (assessment.hasHeavyBleeding == true) {
                ActionBullet(text = stringResource(R.string.aid_bleeding))
            }
            if (assessment.hasSeriousInjury == true) {
                ActionBullet(text = stringResource(R.string.aid_spine))
            }
            ActionBullet(text = stringResource(R.string.aid_stay))
        }
    }

    Spacer(modifier = Modifier.height(14.dp))

    // DO NOT CARD
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFF2A1208),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, Color(0xFFEF5350))
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
            text = stringResource(R.string.bystander_do_not),
                color = Color(0xFFFF8A80),
                fontSize = 15.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.5.sp
            )
            Spacer(modifier = Modifier.height(8.dp))

            ProhibitionBullet(text = stringResource(R.string.aid_do_not_move))
            ProhibitionBullet(text = stringResource(R.string.aid_do_not_helmet))
            ProhibitionBullet(text = stringResource(R.string.aid_do_not_food))
            ProhibitionBullet(text = stringResource(R.string.aid_do_not_object))
        }
    }

    Spacer(modifier = Modifier.height(20.dp))

    // Distress SMS Alert Section
    SectionTitle(text = "Rider Distress Alert (SMS)")
    Spacer(modifier = Modifier.height(10.dp))
    EmergencySignalCard(
        isAlertTriggered = uiState.isEmergencyAlertTriggered,
        statusMessage = uiState.alertStatusMessage,
        onTriggerAlert = onTriggerAlert
    )

    // Rider's Emergency Contacts
    if (uiState.emergencyContacts.isNotEmpty()) {
        Spacer(modifier = Modifier.height(20.dp))
        SectionTitle(text = "Rider's Emergency Contacts")
        Spacer(modifier = Modifier.height(10.dp))
        EmergencyContactsCard(
            contacts = uiState.emergencyContacts,
            onDialContact = onDialContact
        )
    }

    // Rider's Medical Profile
    if (uiState.medicalProfile != null && hasAnyMedicalInfo(uiState.medicalProfile)) {
        Spacer(modifier = Modifier.height(20.dp))
        SectionTitle(text = "Rider's Medical Profile")
        Spacer(modifier = Modifier.height(10.dp))
        MedicalProfileSummaryCard(profile = uiState.medicalProfile!!)
    }

    Spacer(modifier = Modifier.height(24.dp))

    // Done Button
    Button(
        onClick = onBackClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .semantics { contentDescription = "Done, return to home" },
        colors = ButtonDefaults.buttonColors(
            containerColor = colors.cardHigh,
            contentColor = colors.headingText
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Text(
            text = stringResource(R.string.bystander_done),
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
    }

    Spacer(modifier = Modifier.height(20.dp))
}

// ─────────────────────────────────────────────────────────────────────────────
// REUSABLE COMPONENTS
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun DominantCall112Button(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .semantics { contentDescription = "Dial 112 National Emergency Helpline" },
        color = Color(0xFFC62828),
        shape = RoundedCornerShape(20.dp),
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_bystander_call_112),
                contentDescription = null,
                modifier = Modifier.size(38.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "CALL 112 (EMERGENCY)",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.5.sp
                )
                Text(
                    text = stringResource(R.string.bystander_emergency_helpline),
                    color = Color.White.copy(alpha = 0.90f),
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
private fun ActionBullet(text: String) {
    val colors = MaterialTheme.vahakColors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = "• ",
            color = Color(0xFF81C784),
            fontSize = 14.sp,
            fontWeight = FontWeight.Black
        )
        Text(
            text = text,
            color = colors.headingText,
            fontSize = 13.sp,
            lineHeight = 18.sp
        )
    }
}

@Composable
private fun ProhibitionBullet(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = "✕ ",
            color = Color(0xFFFF8A80),
            fontSize = 13.sp,
            fontWeight = FontWeight.Black
        )
        Text(
            text = text,
            color = Color(0xFFFFCDD2),
            fontSize = 13.sp,
            lineHeight = 18.sp
        )
    }
}

@Composable
private fun EmergencySignalCard(
    isAlertTriggered: Boolean,
    statusMessage: String?,
    onTriggerAlert: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.vahakColors

    AppCard(
        modifier = modifier.fillMaxWidth(),
        cornerRadius = 24.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            if (isAlertTriggered) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .background(Color(0xFF2E7D32), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "✓", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                    Text(
                        text = "SMS Alert Requested",
                        color = Color(0xFF81C784),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = statusMessage ?: "Emergency message handoff requested to Android telephony subsystem.",
                    color = colors.mutedText,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            } else {
                Text(
                    text = "Automated Distress Alert",
                    color = colors.headingText,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "If rider is unresponsive, request emergency SMS with GPS coordinates to trusted contacts.",
                    color = colors.mutedText,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
                Spacer(modifier = Modifier.height(14.dp))
                Button(
                    onClick = onTriggerAlert,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .semantics { contentDescription = "Send emergency SMS alert to contacts" },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFD32F2F),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(
                        text = "SEND SMS ALERT TO CONTACTS",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun EmergencyContactsCard(
    contacts: List<Contact>,
    onDialContact: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.vahakColors

    AppCard(
        modifier = modifier.fillMaxWidth(),
        cornerRadius = 24.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            Text(
                text = "Rider's Emergency Contacts (${contacts.size})",
                color = colors.headingText,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Tap to call directly via phone dialer:",
                color = colors.mutedText,
                fontSize = 12.sp
            )
            Spacer(modifier = Modifier.height(12.dp))

            contacts.forEachIndexed { index, contact ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = contact.name,
                            color = colors.headingText,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "${contact.relationship} • ${contact.phone}",
                            color = colors.mutedText,
                            fontSize = 12.sp
                        )
                    }

                    Button(
                        onClick = { onDialContact(contact.phone) },
                        modifier = Modifier
                            .height(38.dp)
                            .semantics { contentDescription = "Call ${contact.name}" },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.primary,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = "CALL",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                if (index != contacts.lastIndex) {
                    SoftDivider(modifier = Modifier.fillMaxWidth().height(1.dp).padding(vertical = 4.dp))
                }
            }
        }
    }
}

@Composable
private fun MedicalProfileSummaryCard(
    profile: MedicalProfile,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.vahakColors

    AppCard(
        modifier = modifier.fillMaxWidth(),
        cornerRadius = 24.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            Text(
                text = "Critical Medical Info",
                color = colors.headingText,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Display to arriving paramedics:",
                color = colors.mutedText,
                fontSize = 12.sp
            )
            Spacer(modifier = Modifier.height(12.dp))

            if (profile.bloodGroup.isNotBlank()) {
                MedicalDetailRow(label = "Blood Group", value = profile.bloodGroup)
            }
            if (profile.allergies.isNotBlank()) {
                MedicalDetailRow(label = "Allergies", value = profile.allergies)
            }
            if (profile.conditions.isNotBlank()) {
                MedicalDetailRow(label = "Conditions", value = profile.conditions)
            }
            if (profile.medications.isNotBlank()) {
                MedicalDetailRow(label = "Medications", value = profile.medications)
            }
            if (profile.notes.isNotBlank()) {
                MedicalDetailRow(label = "Emergency Notes", value = profile.notes)
            }
        }
    }
}

@Composable
private fun MedicalDetailRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.vahakColors

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            color = colors.mutedText,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = value,
            color = colors.headingText,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f, fill = false)
        )
    }
}

private fun hasAnyMedicalInfo(profile: MedicalProfile?): Boolean {
    if (profile == null) return false
    return profile.bloodGroup.isNotBlank() ||
            profile.allergies.isNotBlank() ||
            profile.conditions.isNotBlank() ||
            profile.medications.isNotBlank() ||
            profile.notes.isNotBlank()
}

private fun dialPhone(context: Context, phone: String) {
    try {
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${phone.trim()}"))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    } catch (_: Exception) {
    }
}
