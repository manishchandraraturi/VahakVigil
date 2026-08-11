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
        targetValue = if (isPressed) 4.dp else 11.dp,
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
    modifier: Modifier = Modifier
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
            .height(if (subtitle == null) 58.dp else 68.dp)
            .scale(scale)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
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
                    fontSize = 18.sp,
                    lineHeight = 22.sp,
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
fun StatusCard(modifier: Modifier = Modifier) {
    val colors = MaterialTheme.vahakColors
    val pulseTransition = rememberInfiniteTransition(label = "status_pulse_transition")
    val pulse by pulseTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600),
            repeatMode = RepeatMode.Restart
        ),
        label = "status_pulse"
    )

    AppCard(
        modifier = modifier.height(88.dp),
        cornerRadius = 28.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size((14 + 10 * pulse).dp)
                        .background(colors.success.copy(alpha = 0.18f * (1f - pulse)), CircleShape)
                )
                Box(
                    modifier = Modifier
                        .size(9.dp)
                        .background(colors.success, CircleShape)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "All systems ready",
                    color = colors.headingText,
                    fontSize = 18.sp,
                    lineHeight = 22.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Crash sensors and GPS are active",
                    color = colors.mutedText,
                    fontSize = 13.sp,
                    lineHeight = 17.sp
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
            .padding(horizontal = 18.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.Center
    ) {
        CircleIcon(icon = icon)

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = title,
            color = colors.headingText,
            fontSize = 17.sp,
            lineHeight = 21.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.sp
        )
        Spacer(modifier = Modifier.height(5.dp))
        Text(
            text = subtitle,
            color = colors.mutedText,
            fontSize = 13.sp,
            lineHeight = 17.sp
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
        animationSpec = spring(dampingRatio = 0.78f, stiffness = 260f),
        label = "theme_toggle_progress"
    )
    val trackColor by animateColorAsState(
        targetValue = if (isDarkTheme) Color(0xFF211426) else Color(0xFFEAF3FF),
        animationSpec = tween(durationMillis = 260),
        label = "theme_toggle_track"
    )
    val density = LocalDensity.current
    val width = 64.dp
    val height = 36.dp
    val thumbSize = 28.dp

    Canvas(
        modifier = modifier
            .size(width = width, height = height)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) {
                onThemeChange(!isDarkTheme)
            }
    ) {
        val radius = size.height / 2f
        drawRoundRect(
            color = trackColor,
            cornerRadius = CornerRadius(radius, radius)
        )

        val cloudAlpha = 1f - progress
        val starAlpha = progress
        drawCircle(
            color = Color.White.copy(alpha = 0.70f * cloudAlpha),
            radius = size.height * 0.12f,
            center = Offset(size.width * 0.68f, size.height * 0.64f)
        )
        drawCircle(
            color = Color.White.copy(alpha = 0.58f * cloudAlpha),
            radius = size.height * 0.09f,
            center = Offset(size.width * 0.78f, size.height * 0.66f)
        )

        listOf(
            Offset(size.width * 0.62f, size.height * 0.28f),
            Offset(size.width * 0.77f, size.height * 0.36f),
            Offset(size.width * 0.70f, size.height * 0.72f)
        ).forEachIndexed { index, point ->
            drawCircle(
                color = Color.White.copy(alpha = starAlpha * (0.72f - index * 0.12f)),
                radius = size.height * 0.035f,
                center = point
            )
        }

        val thumbPx = with(density) { thumbSize.toPx() }
        val travel = size.width - thumbPx - 4.dp.toPx()
        val cx = 2.dp.toPx() + thumbPx / 2f + travel * progress
        val cy = size.height / 2f
        val thumbColor = if (isDarkTheme) Color(0xFFF8F8F8) else Color(0xFFFFC857)
        drawCircle(thumbColor, radius = thumbPx / 2f, center = Offset(cx, cy))
        if (isDarkTheme) {
            drawCircle(
                color = trackColor,
                radius = thumbPx * 0.33f,
                center = Offset(cx + thumbPx * 0.18f, cy - thumbPx * 0.15f)
            )
        } else {
            repeat(8) { index ->
                val angle = Math.toRadians((index * 45).toDouble())
                val start = Offset(
                    x = cx + kotlin.math.cos(angle).toFloat() * thumbPx * 0.36f,
                    y = cy + kotlin.math.sin(angle).toFloat() * thumbPx * 0.36f
                )
                val end = Offset(
                    x = cx + kotlin.math.cos(angle).toFloat() * thumbPx * 0.47f,
                    y = cy + kotlin.math.sin(angle).toFloat() * thumbPx * 0.47f
                )
                drawLine(
                    color = Color(0xFFFFA928),
                    start = start,
                    end = end,
                    strokeWidth = 1.4.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
        }
    }
}

@Composable
fun MotorcycleLineArt(
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.vahakColors

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val line = colors.motorcycleLine
        val strokeWidth = (w * 0.006f).coerceIn(2.1f, 3.4f)
        val stroke = Stroke(
            width = strokeWidth,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )
        val rear = Offset(w * 0.28f, h * 0.72f)
        val front = Offset(w * 0.74f, h * 0.72f)
        val wheel = w * 0.105f

        drawLine(
            color = line,
            start = Offset(-w * 0.18f, h * 0.84f),
            end = Offset(w * 1.18f, h * 0.84f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
        drawCircle(line, radius = wheel, center = rear, style = stroke)
        drawCircle(line, radius = wheel * 0.68f, center = rear, style = stroke)
        drawCircle(line, radius = wheel, center = front, style = stroke)
        drawCircle(line, radius = wheel * 0.68f, center = front, style = stroke)
        drawCircle(line, radius = wheel * 0.16f, center = front, style = stroke)

        val body = Path().apply {
            moveTo(w * 0.13f, h * 0.70f)
            cubicTo(w * 0.18f, h * 0.49f, w * 0.31f, h * 0.48f, w * 0.37f, h * 0.58f)
            cubicTo(w * 0.42f, h * 0.42f, w * 0.52f, h * 0.34f, w * 0.62f, h * 0.40f)
            cubicTo(w * 0.58f, h * 0.50f, w * 0.50f, h * 0.54f, w * 0.39f, h * 0.58f)
            cubicTo(w * 0.36f, h * 0.66f, w * 0.33f, h * 0.72f, w * 0.28f, h * 0.76f)
            cubicTo(w * 0.39f, h * 0.79f, w * 0.55f, h * 0.78f, w * 0.67f, h * 0.70f)
            cubicTo(w * 0.71f, h * 0.63f, w * 0.74f, h * 0.57f, w * 0.78f, h * 0.53f)
        }
        drawPath(body, line, style = stroke)

        val seat = Path().apply {
            moveTo(w * 0.25f, h * 0.50f)
            cubicTo(w * 0.30f, h * 0.60f, w * 0.36f, h * 0.60f, w * 0.42f, h * 0.48f)
            cubicTo(w * 0.34f, h * 0.52f, w * 0.29f, h * 0.50f, w * 0.25f, h * 0.50f)
        }
        drawPath(seat, line, style = stroke)

        val tank = Path().apply {
            moveTo(w * 0.45f, h * 0.47f)
            cubicTo(w * 0.48f, h * 0.35f, w * 0.62f, h * 0.34f, w * 0.66f, h * 0.46f)
            cubicTo(w * 0.62f, h * 0.52f, w * 0.53f, h * 0.55f, w * 0.45f, h * 0.47f)
        }
        drawPath(tank, line, style = stroke)

        val engine = Path().apply {
            moveTo(w * 0.48f, h * 0.61f)
            cubicTo(w * 0.53f, h * 0.53f, w * 0.60f, h * 0.57f, w * 0.60f, h * 0.66f)
            cubicTo(w * 0.58f, h * 0.76f, w * 0.47f, h * 0.77f, w * 0.45f, h * 0.68f)
            cubicTo(w * 0.43f, h * 0.63f, w * 0.45f, h * 0.60f, w * 0.48f, h * 0.61f)
        }
        drawPath(engine, line, style = stroke)
        drawLine(line, Offset(w * 0.36f, h * 0.69f), Offset(w * 0.58f, h * 0.69f), strokeWidth, StrokeCap.Round)
        drawLine(line, Offset(w * 0.58f, h * 0.69f), Offset(w * 0.66f, h * 0.51f), strokeWidth, StrokeCap.Round)
        drawLine(line, Offset(w * 0.66f, h * 0.51f), Offset(w * 0.74f, h * 0.72f), strokeWidth, StrokeCap.Round)
        drawLine(line, Offset(w * 0.66f, h * 0.51f), Offset(w * 0.70f, h * 0.35f), strokeWidth, StrokeCap.Round)
        drawLine(line, Offset(w * 0.70f, h * 0.35f), Offset(w * 0.77f, h * 0.47f), strokeWidth, StrokeCap.Round)

        val handle = Path().apply {
            moveTo(w * 0.67f, h * 0.34f)
            cubicTo(w * 0.64f, h * 0.29f, w * 0.60f, h * 0.28f, w * 0.57f, h * 0.31f)
            cubicTo(w * 0.63f, h * 0.32f, w * 0.66f, h * 0.30f, w * 0.68f, h * 0.24f)
            cubicTo(w * 0.65f, h * 0.19f, w * 0.69f, h * 0.14f, w * 0.73f, h * 0.18f)
        }
        drawPath(handle, line, style = stroke)
        drawLine(line, Offset(w * 0.70f, h * 0.35f), Offset(w * 0.77f, h * 0.32f), strokeWidth, StrokeCap.Round)
        drawLine(line, Offset(w * 0.80f, h * 0.48f), Offset(w * 0.88f, h * 0.50f), strokeWidth, StrokeCap.Round)
        drawLine(line, Offset(w * 0.14f, h * 0.74f), Offset(w * 0.38f, h * 0.74f), strokeWidth, StrokeCap.Round)
    }
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
