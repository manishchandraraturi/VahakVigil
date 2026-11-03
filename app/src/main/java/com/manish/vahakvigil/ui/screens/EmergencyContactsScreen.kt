package com.manish.vahakvigil.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.manish.vahakvigil.domain.model.Contact
import com.manish.vahakvigil.ui.components.AppCard
import com.manish.vahakvigil.ui.components.AppIcon
import com.manish.vahakvigil.ui.components.AppScreen
import com.manish.vahakvigil.ui.components.AppTopBar
import com.manish.vahakvigil.ui.components.CircleIconButton
import com.manish.vahakvigil.ui.components.PrimaryButton
import com.manish.vahakvigil.ui.components.SectionTitle
import com.manish.vahakvigil.ui.screens.contacts.EmergencyContactsViewModel
import com.manish.vahakvigil.ui.theme.VahakVigilGradientBackground
import com.manish.vahakvigil.ui.theme.VahakVigilTheme
import com.manish.vahakvigil.ui.theme.vahakColors

/**
 * Emergency Contacts screen.
 *
 * Displays all registered emergency contacts persisted in Room.
 * Supports adding, viewing, clicking to edit, and deleting contacts.
 */
@Composable
fun EmergencyContactsScreen(
    onBackClick: () -> Unit,
    onAddContactClick: () -> Unit,
    onContactClick: (Contact) -> Unit,
    viewModel: EmergencyContactsViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val colors = MaterialTheme.vahakColors
    var contactToDelete by remember { mutableStateOf<Contact?>(null) }

    if (contactToDelete != null) {
        val target = contactToDelete!!
        AlertDialog(
            onDismissRequest = { contactToDelete = null },
            title = {
                Text(
                    text = androidx.compose.ui.res.stringResource(com.manish.vahakvigil.R.string.contacts_delete_title),
                    color = colors.headingText,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = androidx.compose.ui.res.stringResource(com.manish.vahakvigil.R.string.contacts_delete_body, target.name),
                    color = colors.mutedText
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteContact(target)
                        contactToDelete = null
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
                TextButton(onClick = { contactToDelete = null }) {
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
            title = androidx.compose.ui.res.stringResource(com.manish.vahakvigil.R.string.contacts_title),
            onBackClick = onBackClick,
            trailing = {
                if (uiState.canAddContact) {
                    CircleIconButton(
                        icon = AppIcon.Add,
                        onClick = onAddContactClick,
                        modifier = Modifier.size(44.dp)
                    )
                }
            }
        )

        Spacer(modifier = Modifier.height(26.dp))

        SectionTitle(text = androidx.compose.ui.res.stringResource(com.manish.vahakvigil.R.string.contacts_emergency))

        Spacer(modifier = Modifier.height(16.dp))

        if (uiState.contacts.isEmpty()) {
            AppCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = androidx.compose.ui.res.stringResource(com.manish.vahakvigil.R.string.contacts_empty_title),
                        color = colors.headingText,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = androidx.compose.ui.res.stringResource(com.manish.vahakvigil.R.string.contacts_empty_body),
                        color = colors.mutedText,
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )
                }
            }
        } else {
            uiState.contacts.forEachIndexed { index, contact ->
                ContactCard(
                    contact = contact,
                    onClick = { onContactClick(contact) },
                    onDeleteClick = { contactToDelete = contact },
                    onMoveUpClick = if (index > 0) { { viewModel.moveContactUp(contact) } } else null,
                    onMoveDownClick = if (index < uiState.contacts.lastIndex) { { viewModel.moveContactDown(contact) } } else null
                )
                if (index != uiState.contacts.lastIndex) {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (uiState.isMaxContactsReached) {
            AppCard(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = androidx.compose.ui.res.stringResource(com.manish.vahakvigil.R.string.contacts_maximum),
                        color = colors.mutedText,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        } else {
            PrimaryButton(
                text = androidx.compose.ui.res.stringResource(com.manish.vahakvigil.R.string.contacts_add),
                onClick = onAddContactClick,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun ContactCard(
    contact: Contact,
    onClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onMoveUpClick: (() -> Unit)? = null,
    onMoveDownClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.vahakColors
    val priorityLabel = when (contact.priority) {
        1 -> "Primary (#1)"
        2 -> "Secondary (#2)"
        3 -> "Tertiary (#3)"
        else -> "Priority #${contact.priority}"
    }

    AppCard(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        cornerRadius = 28.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .background(colors.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = contact.initials,
                    color = colors.primary,
                    fontSize = 18.sp,
                    lineHeight = 22.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = priorityLabel,
                    color = colors.primary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = contact.name,
                    color = colors.headingText,
                    fontSize = 17.sp,
                    lineHeight = 21.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(5.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = contact.relationship,
                        color = colors.mutedText,
                        fontSize = 13.sp,
                        lineHeight = 17.sp
                    )
                    Text(
                        text = contact.phone,
                        color = colors.mutedText,
                        fontSize = 13.sp,
                        lineHeight = 17.sp
                    )
                }
            }

            if (onMoveUpClick != null || onMoveDownClick != null) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = 4.dp)
                ) {
                    if (onMoveUpClick != null) {
                        Text(
                            text = "▲",
                            color = colors.primary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clickable(onClick = onMoveUpClick)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    if (onMoveDownClick != null) {
                        Text(
                            text = "▼",
                            color = colors.primary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clickable(onClick = onMoveDownClick)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(4.dp))

            TextButton(
                onClick = onDeleteClick,
                modifier = Modifier.padding(start = 2.dp)
            ) {
                Text(
                    text = "Delete",
                    color = colors.primary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 412, heightDp = 917)
@Composable
private fun EmergencyContactsScreenPreview() {
    val previewRepository = object : com.manish.vahakvigil.domain.repository.ContactRepository {
        override fun getContacts(): kotlinx.coroutines.flow.Flow<List<Contact>> =
            kotlinx.coroutines.flow.flowOf(
                listOf(
                    Contact(1L, "Ravi Sharma", "Brother", "+91 98765 43210", 1)
                )
            )
        override suspend fun getContactById(id: Long): Contact? = null
        override suspend fun getContactCount(): Int = 1
        override suspend fun saveContact(contact: Contact): Long = 1L
        override suspend fun updatePriorities(contacts: List<Contact>) {}
        override suspend fun deleteContact(contact: Contact) {}
    }

    VahakVigilTheme {
        VahakVigilGradientBackground {
            EmergencyContactsScreen(
                onBackClick = {},
                onAddContactClick = {},
                onContactClick = {},
                viewModel = EmergencyContactsViewModel(previewRepository)
            )
        }
    }
}
