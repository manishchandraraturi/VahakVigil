package com.manish.vahakvigil.ui.screens

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.manish.vahakvigil.ui.components.AppScreen
import com.manish.vahakvigil.ui.components.AppTopBar
import com.manish.vahakvigil.ui.components.RideCard
import com.manish.vahakvigil.ui.components.SectionTitle
import com.manish.vahakvigil.ui.theme.VahakVigilGradientBackground
import com.manish.vahakvigil.ui.theme.VahakVigilTheme

@Composable
fun RideHistoryScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val rides = listOf(
        RideSummary("Tuesday, 28 July", "18.4 km", "42 min", "Safe"),
        RideSummary("Monday, 27 July", "11.8 km", "31 min", "Safe"),
        RideSummary("Saturday, 25 July", "26.2 km", "58 min", "Safe"),
        RideSummary("Friday, 24 July", "8.6 km", "19 min", "Safe")
    )

    AppScreen(modifier = modifier) {
        AppTopBar(title = "History", onBackClick = onBackClick)

        Spacer(modifier = Modifier.height(26.dp))

        SectionTitle(text = "Ride History")

        Spacer(modifier = Modifier.height(16.dp))

        rides.forEachIndexed { index, ride ->
            RideCard(
                date = ride.date,
                distance = ride.distance,
                duration = ride.duration,
                status = ride.status,
                modifier = Modifier.fillMaxWidth()
            )
            if (index != rides.lastIndex) {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

private data class RideSummary(
    val date: String,
    val distance: String,
    val duration: String,
    val status: String
)

@Preview(showBackground = true, widthDp = 412, heightDp = 917)
@Composable
private fun RideHistoryScreenPreview() {
    VahakVigilTheme {
        VahakVigilGradientBackground {
            RideHistoryScreen(onBackClick = {})
        }
    }
}
