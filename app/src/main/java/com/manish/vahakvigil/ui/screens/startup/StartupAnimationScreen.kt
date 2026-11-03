package com.manish.vahakvigil.ui.screens.startup

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.manish.vahakvigil.R
import com.manish.vahakvigil.ui.components.ProgressiveMotorcycleLineArt
import com.manish.vahakvigil.ui.theme.vahakColors

const val STARTUP_ANIMATION_TOTAL_MS = 6_000

@Composable
fun StartupAnimationScreen(
    modifier: Modifier = Modifier,
    onAnimationFinished: () -> Unit
) {
    val progress = remember { Animatable(0f) }
    var didContinue by remember { mutableStateOf(false) }
    val finish = {
        if (!didContinue) {
            didContinue = true
            onAnimationFinished()
        }
    }

    LaunchedEffect(Unit) {
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(STARTUP_ANIMATION_TOTAL_MS, easing = LinearEasing)
        )
        finish()
    }

    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            ProgressiveMotorcycleLineArt(
                modifier = Modifier.fillMaxWidth().height(260.dp)
                    .semantics { contentDescription = "Motorcycle line drawing animation" },
                progress = progress.value
            )
            Spacer(Modifier.height(22.dp))
            Text(
                text = stringResource(R.string.app_name),
                color = MaterialTheme.vahakColors.headingText,
                fontSize = 25.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.startup_tagline),
                color = MaterialTheme.vahakColors.mutedText,
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )
        }
        TextButton(
            onClick = finish,
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 24.dp, end = 18.dp)
        ) {
            Text(stringResource(R.string.onboarding_skip))
        }
    }
}
