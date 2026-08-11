package com.manish.vahakvigil.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import com.manish.vahakvigil.ui.components.AppScreen
import com.manish.vahakvigil.ui.components.AppTopBar
import com.manish.vahakvigil.ui.components.SectionTitle
import com.manish.vahakvigil.ui.components.SoftDivider
import com.manish.vahakvigil.ui.components.ThemeToggle
import com.manish.vahakvigil.ui.theme.VahakVigilGradientBackground
import com.manish.vahakvigil.ui.theme.VahakVigilTheme
import com.manish.vahakvigil.ui.theme.vahakColors

@Composable
fun SettingsScreen(
    onBackClick: () -> Unit,
    isDarkTheme: Boolean,
    onThemeChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    AppScreen(modifier = modifier) {
        AppTopBar(title = "Settings", onBackClick = onBackClick)

        Spacer(modifier = Modifier.height(26.dp))

        SectionTitle(text = "Appearance")

        Spacer(modifier = Modifier.height(16.dp))

        SettingsGroup {
            SettingRow(
                title = "Theme",
                subtitle = if (isDarkTheme) "Dark luxury wine" else "Light warm paper",
                trailing = {
                    ThemeToggle(
                        isDarkTheme = isDarkTheme,
                        onThemeChange = onThemeChange
                    )
                }
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        SectionTitle(text = "Preferences")

        Spacer(modifier = Modifier.height(16.dp))

        SettingsGroup {
            SettingRow(title = "Units", subtitle = "Metric")
            SoftDivider(modifier = Modifier.fillMaxWidth().height(1.dp))
            SettingRow(title = "Language", subtitle = "English")
            SoftDivider(modifier = Modifier.fillMaxWidth().height(1.dp))
            SettingRow(title = "Emergency Countdown", subtitle = "10 seconds")
        }

        Spacer(modifier = Modifier.height(24.dp))

        SectionTitle(text = "About")

        Spacer(modifier = Modifier.height(16.dp))

        SettingsGroup {
            SettingRow(title = "About VahakVigil", subtitle = "Ride protection companion")
            SoftDivider(modifier = Modifier.fillMaxWidth().height(1.dp))
            SettingRow(title = "Version", subtitle = "1.0")
        }
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
    trailing: @Composable (() -> Unit)? = null
) {
    val colors = MaterialTheme.vahakColors

    Row(
        modifier = modifier
            .fillMaxWidth()
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
