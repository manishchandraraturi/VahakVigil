package com.manish.vahakvigil.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.manish.vahakvigil.ui.components.AppCard
import com.manish.vahakvigil.ui.components.AppIcon
import com.manish.vahakvigil.ui.components.AppScreen
import com.manish.vahakvigil.ui.components.AppTopBar
import com.manish.vahakvigil.ui.components.CircleIconButton
import com.manish.vahakvigil.ui.components.PrimaryButton
import com.manish.vahakvigil.ui.components.SectionTitle
import com.manish.vahakvigil.ui.theme.VahakVigilGradientBackground
import com.manish.vahakvigil.ui.theme.VahakVigilTheme
import com.manish.vahakvigil.ui.theme.vahakColors

@Composable
fun EmergencyContactsScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val contacts = listOf(
        EmergencyContact("Ravi Sharma", "Brother", "+91 98765 43210"),
        EmergencyContact("Asha Sharma", "Mother", "+91 98765 43001"),
        EmergencyContact("Dr. Meera Iyer", "Family Doctor", "+91 98110 24880")
    )

    AppScreen(modifier = modifier) {
        AppTopBar(
            title = "Contacts",
            onBackClick = onBackClick,
            trailing = {
                CircleIconButton(
                    icon = AppIcon.Add,
                    onClick = {},
                    modifier = Modifier.size(44.dp)
                )
            }
        )

        Spacer(modifier = Modifier.height(26.dp))

        SectionTitle(text = "Emergency Contacts")

        Spacer(modifier = Modifier.height(16.dp))

        contacts.forEachIndexed { index, contact ->
            ContactCard(contact = contact)
            if (index != contacts.lastIndex) {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        PrimaryButton(
            text = "Add contact",
            onClick = {},
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun ContactCard(
    contact: EmergencyContact,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.vahakColors

    AppCard(
        modifier = modifier.fillMaxWidth(),
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
        }
    }
}

private data class EmergencyContact(
    val name: String,
    val relationship: String,
    val phone: String
) {
    val initials: String = name
        .split(" ")
        .mapNotNull { it.firstOrNull()?.uppercaseChar()?.toString() }
        .take(2)
        .joinToString("")
}

@Preview(showBackground = true, widthDp = 412, heightDp = 917)
@Composable
private fun EmergencyContactsScreenPreview() {
    VahakVigilTheme {
        VahakVigilGradientBackground {
            EmergencyContactsScreen(onBackClick = {})
        }
    }
}
