package com.manish.vahakvigil.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Native Jetpack Compose implementation of the SlideCommit slide-to-confirm control.
 *
 * Interaction & Visual specifications:
 * - Requires deliberate horizontal drag across the track to reach confirmation threshold.
 * - Accidental taps or incomplete drags (<88% travel) safely bounce back via spring animation.
 * - Once threshold is reached, locks in and unfurls into a confirmed pill displaying [doneLabel].
 * - Proportions: 276dp x 56dp capsule with 30dp radius, 4dp track padding, 48dp handle.
 *
 * @param onConfirm Action invoked exactly once upon reaching confirmation threshold.
 * @param modifier Optional Compose [Modifier].
 * @param label Default track label while idle ("I NEED HELP ").
 * @param doneLabel Unfurled label upon confirmation ("Help On The Way").
 * @param trackColor Track background color (default `#813131`).
 * @param handleColor Drag handle color in idle/drag state (default `#000000`).
 * @param successColor Background color when confirmed (default `#489966`).
 * @param width Overall component width (default 276dp).
 * @param height Overall component height (default 56dp).
 * @param radius Corner radius of the pill capsule (default 30dp).
 * @param pad Inset padding between track edge and handle (default 4dp).
 * @param enabled Whether interaction is enabled.
 * @param isConfirmed External state flag reflecting whether distress is already confirmed.
 */
@Composable
fun SlideCommit(
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    label: String = "I NEED HELP ",
    doneLabel: String = "Help On The Way",
    trackColor: Color = Color(0xFF813131),
    handleColor: Color = Color(0xFF000000),
    successColor: Color = Color(0xFF489966),
    width: Dp = 276.dp,
    height: Dp = 56.dp,
    radius: Dp = 30.dp,
    pad: Dp = 4.dp,
    enabled: Boolean = true,
    isConfirmed: Boolean = false
) {
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()

    val gripSize = height - pad * 2
    val gripRadius = (radius - pad).coerceAtLeast(0.dp)
    val travelDistanceDp = (width - pad * 2 - gripSize).coerceAtLeast(0.dp)

    val travelPx = with(density) { travelDistanceDp.toPx() }
    val padPx = with(density) { pad.toPx() }

    val dragOffset = remember { Animatable(0f) }
    var localConfirmed by remember { mutableStateOf(false) }
    val effectiveConfirmed = isConfirmed || localConfirmed

    // Sync external confirmation state
    LaunchedEffect(isConfirmed) {
        if (isConfirmed && !localConfirmed) {
            localConfirmed = true
            dragOffset.snapTo(travelPx)
        }
    }

    // Dynamic dimensions and colors
    val animatedHandleWidth by animateDpAsState(
        targetValue = if (effectiveConfirmed) width - pad * 2 else gripSize,
        animationSpec = tween(durationMillis = 280),
        label = "handleWidth"
    )

    val currentHandleColor by animateColorAsState(
        targetValue = if (effectiveConfirmed) successColor else handleColor,
        animationSpec = tween(durationMillis = 250),
        label = "handleColor"
    )

    // Opacity fades
    val trackLabelAlpha = if (effectiveConfirmed) {
        0f
    } else {
        val travelDiv = (travelPx * 0.55f).coerceAtLeast(1f)
        (1f - (dragOffset.value / travelDiv)).coerceIn(0f, 1f)
    }

    val arrowAlpha = if (effectiveConfirmed) {
        0f
    } else {
        val startFade = travelPx * 0.55f
        val fadeRange = (travelPx * 0.40f).coerceAtLeast(1f)
        (1f - ((dragOffset.value - startFade) / fadeRange)).coerceIn(0f, 1f)
    }

    val doneContentAlpha by animateFloatAsState(
        targetValue = if (effectiveConfirmed) 1f else 0f,
        animationSpec = tween(durationMillis = 220, delayMillis = 80),
        label = "doneAlpha"
    )

    val dragModifier = if (enabled && !effectiveConfirmed) {
        Modifier.pointerInput(travelPx) {
            detectHorizontalDragGestures(
                onDragEnd = {
                    val threshold = travelPx * 0.88f
                    if (dragOffset.value >= threshold) {
                        coroutineScope.launch {
                            dragOffset.animateTo(
                                targetValue = travelPx,
                                animationSpec = tween(durationMillis = 140)
                            )
                            localConfirmed = true
                            onConfirm()
                        }
                    } else {
                        // Released before threshold: spring return with 0.38 bounce physics
                        coroutineScope.launch {
                            dragOffset.animateTo(
                                targetValue = 0f,
                                animationSpec = spring(
                                    dampingRatio = 0.62f,
                                    stiffness = 420f
                                )
                            )
                        }
                    }
                },
                onDragCancel = {
                    coroutineScope.launch {
                        dragOffset.animateTo(
                            targetValue = 0f,
                            animationSpec = spring(
                                dampingRatio = 0.62f,
                                stiffness = 420f
                            )
                        )
                    }
                },
                onHorizontalDrag = { _, dragAmount ->
                    coroutineScope.launch {
                        val next = (dragOffset.value + dragAmount).coerceIn(0f, travelPx)
                        dragOffset.snapTo(next)
                    }
                }
            )
        }
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .width(width)
            .height(height)
            .clip(RoundedCornerShape(radius))
            .background(trackColor)
            .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(radius))
            .semantics {
                role = Role.Button
                contentDescription = if (effectiveConfirmed) {
                    doneLabel
                } else {
                    "$label. Slide handle to confirm emergency distress"
                }
            }
            .then(dragModifier),
        contentAlignment = Alignment.CenterStart
    ) {
        // Track Centered Label
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp,
                color = Color.White.copy(alpha = trackLabelAlpha * 0.75f),
                textAlign = TextAlign.Center
            )
        }

        // Sliding / Unfurling Handle
        val handleOffsetX = if (effectiveConfirmed) {
            padPx
        } else {
            padPx + dragOffset.value
        }

        Box(
            modifier = Modifier
                .offset { IntOffset(x = handleOffsetX.roundToInt(), y = padPx.roundToInt()) }
                .width(animatedHandleWidth)
                .height(gripSize)
                .clip(RoundedCornerShape(gripRadius))
                .background(currentHandleColor)
                .border(
                    width = 1.dp,
                    color = if (effectiveConfirmed) Color(0x44FFFFFF) else Color(0x22FFFFFF),
                    shape = RoundedCornerShape(gripRadius)
                ),
            contentAlignment = Alignment.Center
        ) {
            if (!effectiveConfirmed) {
                // Right Arrow Icon
                Canvas(
                    modifier = Modifier.size(22.dp)
                ) {
                    val w = size.width
                    val h = size.height
                    val strokeW = 2.4.dp.toPx()
                    val arrowPath = Path().apply {
                        // Horizontal arrow stem
                        moveTo(w * 0.20f, h * 0.50f)
                        lineTo(w * 0.80f, h * 0.50f)
                        // Arrow head chevron
                        moveTo(w * 0.54f, h * 0.26f)
                        lineTo(w * 0.80f, h * 0.50f)
                        lineTo(w * 0.54f, h * 0.74f)
                    }
                    drawPath(
                        path = arrowPath,
                        color = Color.White.copy(alpha = arrowAlpha),
                        style = Stroke(
                            width = strokeW,
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )
                }
            } else {
                // Done Confirmation Pill Content (Checkmark + doneLabel)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) {
                    Canvas(
                        modifier = Modifier.size(18.dp)
                    ) {
                        val w = size.width
                        val h = size.height
                        val strokeW = 2.6.dp.toPx()
                        val checkPath = Path().apply {
                            moveTo(w * 0.12f, h * 0.52f)
                            lineTo(w * 0.40f, h * 0.80f)
                            lineTo(w * 0.88f, h * 0.22f)
                        }
                        drawPath(
                            path = checkPath,
                            color = Color.White.copy(alpha = doneContentAlpha),
                            style = Stroke(
                                width = strokeW,
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = doneLabel,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp,
                        color = Color.White.copy(alpha = doneContentAlpha)
                    )
                }
            }
        }
    }
}
