package com.manish.vahakvigil.ui.screens.simulation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.manish.vahakvigil.R
import com.manish.vahakvigil.domain.simulation.EmergencySimulationStage
import com.manish.vahakvigil.ui.components.AppCard
import com.manish.vahakvigil.ui.components.AppScreen
import com.manish.vahakvigil.ui.components.AppTopBar
import com.manish.vahakvigil.ui.components.PrimaryButton
import com.manish.vahakvigil.ui.theme.vahakColors

@Composable
fun EmergencySimulationScreen(
    viewModel: EmergencySimulationViewModel,
    onExit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val colors = MaterialTheme.vahakColors

    BackHandler(onBack = onExit)
    AppScreen(modifier = modifier) {
        AppTopBar(title = stringResource(R.string.simulation_title), onBackClick = onExit)
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.simulation_safety_banner),
            modifier = Modifier.fillMaxWidth().background(colors.primaryContainer, RoundedCornerShape(14.dp)).padding(horizontal = 14.dp, vertical = 12.dp),
            color = colors.headingText,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(20.dp))

        when (state.stage) {
            EmergencySimulationStage.INTRO -> {
                Text(stringResource(R.string.simulation_intro_title), color = colors.headingText, fontSize = 25.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                AppCard(Modifier.fillMaxWidth(), cornerRadius = 22.dp) {
                    Text(stringResource(R.string.simulation_intro_body), modifier = Modifier.padding(18.dp), color = colors.mutedText, fontSize = 15.sp, lineHeight = 23.sp)
                }
                Spacer(Modifier.height(22.dp))
                PrimaryButton(text = stringResource(R.string.simulation_start), onClick = viewModel::startSimulation, modifier = Modifier.fillMaxWidth(), prominent = true)
            }

            EmergencySimulationStage.CRASH_SUSPECTED,
            EmergencySimulationStage.COUNTDOWN -> {
                Text(stringResource(R.string.simulation_suspected_title), color = colors.headingText, fontSize = 22.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.simulation_suspected_body), color = colors.mutedText, fontSize = 14.sp, lineHeight = 21.sp, textAlign = TextAlign.Center)
                Spacer(Modifier.height(24.dp))
                AppCard(Modifier.fillMaxWidth(), cornerRadius = 24.dp) {
                    Column(Modifier.fillMaxWidth().padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.simulation_countdown_label), color = colors.mutedText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = if (state.stage == EmergencySimulationStage.CRASH_SUSPECTED) "—" else state.remainingSeconds.toString(),
                            color = colors.primary,
                            fontSize = 68.sp,
                            lineHeight = 76.sp,
                            fontWeight = FontWeight.Black
                        )
                        Text(stringResource(R.string.emergency_countdown_seconds), color = colors.mutedText, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.height(22.dp))
                Button(
                    onClick = viewModel::cancelAsFine,
                    modifier = Modifier.fillMaxWidth().height(64.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.primaryContainer, contentColor = colors.headingText)
                ) { Text(stringResource(R.string.simulation_im_fine), fontSize = 16.sp, fontWeight = FontWeight.Bold) }
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = viewModel::requestHelp,
                    modifier = Modifier.fillMaxWidth().height(64.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError)
                ) { Text(stringResource(R.string.simulation_need_help), fontSize = 16.sp, fontWeight = FontWeight.Bold) }
            }

            EmergencySimulationStage.USER_CANCELLED -> {
                OutcomeCard(title = stringResource(R.string.simulation_cancelled_title), body = stringResource(R.string.simulation_cancelled_body))
                Spacer(Modifier.height(22.dp))
                PrimaryButton(text = stringResource(R.string.simulation_restart), onClick = viewModel::restart, modifier = Modifier.fillMaxWidth(), prominent = true)
                Spacer(Modifier.height(10.dp))
                OutlinedButton(onClick = onExit, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text(stringResource(R.string.simulation_exit)) }
            }

            EmergencySimulationStage.HELP_REQUESTED -> {
                OutcomeCard(title = stringResource(R.string.simulation_help_title), body = stringResource(R.string.simulation_help_body))
                Spacer(Modifier.height(22.dp))
                PrimaryButton(text = stringResource(R.string.simulation_view_guide), onClick = viewModel::openBystanderGuidance, modifier = Modifier.fillMaxWidth(), prominent = true)
                Spacer(Modifier.height(10.dp))
                OutlinedButton(onClick = viewModel::restart, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text(stringResource(R.string.simulation_restart)) }
            }

            EmergencySimulationStage.SIMULATED_DISPATCH_COMPLETE -> {
                OutcomeCard(title = stringResource(R.string.simulation_timeout_title), body = stringResource(R.string.simulation_timeout_body))
                Spacer(Modifier.height(22.dp))
                PrimaryButton(text = stringResource(R.string.simulation_view_guide), onClick = viewModel::openBystanderGuidance, modifier = Modifier.fillMaxWidth(), prominent = true)
                Spacer(Modifier.height(10.dp))
                OutlinedButton(onClick = viewModel::restart, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text(stringResource(R.string.simulation_restart)) }
            }

            EmergencySimulationStage.BYSTANDER_GUIDANCE -> {
                Text(stringResource(R.string.simulation_guide_title), color = colors.headingText, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.simulation_guide_body), color = colors.mutedText, fontSize = 14.sp, lineHeight = 21.sp)
                Spacer(Modifier.height(14.dp))
                GuidanceCard(
                    title = stringResource(R.string.bystander_do_now),
                    items = listOf(R.string.aid_call_112, R.string.aid_airway, R.string.aid_bleeding, R.string.aid_spine, R.string.aid_stay)
                )
                Spacer(Modifier.height(12.dp))
                GuidanceCard(
                    title = stringResource(R.string.bystander_do_not),
                    items = listOf(R.string.aid_do_not_move, R.string.aid_do_not_helmet, R.string.aid_do_not_food, R.string.aid_do_not_object)
                )
                Spacer(Modifier.height(20.dp))
                PrimaryButton(text = stringResource(R.string.simulation_complete), onClick = viewModel::complete, modifier = Modifier.fillMaxWidth(), prominent = true)
            }

            EmergencySimulationStage.COMPLETED -> {
                OutcomeCard(title = stringResource(R.string.simulation_completed_title), body = stringResource(R.string.simulation_completed_body))
                Spacer(Modifier.height(22.dp))
                PrimaryButton(text = stringResource(R.string.simulation_restart), onClick = viewModel::restart, modifier = Modifier.fillMaxWidth(), prominent = true)
                Spacer(Modifier.height(10.dp))
                OutlinedButton(onClick = onExit, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text(stringResource(R.string.simulation_exit)) }
            }
        }
    }
}

@Composable
private fun OutcomeCard(title: String, body: String) {
    val colors = MaterialTheme.vahakColors
    AppCard(Modifier.fillMaxWidth(), cornerRadius = 22.dp) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, color = colors.headingText, fontSize = 21.sp, fontWeight = FontWeight.Bold)
            Text(body, color = colors.mutedText, fontSize = 15.sp, lineHeight = 22.sp)
        }
    }
}

@Composable
private fun GuidanceCard(title: String, items: List<Int>) {
    val colors = MaterialTheme.vahakColors
    AppCard(Modifier.fillMaxWidth(), cornerRadius = 20.dp) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, color = colors.headingText, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            items.forEach { id ->
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("•", color = colors.primary, fontWeight = FontWeight.Bold)
                    Text(stringResource(id), color = colors.mutedText, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}
