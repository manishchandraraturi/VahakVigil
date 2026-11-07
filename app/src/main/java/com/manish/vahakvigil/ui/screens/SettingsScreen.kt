package com.manish.vahakvigil.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.manish.vahakvigil.ui.components.AppCard
import com.manish.vahakvigil.ui.components.AppScreen
import com.manish.vahakvigil.ui.components.AppTopBar
import com.manish.vahakvigil.ui.components.SectionTitle
import com.manish.vahakvigil.ui.components.SoftDivider
import com.manish.vahakvigil.ui.components.ThemeToggle
import com.manish.vahakvigil.ui.theme.VahakVigilGradientBackground
import com.manish.vahakvigil.ui.theme.VahakVigilTheme
import com.manish.vahakvigil.ui.theme.vahakColors
import kotlinx.coroutines.launch
import androidx.compose.ui.res.stringResource
import com.manish.vahakvigil.R
import com.manish.vahakvigil.data.preferences.LanguageManager

@Composable
fun SettingsScreen(
    onBackClick: () -> Unit,
    isDarkTheme: Boolean,
    onThemeChange: (Boolean) -> Unit,
    timezoneManager: com.manish.vahakvigil.data.preferences.TimezoneManager? = null,
    onLanguageChanged: () -> Unit = {},
    onReadinessClick: () -> Unit = {},
    onContactsClick: () -> Unit = {},
    onMedicalClick: () -> Unit = {},
    onEmergencySimulationClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()
    var showTimezoneDialog by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    var showLanguageDialog by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val currentContext = androidx.compose.ui.platform.LocalContext.current
    val languageManager = remember(currentContext) { LanguageManager(currentContext) }
    var selectedLanguage by remember { mutableStateOf(languageManager.languageTag()) }

    val selectedZoneId by (timezoneManager?.selectedZoneId ?: kotlinx.coroutines.flow.flowOf(null))
        .collectAsStateWithLifecycle(initialValue = null)

    var tick by androidx.compose.runtime.remember { androidx.compose.runtime.mutableLongStateOf(0L) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(1000L)
            tick++
        }
    }

    val currentRegion = com.manish.vahakvigil.data.preferences.TimezoneManager.supportedRegions.find {
        it.id == selectedZoneId
    } ?: com.manish.vahakvigil.data.preferences.TimezoneManager.supportedRegions.first()
    val currentTime = androidx.compose.runtime.remember(selectedZoneId, tick) {
        com.manish.vahakvigil.data.preferences.TimezoneManager.formatCurrentTime(selectedZoneId)
    }
    val timeDisplay = "${currentRegion.label} · ${currentRegion.code} · $currentTime"

    AppScreen(modifier = modifier) {
        AppTopBar(title = stringResource(R.string.settings_title), onBackClick = onBackClick)

        Spacer(modifier = Modifier.height(26.dp))

        SectionTitle(text = stringResource(R.string.settings_appearance))

        Spacer(modifier = Modifier.height(16.dp))

        SettingsGroup {
            SettingRow(
                title = stringResource(R.string.settings_theme),
                subtitle = if (isDarkTheme) stringResource(R.string.theme_dark) else stringResource(R.string.theme_light),
                trailing = {
                    ThemeToggle(
                        isDarkTheme = isDarkTheme,
                        onThemeChange = onThemeChange
                    )
                }
            )
        }

        SettingsGroup(Modifier.padding(top = 8.dp)) {
            SettingRow(
                title = stringResource(R.string.settings_time_region),
                subtitle = timeDisplay,
                onClick = { showTimezoneDialog = true }
            )
            SoftDivider(modifier = Modifier.fillMaxWidth().height(1.dp))
            SettingRow(title = stringResource(R.string.settings_units), subtitle = stringResource(R.string.settings_metric))
        }

        Spacer(modifier = Modifier.height(20.dp))
        SectionTitle(text = stringResource(R.string.settings_language_section))

        Spacer(modifier = Modifier.height(12.dp))

        SettingsGroup {
            SettingRow(
                title = stringResource(R.string.settings_language),
                subtitle = LanguageManager.supportedLanguages.firstOrNull { it.tag == selectedLanguage }?.label ?: "English",
                onClick = { showLanguageDialog = true }
            )
        }

        Spacer(modifier = Modifier.height(20.dp))
        SectionTitle(text = stringResource(R.string.settings_safety_monitoring))
        Spacer(modifier = Modifier.height(12.dp))
        SettingsGroup {
            SettingRow(title = stringResource(R.string.settings_countdown), subtitle = stringResource(R.string.settings_countdown_value))
            SoftDivider(modifier = Modifier.fillMaxWidth().height(1.dp))
            SettingRow(title = stringResource(R.string.settings_monitoring_state), subtitle = stringResource(R.string.settings_monitoring_subtitle))
            SoftDivider(modifier = Modifier.fillMaxWidth().height(1.dp))
            SettingRow(title = stringResource(R.string.simulation_settings_title), subtitle = stringResource(R.string.simulation_settings_body), onClick = onEmergencySimulationClick)
        }

        Spacer(modifier = Modifier.height(20.dp))
        SectionTitle(text = stringResource(R.string.settings_permissions))
        Spacer(modifier = Modifier.height(12.dp))
        SettingsGroup {
            SettingRow(title = stringResource(R.string.settings_permissions_action), subtitle = stringResource(R.string.settings_permissions_subtitle), onClick = onReadinessClick)
        }

        Spacer(modifier = Modifier.height(20.dp))
        SectionTitle(text = stringResource(R.string.settings_contacts))
        Spacer(modifier = Modifier.height(12.dp))
        SettingsGroup {
            SettingRow(title = stringResource(R.string.settings_contacts_action), subtitle = stringResource(R.string.home_trusted_contacts), onClick = onContactsClick)
        }

        Spacer(modifier = Modifier.height(20.dp))
        SectionTitle(text = stringResource(R.string.settings_medical))
        Spacer(modifier = Modifier.height(12.dp))
        SettingsGroup {
            SettingRow(title = stringResource(R.string.settings_medical_action), subtitle = stringResource(R.string.home_editable_profile), onClick = onMedicalClick)
        }

        Spacer(modifier = Modifier.height(20.dp))

        SectionTitle(text = stringResource(R.string.settings_about))

        Spacer(modifier = Modifier.height(16.dp))

        SettingsGroup {
            SettingRow(title = stringResource(R.string.settings_about_app), subtitle = stringResource(R.string.settings_about_subtitle))
            SoftDivider(modifier = Modifier.fillMaxWidth().height(1.dp))
            val version = remember(currentContext) {
                runCatching {
                    @Suppress("DEPRECATION")
                    currentContext.packageManager.getPackageInfo(currentContext.packageName, 0).versionName
                }.getOrNull()
            }
            SettingRow(title = stringResource(R.string.settings_version), subtitle = version ?: stringResource(R.string.settings_version_unavailable))
        }
    }

    if (showLanguageDialog) {
        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            title = { Text(stringResource(R.string.settings_language)) },
            text = {
                Column {
                    LanguageManager.supportedLanguages.forEach { option ->
                        TextButton(
                            onClick = {
                                languageManager.setLanguageTag(option.tag)
                                selectedLanguage = option.tag
                                showLanguageDialog = false
                                onLanguageChanged()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = if (selectedLanguage == option.tag) "✓  ${option.label}" else option.label,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLanguageDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    if (showTimezoneDialog) {
        TimezoneSelectionDialog(
            selectedZoneId = selectedZoneId,
            onSelect = { zoneId ->
                coroutineScope.launch {
                    timezoneManager?.setZoneId(zoneId)
                    showTimezoneDialog = false
                }
            },
            onDismiss = { showTimezoneDialog = false }
        )
    }
}

@Composable
private fun SettingsGroup(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    AppCard(
        modifier = modifier.fillMaxWidth(),
        cornerRadius = 28.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 6.dp),
            content = content
        )
    }
}

@Composable
private fun SettingRow(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null
) {
    val colors = MaterialTheme.vahakColors

    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) {
                    Modifier.clickable(onClick = onClick)
                } else {
                    Modifier
                }
            )
            .padding(vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = colors.headingText,
                fontSize = 16.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(5.dp))
            Text(
                text = subtitle,
                color = colors.mutedText,
                fontSize = 13.sp,
                lineHeight = 17.sp
            )
        }

        if (trailing != null) {
            Spacer(modifier = Modifier.width(12.dp))
            trailing()
        }
    }
}

@Composable
private fun TimezoneSelectionDialog(
    selectedZoneId: String?,
    onSelect: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = MaterialTheme.vahakColors
    Dialog(onDismissRequest = onDismiss) {
        AppCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp),
            cornerRadius = 24.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Text(
                    text = stringResource(R.string.timezone_dialog_title),
                    color = colors.headingText,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.timezone_dialog_body),
                    color = colors.mutedText,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(16.dp))

                val scrollState = rememberScrollState()
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                        .verticalScroll(scrollState)
                ) {
                    com.manish.vahakvigil.data.preferences.TimezoneManager.supportedRegions.forEach { region ->
                        val isSelected = (selectedZoneId == null && region.id == null) ||
                                (selectedZoneId != null && selectedZoneId == region.id)
                        val sampleTime = com.manish.vahakvigil.data.preferences.TimezoneManager.formatCurrentTime(region.id)

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(region.id) }
                                .padding(vertical = 12.dp, horizontal = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = region.label,
                                    color = if (isSelected) colors.primary else colors.headingText,
                                    fontSize = 15.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "${region.code} · $sampleTime",
                                    color = colors.mutedText,
                                    fontSize = 12.sp
                                )
                            }
                            if (isSelected) {
                                Text(
                                    text = "✓",
                                    color = colors.primary,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        SoftDivider(modifier = Modifier.fillMaxWidth().height(1.dp))
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Text(
                        text = "Cancel",
                        color = colors.primary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clickable(onClick = onDismiss)
                            .padding(8.dp)
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 412, heightDp = 917)
@Composable
private fun SettingsScreenPreview() {
    VahakVigilTheme {
        VahakVigilGradientBackground {
            SettingsScreen(
                onBackClick = {},
                isDarkTheme = false,
                onThemeChange = {}
            )
        }
    }
}
