package com.manish.vahakvigil.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.manish.vahakvigil.ui.components.AppCard
import com.manish.vahakvigil.ui.components.AppScreen
import com.manish.vahakvigil.ui.components.AppTopBar
import com.manish.vahakvigil.ui.components.PrimaryButton
import com.manish.vahakvigil.ui.components.SectionTitle
import com.manish.vahakvigil.ui.components.SoftDivider
import com.manish.vahakvigil.ui.theme.VahakVigilGradientBackground
import com.manish.vahakvigil.ui.theme.VahakVigilTheme
import com.manish.vahakvigil.ui.theme.vahakColors

@Composable
fun ProfileScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    AppScreen(modifier = modifier) {
        AppTopBar(title = "Profile", onBackClick = onBackClick)

        Spacer(modifier = Modifier.height(26.dp))

        ProfileIdentity()

        Spacer(modifier = Modifier.height(26.dp))

        SectionTitle(text = "Overview")

        Spacer(modifier = Modifier.height(16.dp))

        ProfileInfoCard(
            rows = listOf(
                "Blood Group" to "O+",
                "Emergency Contact" to "Ravi Sharma",
                "Crash Alerts" to "Enabled",
                "Location Sharing" to "Active"
            )
        )

        Spacer(modifier = Modifier.height(24.dp))

        PrimaryButton(
            text = "Edit Profile",
            onClick = {},
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun ProfileIdentity(
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.vahakColors

    AppCard(
        modifier = modifier.fillMaxWidth(),
        cornerRadius = 30.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(92.dp)
                    .background(colors.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "M",
                    color = colors.primary,
                    fontSize = 34.sp,
                    lineHeight = 38.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = "Manish Sharma",
                color = colors.headingText,
                fontSize = 26.sp,
                lineHeight = 31.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "manish@example.com",
                color = colors.mutedText,
                fontSize = 14.sp,
                lineHeight = 18.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "+91 98765 43210",
                color = colors.mutedText,
                fontSize = 14.sp,
                lineHeight = 18.sp
            )
        }
    }
}

@Composable
private fun ProfileInfoCard(
    rows: List<Pair<String, String>>,
    modifier: Modifier = Modifier
) {
    AppCard(
        modifier = modifier.fillMaxWidth(),
        cornerRadius = 28.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            rows.forEachIndexed { index, row ->
                ProfileRow(label = row.first, value = row.second)
                if (index != rows.lastIndex) {
                    SoftDivider(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.vahakColors

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = colors.mutedText,
            fontSize = 14.sp,
            lineHeight = 18.sp
        )
        Spacer(modifier = Modifier.width(18.dp))
        Text(
            text = value,
            modifier = Modifier.weight(1f),
            color = colors.headingText,
            fontSize = 14.sp,
            lineHeight = 18.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.End
        )
    }
}

@Preview(showBackground = true, widthDp = 412, heightDp = 917)
@Composable
private fun ProfileScreenPreview() {
    VahakVigilTheme {
        VahakVigilGradientBackground {
            ProfileScreen(onBackClick = {})
        }
    }
}
