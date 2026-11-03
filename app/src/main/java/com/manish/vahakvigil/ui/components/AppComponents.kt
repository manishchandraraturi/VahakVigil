package com.manish.vahakvigil.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.manish.vahakvigil.ui.theme.vahakColors

@Composable
fun AppScreen(
    modifier: Modifier = Modifier,
    content: @Composable (horizontalPadding: Dp) -> Unit
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = Color.Transparent
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val horizontalPadding = if (maxWidth < 360.dp) 20.dp else 28.dp

            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxSize()
                    .widthIn(max = 420.dp)
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = horizontalPadding)
                    .padding(top = 20.dp, bottom = 32.dp)
            ) {
                content(horizontalPadding)
            }
        }
    }
}

@Composable
fun AppTopBar(
    title: String,
    onBackClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.vahakColors

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBackClick == null) {
            Spacer(modifier = Modifier.size(44.dp))
        } else {
            CircleIconButton(
                icon = AppIcon.Back,
                onClick = onBackClick,
                modifier = Modifier.size(44.dp)
            )
        }

        Text(
            text = title,
            modifier = Modifier.weight(1f),
            color = colors.headingText,
            fontSize = 20.sp,
            lineHeight = 24.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            letterSpacing = 0.sp
        )

        Box(
            modifier = Modifier.size(width = 64.dp, height = 44.dp),
            contentAlignment = Alignment.CenterEnd
        ) {
            trailing?.invoke()
        }
    }
}

@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 28.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val colors = MaterialTheme.vahakColors
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val lift by animateDpAsState(
        targetValue = if (isPressed) 2.dp else 5.dp,
        animationSpec = tween(durationMillis = 220),
        label = "card_lift"
    )
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.985f else 1f,
        animationSpec = tween(durationMillis = 220),
        label = "card_scale"
    )

    Box(
        modifier = modifier
            .scale(scale)
            .drawBehind {
                val radius = cornerRadius.toPx()
                val liftPx = lift.toPx()
                drawRoundRect(
                    color = colors.cardShadow,
                    topLeft = Offset(liftPx * 0.42f, liftPx * 0.62f),
                    size = size,
                    cornerRadius = CornerRadius(radius, radius)
                )
                drawRoundRect(
                    color = colors.cardHighlight,
                    topLeft = Offset(-liftPx * 0.28f, -liftPx * 0.30f),
                    size = size,
                    cornerRadius = CornerRadius(radius, radius)
                )
            }
            .background(colors.card, RoundedCornerShape(cornerRadius))
            .border(1.dp, colors.divider.copy(alpha = 0.58f), RoundedCornerShape(cornerRadius))
            .then(
                if (onClick == null) {
                    Modifier
                } else {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = onClick
                    )
                }
            )
    ) {
        content()
    }
}

@Composable
fun PrimaryButton(
    text: String,
    subtitle: String? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    prominent: Boolean = false
) {
    val colors = MaterialTheme.vahakColors
    val onPrimary = MaterialTheme.colorScheme.onPrimary
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = tween(durationMillis = 180),
        label = "button_scale"
    )

    Surface(
        modifier = modifier
            .height(if (prominent || subtitle != null) 72.dp else 64.dp)
            .scale(scale)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .semantics { role = Role.Button },
        color = colors.primary,
        shape = RoundedCornerShape(24.dp),
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 24.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = text,
                    color = onPrimary,
                    fontSize = if (prominent) 20.sp else 18.sp,
                    lineHeight = if (prominent) 24.sp else 22.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.sp
                )
                if (subtitle != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = subtitle,
                        color = onPrimary.copy(alpha = 0.82f),
                        fontSize = 13.sp,
                        lineHeight = 17.sp
                    )
                }
            }

            CircleIcon(
                icon = AppIcon.ArrowRight,
                iconColor = onPrimary,
                containerColor = onPrimary.copy(alpha = 0.15f),
                modifier = Modifier.size(42.dp)
            )
        }
    }
}

@Composable
fun SectionTitle(
    text: String,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.vahakColors
    Text(
        text = text,
        modifier = modifier,
        color = colors.headingText,
        fontSize = 16.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.sp
    )
}

@Composable
fun StatusCard(
    modifier: Modifier = Modifier,
    title: String = "All systems ready",
    subtitle: String = "Crash sensors and GPS are active",
    isActive: Boolean = false,
    isReady: Boolean = true
) {
    val colors = MaterialTheme.vahakColors
    val indicatorColor = when {
        isActive -> colors.primary
        isReady -> colors.success
        else -> Color(0xFFD49A49)
    }

    AppCard(
        modifier = modifier.heightIn(min = 88.dp),
        cornerRadius = 28.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .background(if (isReady) colors.successContainer else indicatorColor.copy(alpha = 0.14f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isReady) "✓" else "!",
                    color = indicatorColor,
                    fontSize = 18.sp,
                    lineHeight = 22.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = colors.headingText,
                    fontSize = 19.sp,
                    lineHeight = 24.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = subtitle,
                    color = colors.mutedText,
                    fontSize = 13.sp,
                    lineHeight = 17.sp,
                    maxLines = 2
                )
            }
        }
    }
}

@Composable
fun QuickAccessCard(
    title: String,
    subtitle: String,
    icon: AppIcon,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.vahakColors

    Column(
        modifier = modifier
            .fillMaxHeight()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.Center
    ) {
        CircleIcon(icon = icon, modifier = Modifier.size(36.dp))

        Spacer(modifier = Modifier.height(9.dp))

        Text(
            text = title,
            color = colors.headingText,
            fontSize = 15.sp,
            lineHeight = 19.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.sp,
            maxLines = 1
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = subtitle,
            color = colors.mutedText,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            maxLines = 2
        )
    }
}

@Composable
fun RideCard(
    date: String,
    distance: String,
    duration: String,
    status: String,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.vahakColors

    AppCard(modifier = modifier, cornerRadius = 28.dp) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(22.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = date,
                    color = colors.mutedText,
                    fontSize = 13.sp,
                    lineHeight = 17.sp
                )
                Text(
                    text = status,
                    color = colors.success,
                    fontSize = 13.sp,
                    lineHeight = 17.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Metric(value = distance, label = "Distance")
                Metric(value = duration, label = "Duration")
                Metric(value = "0", label = "Alerts")
            }
        }
    }
}

@Composable
fun AppTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    minLines: Int = 1
) {
    val colors = MaterialTheme.vahakColors
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val radius = 20.dp
    val glow by animateFloatAsState(
        targetValue = if (isFocused) 1f else 0f,
        animationSpec = tween(durationMillis = 220),
        label = "field_glow"
    )

    Column(modifier = modifier) {
        Text(
            text = label,
            color = colors.headingText,
            fontSize = 13.sp,
            lineHeight = 17.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(8.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            interactionSource = interactionSource,
            minLines = minLines,
            textStyle = TextStyle(
                color = colors.headingText,
                fontSize = 15.sp,
                lineHeight = 21.sp,
                letterSpacing = 0.sp
            ),
            modifier = Modifier
                .fillMaxWidth()
                .drawBehind {
                    val corner = radius.toPx()
                    drawRoundRect(
                        color = colors.insetShadow,
                        topLeft = Offset(0f, 2.dp.toPx()),
                        size = size,
                        cornerRadius = CornerRadius(corner, corner)
                    )
                    drawRoundRect(
                        color = colors.insetHighlight,
                        topLeft = Offset(0f, -1.dp.toPx()),
                        size = size,
                        cornerRadius = CornerRadius(corner, corner)
                    )
                    if (glow > 0f) {
                        drawRoundRect(
                            color = colors.primary.copy(alpha = 0.16f * glow),
                            size = size,
                            cornerRadius = CornerRadius(corner, corner)
                        )
                    }
                }
                .background(colors.cardHigh.copy(alpha = 0.72f), RoundedCornerShape(radius))
                .padding(horizontal = 16.dp, vertical = if (minLines > 1) 14.dp else 15.dp)
        )
    }
}

@Composable
fun ThemeToggle(
    isDarkTheme: Boolean,
    onThemeChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val progress by animateFloatAsState(
        targetValue = if (isDarkTheme) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.82f, stiffness = 300f),
        label = "theme_toggle_progress"
    )
    val trackBg by animateColorAsState(
        targetValue = if (isDarkTheme) Color(0xFF2B1B30) else Color(0xFFE6EBEE),
        animationSpec = tween(durationMillis = 200),
        label = "theme_toggle_track_bg"
    )
    val trackBorder by animateColorAsState(
        targetValue = if (isDarkTheme) Color(0xFF5A3666) else Color(0xFFCAD3D8),
        animationSpec = tween(durationMillis = 200),
        label = "theme_toggle_track_border"
    )
    val thumbColor by animateColorAsState(
        targetValue = if (isDarkTheme) Color(0xFFF3EDF5) else Color(0xFFD97724),
        animationSpec = tween(durationMillis = 200),
        label = "theme_toggle_thumb_color"
    )
    val density = LocalDensity.current

    val trackWidth = 44.dp
    val trackHeight = 24.dp
    val thumbDiameter = 18.dp

    val description = androidx.compose.ui.res.stringResource(
        if (isDarkTheme) com.manish.vahakvigil.R.string.theme_switch_to_light
        else com.manish.vahakvigil.R.string.theme_switch_to_dark
    )
    Box(
        modifier = modifier
            .size(width = 48.dp, height = 48.dp)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) {
                onThemeChange(!isDarkTheme)
            }
            .semantics {
                role = Role.Switch
                contentDescription = description
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(width = trackWidth, height = trackHeight)) {
            val corner = size.height / 2f
            // Track background
            drawRoundRect(
                color = trackBg,
                cornerRadius = CornerRadius(corner, corner)
            )
            // Track outline
            drawRoundRect(
                color = trackBorder,
                cornerRadius = CornerRadius(corner, corner),
                style = Stroke(width = 1.dp.toPx())
            )

            val thumbPx = with(density) { thumbDiameter.toPx() }
            val paddingPx = 3.dp.toPx()
            val maxTravel = size.width - thumbPx - (paddingPx * 2f)
            val cx = paddingPx + (thumbPx / 2f) + (maxTravel * progress)
            val cy = size.height / 2f

            // Thumb shadow
            drawCircle(
                color = Color.Black.copy(alpha = 0.15f),
                radius = (thumbPx / 2f) + 0.8.dp.toPx(),
                center = Offset(cx, cy + 0.8.dp.toPx())
            )
            // Thumb body
            drawCircle(
                color = thumbColor,
                radius = thumbPx / 2f,
                center = Offset(cx, cy)
            )

            // Minimalist icon accent inside thumb
            if (progress > 0.5f) {
                // Crescent moon cut-out
                drawCircle(
                    color = trackBg,
                    radius = thumbPx * 0.34f,
                    center = Offset(cx + thumbPx * 0.14f, cy - thumbPx * 0.10f)
                )
            } else {
                // Sun center dot
                drawCircle(
                    color = Color(0xFFFFF7ED),
                    radius = thumbPx * 0.20f,
                    center = Offset(cx, cy)
                )
            }
        }
    }
}

@Composable
fun MotorcycleLineArt(modifier: Modifier = Modifier) {
    ProgressiveMotorcycleLineArt(modifier = modifier, progress = 1f)
}

@Composable
fun CircleIconButton(
    icon: AppIcon,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    AppCard(
        modifier = modifier,
        cornerRadius = 22.dp,
        onClick = onClick
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            AppIconCanvas(
                icon = icon,
                color = MaterialTheme.vahakColors.mutedText,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
fun CircleIcon(
    icon: AppIcon,
    modifier: Modifier = Modifier,
    iconColor: Color = Color.Unspecified,
    containerColor: Color = Color.Unspecified
) {
    val colors = MaterialTheme.vahakColors
    val resolvedIcon = if (iconColor == Color.Unspecified) colors.primary else iconColor
    val resolvedContainer = if (containerColor == Color.Unspecified) colors.primaryContainer else containerColor

    Box(
        modifier = modifier
            .size(40.dp)
            .background(resolvedContainer, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        AppIconCanvas(icon = icon, color = resolvedIcon, modifier = Modifier.size(22.dp))
    }
}

@Composable
fun Metric(
    value: String,
    label: String,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.vahakColors
    Column(modifier = modifier) {
        Text(
            text = value,
            color = colors.headingText,
            fontSize = 23.sp,
            lineHeight = 27.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.sp
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            color = colors.mutedText,
            fontSize = 12.sp,
            lineHeight = 16.sp
        )
    }
}

@Composable
fun SoftDivider(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.background(MaterialTheme.vahakColors.divider)
    )
}

enum class AppIcon {
    Add,
    ArrowRight,
    Back,
    Contact,
    Gear,
    Heart,
    Route
}

@Composable
fun AppIconCanvas(
    icon: AppIcon,
    color: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = Stroke(
            width = size.minDimension * 0.09f,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )
        when (icon) {
            AppIcon.Add -> {
                drawLine(color, Offset(w * 0.5f, h * 0.22f), Offset(w * 0.5f, h * 0.78f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.22f, h * 0.5f), Offset(w * 0.78f, h * 0.5f), strokeWidth = stroke.width, cap = StrokeCap.Round)
            }
            AppIcon.ArrowRight -> {
                drawLine(color, Offset(w * 0.22f, h * 0.50f), Offset(w * 0.76f, h * 0.50f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.54f, h * 0.28f), Offset(w * 0.76f, h * 0.50f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.54f, h * 0.72f), Offset(w * 0.76f, h * 0.50f), strokeWidth = stroke.width, cap = StrokeCap.Round)
            }
            AppIcon.Back -> {
                drawLine(color, Offset(w * 0.72f, h * 0.20f), Offset(w * 0.30f, h * 0.50f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.30f, h * 0.50f), Offset(w * 0.72f, h * 0.80f), strokeWidth = stroke.width, cap = StrokeCap.Round)
            }
            AppIcon.Contact -> {
                drawCircle(color, radius = w * 0.14f, center = Offset(w * 0.40f, h * 0.36f), style = stroke)
                drawCircle(color, radius = w * 0.11f, center = Offset(w * 0.67f, h * 0.41f), style = stroke)
                drawLine(color, Offset(w * 0.20f, h * 0.76f), Offset(w * 0.57f, h * 0.76f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.62f, h * 0.76f), Offset(w * 0.84f, h * 0.76f), strokeWidth = stroke.width, cap = StrokeCap.Round)
            }
            AppIcon.Gear -> {
                drawCircle(color, radius = w * 0.20f, center = Offset(w * 0.5f, h * 0.5f), style = stroke)
                drawCircle(color, radius = w * 0.06f, center = Offset(w * 0.5f, h * 0.5f), style = stroke)
                listOf(
                    Offset(0.50f, 0.15f) to Offset(0.50f, 0.25f),
                    Offset(0.50f, 0.75f) to Offset(0.50f, 0.85f),
                    Offset(0.15f, 0.50f) to Offset(0.25f, 0.50f),
                    Offset(0.75f, 0.50f) to Offset(0.85f, 0.50f),
                    Offset(0.26f, 0.26f) to Offset(0.33f, 0.33f),
                    Offset(0.74f, 0.26f) to Offset(0.67f, 0.33f),
                    Offset(0.26f, 0.74f) to Offset(0.33f, 0.67f),
                    Offset(0.74f, 0.74f) to Offset(0.67f, 0.67f)
                ).forEach { (start, end) ->
                    drawLine(color, Offset(w * start.x, h * start.y), Offset(w * end.x, h * end.y), strokeWidth = stroke.width, cap = StrokeCap.Round)
                }
            }
            AppIcon.Heart -> {
                val heart = Path().apply {
                    moveTo(w * 0.50f, h * 0.73f)
                    cubicTo(w * 0.18f, h * 0.49f, w * 0.22f, h * 0.22f, w * 0.42f, h * 0.30f)
                    cubicTo(w * 0.47f, h * 0.32f, w * 0.50f, h * 0.39f, w * 0.50f, h * 0.39f)
                    cubicTo(w * 0.50f, h * 0.39f, w * 0.53f, h * 0.32f, w * 0.58f, h * 0.30f)
                    cubicTo(w * 0.78f, h * 0.22f, w * 0.82f, h * 0.49f, w * 0.50f, h * 0.73f)
                }
                drawPath(heart, color, style = stroke)
                drawLine(color, Offset(w * 0.28f, h * 0.53f), Offset(w * 0.41f, h * 0.53f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.41f, h * 0.53f), Offset(w * 0.48f, h * 0.44f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.48f, h * 0.44f), Offset(w * 0.56f, h * 0.60f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.56f, h * 0.60f), Offset(w * 0.65f, h * 0.53f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.65f, h * 0.53f), Offset(w * 0.75f, h * 0.53f), strokeWidth = stroke.width, cap = StrokeCap.Round)
            }
            AppIcon.Route -> {
                drawCircle(color, radius = w * 0.09f, center = Offset(w * 0.25f, h * 0.30f), style = stroke)
                drawCircle(color, radius = w * 0.09f, center = Offset(w * 0.72f, h * 0.50f), style = stroke)
                drawCircle(color, radius = w * 0.09f, center = Offset(w * 0.30f, h * 0.72f), style = stroke)
                drawLine(color, Offset(w * 0.34f, h * 0.32f), Offset(w * 0.63f, h * 0.48f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.63f, h * 0.52f), Offset(w * 0.39f, h * 0.70f), strokeWidth = stroke.width, cap = StrokeCap.Round)
            }
        }
    }
}
