package com.manish.vahakvigil.ui.screens.contacts

import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.manish.vahakvigil.domain.model.Contact
import com.manish.vahakvigil.domain.repository.ContactRepository
import com.manish.vahakvigil.ui.components.AppCard
import com.manish.vahakvigil.ui.components.AppScreen
import com.manish.vahakvigil.ui.components.AppTextField
import com.manish.vahakvigil.ui.components.AppTopBar
import com.manish.vahakvigil.ui.components.PrimaryButton
import com.manish.vahakvigil.ui.components.SectionTitle
import com.manish.vahakvigil.ui.theme.VahakVigilGradientBackground
import com.manish.vahakvigil.ui.theme.VahakVigilTheme
import com.manish.vahakvigil.ui.theme.vahakColors
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Screen for adding a new emergency contact or editing an existing one.
 */
@Composable
fun AddEditContactScreen(
    onBackClick: () -> Unit,
    viewModel: AddEditContactViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val colors = MaterialTheme.vahakColors
    var showDeleteConfirmation by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.isSaveCompleted, uiState.isDeleted) {
        if (uiState.isSaveCompleted || uiState.isDeleted) {
            onBackClick()
        }
    }

    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = {
                Text(
                    text = androidx.compose.ui.res.stringResource(com.manish.vahakvigil.R.string.contacts_delete_title),
                    color = colors.headingText,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = androidx.compose.ui.res.stringResource(
                        com.manish.vahakvigil.R.string.contacts_delete_body,
                        uiState.name.ifBlank { androidx.compose.ui.res.stringResource(com.manish.vahakvigil.R.string.contacts_this_contact) }
                    ),
                    color = colors.mutedText
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirmation = false
                        viewModel.deleteContact()
                    }
                ) {
                    Text(
                        text = "Delete",
                        color = colors.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) {
                    Text(
                        text = "Cancel",
                        color = colors.mutedText
                    )
                }
            },
            containerColor = colors.card
        )
    }

    AppScreen(modifier = modifier) {
        AppTopBar(
            title = if (uiState.isEditing) androidx.compose.ui.res.stringResource(com.manish.vahakvigil.R.string.contacts_edit) else androidx.compose.ui.res.stringResource(com.manish.vahakvigil.R.string.contacts_add_title),
            onBackClick = onBackClick
        )

        Spacer(modifier = Modifier.height(26.dp))

        SectionTitle(
            text = if (uiState.isEditing) androidx.compose.ui.res.stringResource(com.manish.vahakvigil.R.string.contacts_edit_section) else androidx.compose.ui.res.stringResource(com.manish.vahakvigil.R.string.contacts_new_section)
        )

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
                    onValueChange = viewModel::onNameChanged
                )
                if (uiState.nameError != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = uiState.nameError ?: "",
                        color = colors.primary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                AppTextField(
                    label = androidx.compose.ui.res.stringResource(com.manish.vahakvigil.R.string.contacts_relationship),
                    value = uiState.relationship,
                    onValueChange = viewModel::onRelationshipChanged
                )
                if (uiState.relationshipError != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = uiState.relationshipError ?: "",
                        color = colors.primary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                AppTextField(
                    label = androidx.compose.ui.res.stringResource(com.manish.vahakvigil.R.string.contacts_phone),
                    value = uiState.phone,
                    onValueChange = viewModel::onPhoneChanged
                )
                if (uiState.phoneError != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = uiState.phoneError ?: "",
                        color = colors.primary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                if (uiState.generalError != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = uiState.generalError ?: "",
                        color = colors.primary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        PrimaryButton(
            text = when {
                uiState.isSaving -> androidx.compose.ui.res.stringResource(com.manish.vahakvigil.R.string.contacts_saving)
                uiState.isEditing -> androidx.compose.ui.res.stringResource(com.manish.vahakvigil.R.string.contacts_save_changes)
                else -> androidx.compose.ui.res.stringResource(com.manish.vahakvigil.R.string.contacts_add)
            },
            onClick = viewModel::saveContact,
            modifier = Modifier.fillMaxWidth()
        )

        if (uiState.isEditing) {
            Spacer(modifier = Modifier.height(14.dp))

            TextButton(
                onClick = { showDeleteConfirmation = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = androidx.compose.ui.res.stringResource(com.manish.vahakvigil.R.string.contacts_delete_title),
                    color = colors.primary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 412, heightDp = 917)
@Composable
private fun AddEditContactScreenPreview() {
    val fakeRepo = object : ContactRepository {
        override fun getContacts(): Flow<List<Contact>> = flowOf(emptyList())
        override suspend fun getContactById(id: Long): Contact? = Contact(1L, "Ravi Sharma", "Brother", "+91 98765 43210")
        override suspend fun getContactCount(): Int = 1
        override suspend fun saveContact(contact: Contact): Long = 1L
        override suspend fun updatePriorities(contacts: List<Contact>) {}
        override suspend fun deleteContact(contact: Contact) {}
    }

    VahakVigilTheme {
        VahakVigilGradientBackground {
            AddEditContactScreen(
                onBackClick = {},
                viewModel = AddEditContactViewModel(fakeRepo, contactId = 1L)
            )
        }
    }
}
