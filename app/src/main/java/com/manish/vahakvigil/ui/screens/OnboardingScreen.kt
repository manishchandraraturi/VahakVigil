package com.manish.vahakvigil.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.manish.vahakvigil.R
import com.manish.vahakvigil.ui.components.AppCard
import com.manish.vahakvigil.ui.components.AppIcon
import com.manish.vahakvigil.ui.components.AppScreen
import com.manish.vahakvigil.ui.components.CircleIcon
import com.manish.vahakvigil.ui.components.ProgressiveMotorcycleLineArt
import com.manish.vahakvigil.ui.components.PrimaryButton
import com.manish.vahakvigil.ui.theme.vahakColors

private data class OnboardingPage(val icon: AppIcon, val title: Int, val body: Int)

@Composable
fun OnboardingScreen(onComplete: () -> Unit) {
    val pages = listOf(
        OnboardingPage(AppIcon.Heart, R.string.onboarding_title_1, R.string.onboarding_body_1),
        OnboardingPage(AppIcon.Route, R.string.onboarding_title_2, R.string.onboarding_body_2),
        OnboardingPage(AppIcon.Gear, R.string.onboarding_title_3, R.string.onboarding_body_3),
        OnboardingPage(AppIcon.Contact, R.string.onboarding_title_4, R.string.onboarding_body_4),
        OnboardingPage(AppIcon.Heart, R.string.onboarding_title_5, R.string.onboarding_body_5)
    )
    var index by remember { mutableIntStateOf(0) }
    AppScreen {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onComplete) { Text(stringResource(R.string.onboarding_skip)) }
        }
        Spacer(Modifier.height(10.dp))
        ProgressiveMotorcycleLineArt(
            modifier = Modifier.fillMaxWidth().height(124.dp),
            progress = 1f
        )
        Spacer(Modifier.height(12.dp))
        AppCard(Modifier.fillMaxWidth(), cornerRadius = 28.dp) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 26.dp, vertical = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                CircleIcon(icon = pages[index].icon, modifier = Modifier.size(68.dp))
                Spacer(Modifier.height(24.dp))
                Text(stringResource(pages[index].title), color = MaterialTheme.vahakColors.headingText, fontSize = 24.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Spacer(Modifier.height(12.dp))
                Text(stringResource(pages[index].body), color = MaterialTheme.vahakColors.mutedText, fontSize = 15.sp, lineHeight = 23.sp, textAlign = TextAlign.Center)
            }
        }
        Spacer(Modifier.height(20.dp))
        Text(stringResource(R.string.onboarding_page, index + 1, pages.size), modifier = Modifier.fillMaxWidth(), color = MaterialTheme.vahakColors.mutedText, textAlign = TextAlign.Center)
        Spacer(Modifier.height(36.dp))
        PrimaryButton(
            text = stringResource(if (index == pages.lastIndex) R.string.onboarding_done else R.string.onboarding_continue),
            onClick = { if (index == pages.lastIndex) onComplete() else index++ },
            modifier = Modifier.fillMaxWidth()
        )
    }
}
