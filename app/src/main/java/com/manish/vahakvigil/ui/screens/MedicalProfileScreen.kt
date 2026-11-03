package com.manish.vahakvigil.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.manish.vahakvigil.ui.components.AppCard
import com.manish.vahakvigil.ui.components.AppScreen
import com.manish.vahakvigil.ui.components.AppTextField
import com.manish.vahakvigil.ui.components.AppTopBar
import com.manish.vahakvigil.ui.components.PrimaryButton
import com.manish.vahakvigil.ui.components.SectionTitle
import com.manish.vahakvigil.ui.screens.medical.MedicalField
import com.manish.vahakvigil.ui.screens.medical.MedicalProfileViewModel
import com.manish.vahakvigil.ui.theme.VahakVigilGradientBackground
import com.manish.vahakvigil.ui.theme.VahakVigilTheme

/**
 * Medical Profile screen.
 *
 * Stateless composable: all state is owned by [MedicalProfileViewModel] and
 * collected here via [collectAsStateWithLifecycle]. The screen itself holds
 * no [androidx.compose.runtime.remember] state.
 *
 * [viewModel] defaults to a new instance scoped to the current Navigation
 * back-stack entry, so state survives configuration changes (e.g. rotation)
 * and is cleared when the destination is popped from the back stack.
 */
@Composable
fun MedicalProfileScreen(
    onBackClick: () -> Unit,
    viewModel: MedicalProfileViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    AppScreen(modifier = modifier) {
        AppTopBar(title = androidx.compose.ui.res.stringResource(com.manish.vahakvigil.R.string.medical_title), onBackClick = onBackClick)

        Spacer(modifier = Modifier.height(26.dp))

        SectionTitle(text = androidx.compose.ui.res.stringResource(com.manish.vahakvigil.R.string.medical_profile_section))

        Spacer(modifier = Modifier.height(16.dp))

        AppCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                AppTextField(
                    label = androidx.compose.ui.res.stringResource(com.manish.vahakvigil.R.string.medical_name),
                    value = uiState.name,
                    onValueChange = { viewModel.updateField(MedicalField.NAME, it) }
                )
                Spacer(modifier = Modifier.height(16.dp))
                AppTextField(
                    label = androidx.compose.ui.res.stringResource(com.manish.vahakvigil.R.string.medical_age),
                    value = uiState.age,
                    onValueChange = { viewModel.updateField(MedicalField.AGE, it) }
                )
                Spacer(modifier = Modifier.height(16.dp))
                AppTextField(
                    label = androidx.compose.ui.res.stringResource(com.manish.vahakvigil.R.string.medical_blood_group),
                    value = uiState.bloodGroup,
                    onValueChange = { viewModel.updateField(MedicalField.BLOOD_GROUP, it) }
                )
                Spacer(modifier = Modifier.height(16.dp))
                AppTextField(
                    label = androidx.compose.ui.res.stringResource(com.manish.vahakvigil.R.string.medical_conditions),
                    value = uiState.conditions,
                    onValueChange = { viewModel.updateField(MedicalField.CONDITIONS, it) },
                    minLines = 2
                )
            }
        }

        Spacer(modifier = Modifier.height(22.dp))

        SectionTitle(text = androidx.compose.ui.res.stringResource(com.manish.vahakvigil.R.string.medical_care_details))

        Spacer(modifier = Modifier.height(16.dp))

        AppCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                AppTextField(
                    label = androidx.compose.ui.res.stringResource(com.manish.vahakvigil.R.string.medical_allergies),
                    value = uiState.allergies,
                    onValueChange = { viewModel.updateField(MedicalField.ALLERGIES, it) },
                    minLines = 2
                )
                Spacer(modifier = Modifier.height(16.dp))
                AppTextField(
                    label = androidx.compose.ui.res.stringResource(com.manish.vahakvigil.R.string.medical_medications),
                    value = uiState.medications,
                    onValueChange = { viewModel.updateField(MedicalField.MEDICATIONS, it) },
                    minLines = 2
                )
                Spacer(modifier = Modifier.height(16.dp))
                AppTextField(
                    label = androidx.compose.ui.res.stringResource(com.manish.vahakvigil.R.string.medical_height),
                    value = uiState.height,
                    onValueChange = { viewModel.updateField(MedicalField.HEIGHT, it) }
                )
                Spacer(modifier = Modifier.height(16.dp))
                AppTextField(
                    label = androidx.compose.ui.res.stringResource(com.manish.vahakvigil.R.string.medical_weight),
                    value = uiState.weight,
                    onValueChange = { viewModel.updateField(MedicalField.WEIGHT, it) }
                )
                Spacer(modifier = Modifier.height(16.dp))
                AppTextField(
                    label = androidx.compose.ui.res.stringResource(com.manish.vahakvigil.R.string.medical_notes),
                    value = uiState.notes,
                    onValueChange = { viewModel.updateField(MedicalField.NOTES, it) },
                    minLines = 3
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        PrimaryButton(
            text = when {
                uiState.isSaving    -> androidx.compose.ui.res.stringResource(com.manish.vahakvigil.R.string.medical_saving)
                uiState.saveSuccess -> androidx.compose.ui.res.stringResource(com.manish.vahakvigil.R.string.medical_saved)
                else                -> androidx.compose.ui.res.stringResource(com.manish.vahakvigil.R.string.medical_save)
            },
            onClick = viewModel::save,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Preview(showBackground = true, widthDp = 412, heightDp = 917)
@Composable
private fun MedicalProfileScreenPreview() {
    val previewRepository = object : com.manish.vahakvigil.domain.repository.MedicalProfileRepository {
        override fun getProfile(): kotlinx.coroutines.flow.Flow<com.manish.vahakvigil.domain.model.MedicalProfile?> =
            kotlinx.coroutines.flow.flowOf(
                com.manish.vahakvigil.domain.model.MedicalProfile(
                    name = "Manish Sharma",
                    age = "29",
                    bloodGroup = "O+"
                )
            )
        override suspend fun saveProfile(profile: com.manish.vahakvigil.domain.model.MedicalProfile) {}
    }

    VahakVigilTheme {
        VahakVigilGradientBackground {
            MedicalProfileScreen(
                onBackClick = {},
                viewModel = MedicalProfileViewModel(previewRepository)
            )
        }
    }
}
