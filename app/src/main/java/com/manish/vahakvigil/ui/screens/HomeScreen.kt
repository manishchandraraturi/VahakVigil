package com.manish.vahakvigil.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import com.manish.vahakvigil.ui.components.AppIcon
import com.manish.vahakvigil.ui.components.AppScreen
import com.manish.vahakvigil.ui.components.Metric
import com.manish.vahakvigil.ui.components.MotorcycleLineArt
import com.manish.vahakvigil.ui.components.PrimaryButton
import com.manish.vahakvigil.ui.components.QuickAccessCard
import com.manish.vahakvigil.ui.components.SectionTitle
import com.manish.vahakvigil.ui.components.SoftDivider
import com.manish.vahakvigil.ui.components.StatusCard
import com.manish.vahakvigil.ui.components.ThemeToggle
import com.manish.vahakvigil.ui.theme.VahakVigilGradientBackground
import com.manish.vahakvigil.ui.theme.VahakVigilTheme
import com.manish.vahakvigil.ui.theme.vahakColors

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    onStartMonitoringClick: () -> Unit = {},
    onEmergencyContactsClick: () -> Unit = {},
    onMedicalClick: () -> Unit = {},
    onHistoryClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onProfileClick: () -> Unit = {},
    isDarkTheme: Boolean,
    onThemeChange: (Boolean) -> Unit
) {
    AppScreen(modifier = modifier) {
        GreetingHeader(
            isDarkTheme = isDarkTheme,
            onThemeChange = onThemeChange,
            onProfileClick = onProfileClick
        )

        Spacer(modifier = Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(314.dp)
        ) {
            MotorcycleLineArt(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth(1.16f)
                    .height(238.dp)
            )

            StatusCard(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        PrimaryButton(
            text = "Start ride",
            subtitle = "Protection turns on instantly",
            onClick = onStartMonitoringClick,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(34.dp))

        SectionTitle(text = "Quick Access")

        Spacer(modifier = Modifier.height(18.dp))

        QuickAccessGrid(
            onMedicalClick = onMedicalClick,
            onContactsClick = onEmergencyContactsClick,
            onHistoryClick = onHistoryClick,
            onSettingsClick = onSettingsClick
        )

        Spacer(modifier = Modifier.height(36.dp))

        LastRideHeader(onHistoryClick = onHistoryClick)

        Spacer(modifier = Modifier.height(18.dp))

        LastRideSummary()
    }
}

@Composable
private fun GreetingHeader(
    isDarkTheme: Boolean,
    onThemeChange: (Boolean) -> Unit,
    onProfileClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.vahakColors

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .widthIn(min = 0.dp)
        ) {
            Text(
                text = "Wednesday, 29 July",
                color = colors.mutedText,
                fontSize = 14.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Good morning,\nManish",
                color = colors.headingText,
                fontSize = 32.sp,
                lineHeight = 37.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.sp
            )
        }

        Column(horizontalAlignment = Alignment.End) {
            ThemeToggle(
                isDarkTheme = isDarkTheme,
                onThemeChange = onThemeChange,
                modifier = Modifier.padding(top = 22.dp)
            )
            Text(
                text = "Profile",
                modifier = Modifier
                    .padding(top = 10.dp)
                    .clickable(onClick = onProfileClick),
                color = colors.primary,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun QuickAccessGrid(
    onMedicalClick: () -> Unit,
    onContactsClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    AppCard(
        modifier = modifier
            .fillMaxWidth()
            .height(282.dp),
        cornerRadius = 28.dp
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(modifier = Modifier.weight(1f)) {
                QuickAccessCard(
                    title = "Medical",
                    subtitle = "Editable profile",
                    icon = AppIcon.Heart,
                    onClick = onMedicalClick,
                    modifier = Modifier.weight(1f)
                )
                SoftDivider(
                    modifier = Modifier
                        .width(1.dp)
                        .fillMaxHeight()
                )
                QuickAccessCard(
                    title = "Contacts",
                    subtitle = "3 trusted",
                    icon = AppIcon.Contact,
                    onClick = onContactsClick,
                    modifier = Modifier.weight(1f)
                )
            }

            SoftDivider(
                modifier = Modifier
                    .height(1.dp)
                    .fillMaxWidth()
            )

            Row(modifier = Modifier.weight(1f)) {
                QuickAccessCard(
                    title = "History",
                    subtitle = "24 rides",
                    icon = AppIcon.Route,
                    onClick = onHistoryClick,
                    modifier = Modifier.weight(1f)
                )
                SoftDivider(
                    modifier = Modifier
                        .width(1.dp)
                        .fillMaxHeight()
                )
                QuickAccessCard(
                    title = "Settings",
                    subtitle = "Theme & safety",
                    icon = AppIcon.Gear,
                    onClick = onSettingsClick,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun LastRideHeader(
    onHistoryClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.vahakColors

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        SectionTitle(text = "Last Ride")

        Text(
            text = "History",
            modifier = Modifier
                .padding(end = 2.dp)
                .clickable(onClick = onHistoryClick),
            color = colors.primary,
            fontSize = 13.sp,
            lineHeight = 17.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun LastRideSummary(
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.vahakColors

    AppCard(
        modifier = modifier
            .fillMaxWidth()
            .height(140.dp),
        cornerRadius = 28.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 24.dp)
        ) {
            Text(
                text = "Yesterday, 6:12 PM",
                color = colors.mutedText,
                fontSize = 13.sp,
                lineHeight = 17.sp
            )
            Spacer(modifier = Modifier.weight(1f))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Metric(value = "18.4 km", label = "Distance")
                Metric(value = "42 min", label = "Duration")
                Text(
                    text = "Safe",
                    color = colors.success,
                    fontSize = 24.sp,
                    lineHeight = 28.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Start
                )
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 412, heightDp = 917)
@Composable
private fun HomeScreenPreview() {
    VahakVigilTheme(dynamicColor = false) {
        VahakVigilGradientBackground {
            HomeScreen(
                isDarkTheme = false,
                onThemeChange = {}
            )
        }
    }
}
