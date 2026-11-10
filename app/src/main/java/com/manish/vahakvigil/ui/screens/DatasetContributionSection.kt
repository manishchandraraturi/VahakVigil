package com.manish.vahakvigil.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.manish.vahakvigil.R
import com.manish.vahakvigil.domain.export.DatasetContributionConsent
import com.manish.vahakvigil.domain.export.DatasetPackageExportResult
import com.manish.vahakvigil.domain.export.SensorDatasetPackageExport
import com.manish.vahakvigil.domain.model.RideSession
import com.manish.vahakvigil.domain.model.RideSessionStatus
import com.manish.vahakvigil.ui.components.AppCard
import com.manish.vahakvigil.ui.components.SectionTitle
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import java.util.UUID

@Composable
internal fun DatasetContributionSection(
    rides: List<RideSession>,
    consent: DatasetContributionConsent,
    exporter: SensorDatasetPackageExport,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var enabled by remember { mutableStateOf(consent.mayExport()) }
    var showConsent by remember { mutableStateOf(false) }
    var showRidePicker by remember { mutableStateOf(false) }
    var selectedRide by remember { mutableStateOf<RideSession?>(null) }
    var provenance by remember { mutableStateOf("UNKNOWN") }
    var status by remember { mutableStateOf<String?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        val ride = selectedRide
        if (uri == null) {
            status = context.getString(R.string.dataset_export_cancelled)
        } else if (ride != null) {
            scope.launch {
                status = context.getString(R.string.dataset_export_running)
                runCatching { exporter.export(ride.id, uri.toString(), provenance) }
                    .onSuccess { result ->
                        status = when (result) {
                            DatasetPackageExportResult.Empty -> context.getString(R.string.dataset_export_empty)
                            is DatasetPackageExportResult.Complete -> context.getString(R.string.dataset_export_success, result.exportedRows)
                        }
                    }
                    .onFailure { status = context.getString(R.string.dataset_export_failed, it.message ?: it::class.java.simpleName) }
            }
        }
        selectedRide = null
    }
    val sessionPicker = rides.filter { it.status != RideSessionStatus.ACTIVE }.sortedByDescending { it.startedAtEpochMillis }

    Column(modifier) {
        SectionTitle(stringResource(R.string.dataset_contribution_title))
        Spacer(Modifier.height(12.dp))
        AppCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 20.dp) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.dataset_contribution_status))
                        Text(stringResource(if (enabled) R.string.dataset_contribution_enabled else R.string.dataset_contribution_disabled))
                    }
                    Switch(checked = enabled, onCheckedChange = { requested ->
                        if (requested) showConsent = true else {
                            consent.withdraw()
                            enabled = false
                            status = context.getString(R.string.dataset_consent_withdrawn)
                        }
                    })
                }
                Text(stringResource(R.string.dataset_contribution_included))
                Text(stringResource(R.string.dataset_contribution_excluded))
                Text(stringResource(R.string.dataset_contribution_optional))
                TextButton(
                    enabled = enabled && sessionPicker.isNotEmpty(),
                    onClick = { showRidePicker = true },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(stringResource(R.string.dataset_export_action)) }
                status?.let { Text(it) }
            }
        }
    }

    if (showConsent) AlertDialog(
        onDismissRequest = { showConsent = false },
        title = { Text(stringResource(R.string.dataset_consent_title)) },
        text = { Text(stringResource(R.string.dataset_consent_explanation)) },
        confirmButton = { TextButton(onClick = {
            consent.grant(System.currentTimeMillis())
            enabled = true
            status = context.getString(R.string.dataset_consent_enabled)
            showConsent = false
        }) { Text(stringResource(R.string.dataset_consent_accept)) } },
        dismissButton = { TextButton(onClick = { showConsent = false }) { Text(stringResource(R.string.action_cancel)) } }
    )

    if (showRidePicker) AlertDialog(
        onDismissRequest = { showRidePicker = false },
        title = { Text(stringResource(R.string.dataset_select_recording)) },
        text = {
            Column {
                if (sessionPicker.isEmpty()) Text(stringResource(R.string.dataset_no_recordings))
                sessionPicker.forEach { ride ->
                    TextButton(onClick = { selectedRide = ride; showRidePicker = false }) {
                        Text(DateFormat.getDateTimeInstance().format(Date(ride.startedAtEpochMillis)) + " · " + ride.status.name)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { showRidePicker = false }) { Text(stringResource(R.string.action_cancel)) } }
    )

    selectedRide?.let { ride ->
        AlertDialog(
            onDismissRequest = { selectedRide = null },
            title = { Text(stringResource(R.string.dataset_provenance_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.dataset_provenance_explanation))
                    listOf("REAL", "SIMULATED", "MIXED", "UNKNOWN").forEach { value ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { provenance = value }.padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = provenance == value, onClick = { provenance = value })
                            val originRes = when (value) {
                                "REAL" -> R.string.dataset_origin_real
                                "SIMULATED" -> R.string.dataset_origin_simulated
                                "MIXED" -> R.string.dataset_origin_mixed
                                else -> R.string.dataset_origin_unknown
                            }
                            Text(stringResource(originRes))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = {
                val filename = "vahakvigil-dataset-${UUID.randomUUID()}.zip"
                launcher.launch(filename)
            }) { Text(stringResource(R.string.dataset_continue_export)) } },
            dismissButton = { TextButton(onClick = { selectedRide = null }) { Text(stringResource(R.string.action_cancel)) } }
        )
    }
}
