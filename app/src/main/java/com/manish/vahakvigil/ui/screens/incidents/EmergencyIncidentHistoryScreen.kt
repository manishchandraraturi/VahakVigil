package com.manish.vahakvigil.ui.screens.incidents

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.manish.vahakvigil.R
import com.manish.vahakvigil.domain.model.EmergencyIncidentRecord
import com.manish.vahakvigil.ui.components.AppCard
import com.manish.vahakvigil.ui.components.AppScreen
import com.manish.vahakvigil.ui.components.AppTopBar
import com.manish.vahakvigil.ui.theme.vahakColors
import java.text.DateFormat
import java.util.Date

@Composable
fun EmergencyIncidentHistoryScreen(viewModel: EmergencyIncidentViewModel, onBackClick: () -> Unit, onIncidentClick: (String) -> Unit, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var deleteTarget by remember { mutableStateOf<EmergencyIncidentRecord?>(null) }
    AppScreen(modifier) {
        AppTopBar(androidx.compose.ui.res.stringResource(R.string.incident_history_title), onBackClick)
        Spacer(Modifier.height(18.dp))
        when {
            state.isLoading -> Text(androidx.compose.ui.res.stringResource(R.string.incident_history_loading), color = MaterialTheme.vahakColors.mutedText)
            state.error && state.incidents.isEmpty() -> Text(androidx.compose.ui.res.stringResource(R.string.incident_history_error), color = MaterialTheme.vahakColors.mutedText)
            state.incidents.isEmpty() -> AppCard(Modifier.fillMaxWidth(), cornerRadius = 22.dp) {
                Text(androidx.compose.ui.res.stringResource(R.string.incident_history_empty), Modifier.padding(24.dp), color = MaterialTheme.vahakColors.mutedText)
            }
            else -> state.incidents.forEach { incident ->
                AppCard(Modifier.fillMaxWidth(), cornerRadius = 20.dp, onClick = { onIncidentClick(incident.incidentId) }) {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(incident.incidentTimestampEpochMillis)), color = MaterialTheme.vahakColors.headingText, fontWeight = FontWeight.SemiBold)
                                Text(androidx.compose.ui.res.stringResource(incidentStatusRes(incident.status)), color = MaterialTheme.vahakColors.mutedText, fontSize = 12.sp)
                            }
                            TextButton(onClick = { deleteTarget = incident }) { Text(androidx.compose.ui.res.stringResource(R.string.action_delete)) }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(androidx.compose.ui.res.stringResource(R.string.incident_history_confidence, incident.confidenceScore), color = MaterialTheme.vahakColors.mutedText, fontSize = 13.sp)
                        Text(incident.evidenceSummary, color = MaterialTheme.vahakColors.mutedText, fontSize = 12.sp)
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(androidx.compose.ui.res.stringResource(R.string.incident_delete_title)) },
            text = { Text(androidx.compose.ui.res.stringResource(R.string.incident_delete_message)) },
            confirmButton = { TextButton(onClick = { viewModel.delete(target.incidentId); deleteTarget = null }) { Text(androidx.compose.ui.res.stringResource(R.string.action_delete)) } },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text(androidx.compose.ui.res.stringResource(R.string.action_cancel)) } },
            containerColor = MaterialTheme.vahakColors.card
        )
    }
}

@Composable
fun EmergencyIncidentDetailScreen(viewModel: EmergencyIncidentViewModel, incidentId: String, onBackClick: () -> Unit, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(incidentId) { viewModel.load(incidentId) }
    AppScreen(modifier) {
        AppTopBar(androidx.compose.ui.res.stringResource(R.string.incident_detail_title), onBackClick)
        val incident = state.selected
        if (incident == null) {
            Text(androidx.compose.ui.res.stringResource(if (state.error) R.string.incident_history_error else R.string.incident_history_loading), color = MaterialTheme.vahakColors.mutedText)
        } else {
            Spacer(Modifier.height(12.dp))
            Text(DateFormat.getDateTimeInstance(DateFormat.FULL, DateFormat.SHORT).format(Date(incident.incidentTimestampEpochMillis)), color = MaterialTheme.vahakColors.headingText, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            IncidentDetailRow(androidx.compose.ui.res.stringResource(R.string.incident_status), androidx.compose.ui.res.stringResource(incidentStatusRes(incident.status)))
            IncidentDetailRow(androidx.compose.ui.res.stringResource(R.string.incident_user_action), androidx.compose.ui.res.stringResource(incidentActionRes(incident.userAction)))
            IncidentDetailRow(androidx.compose.ui.res.stringResource(R.string.incident_confidence), String.format(java.util.Locale.getDefault(), "%.2f", incident.confidenceScore))
            IncidentDetailRow(androidx.compose.ui.res.stringResource(R.string.incident_evidence_summary), incident.evidenceSummary)
            val location = if (incident.latitude != null && incident.longitude != null) "${incident.latitude}, ${incident.longitude}" else androidx.compose.ui.res.stringResource(R.string.incident_location_unavailable)
            IncidentDetailRow(androidx.compose.ui.res.stringResource(R.string.incident_location), location)
            incident.voiceNote?.let { note ->
                Spacer(Modifier.height(12.dp))
                Text(androidx.compose.ui.res.stringResource(R.string.incident_voice_note), color = MaterialTheme.vahakColors.headingText, fontWeight = FontWeight.SemiBold)
                Text(note, color = MaterialTheme.vahakColors.mutedText)
            }
            incident.photoReference?.let { path ->
                Spacer(Modifier.height(12.dp))
                Text(androidx.compose.ui.res.stringResource(R.string.incident_photo), color = MaterialTheme.vahakColors.headingText, fontWeight = FontWeight.SemiBold)
                val bitmap = remember(path) {
                    runCatching { BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = 4 }) }.getOrNull()
                }
                if (bitmap != null) Image(bitmap.asImageBitmap(), contentDescription = androidx.compose.ui.res.stringResource(R.string.incident_photo), modifier = Modifier.fillMaxWidth().height(240.dp), contentScale = ContentScale.Fit)
                else Text(androidx.compose.ui.res.stringResource(R.string.incident_photo_unavailable), color = MaterialTheme.vahakColors.mutedText)
            }
        }
    }
}

@Composable
private fun IncidentDetailRow(label: String, value: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Text(label, color = MaterialTheme.vahakColors.mutedText, fontSize = 12.sp)
        Text(value, color = MaterialTheme.vahakColors.headingText, fontWeight = FontWeight.Medium)
    }
}

private fun incidentStatusRes(status: String): Int = when (status) {
    "COUNTING_DOWN" -> R.string.incident_state_counting_down
    "CANCELLED" -> R.string.incident_state_cancelled
    "EXPIRED" -> R.string.incident_state_expired
    "DISPATCHING" -> R.string.incident_state_dispatching
    "DISPATCH_TRIGGERED" -> R.string.incident_state_dispatch_triggered
    "DISPATCH_FAILED" -> R.string.incident_state_dispatch_failed
    else -> R.string.incident_state_idle
}

private fun incidentActionRes(action: String): Int = when (action) {
    "HELP_CONFIRMED" -> R.string.incident_action_help_confirmed
    "COUNTDOWN_CANCELLED" -> R.string.incident_action_countdown_cancelled
    else -> R.string.incident_action_no_action
}
