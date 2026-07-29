package com.manish.vahakvigil.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.manish.vahakvigil.R
import com.manish.vahakvigil.ui.theme.VahakVigilTheme
import com.manish.vahakvigil.ui.theme.vahakColors

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    onStartMonitoringClick: () -> Unit = {},
    onEmergencyContactsClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onProfileClick: () -> Unit = {}
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = Color.Transparent
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val horizontalPadding = if (maxWidth < 360.dp) 20.dp else 28.dp
            val topPadding = if (maxHeight < 760.dp) 24.dp else 36.dp
            val heroHeight = when {
                maxWidth < 360.dp -> 248.dp
                maxHeight < 760.dp -> 258.dp
                else -> 276.dp
            }

            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxSize()
                    .widthIn(max = 420.dp)
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = horizontalPadding)
                    .padding(top = topPadding, bottom = 32.dp)
            ) {
                GreetingHeader()

                Spacer(modifier = Modifier.height(14.dp))

                HeroStatusStack(
                    heroHeight = heroHeight,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(18.dp))

                StartRideButton(
                    onClick = onStartMonitoringClick,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(36.dp))

                SectionTitle(text = "Quick access")

                Spacer(modifier = Modifier.height(18.dp))

                QuickAccessGrid(
                    onMedicalClick = onProfileClick,
                    onContactsClick = onEmergencyContactsClick,
                    onHistoryClick = {},
                    onSettingsClick = onSettingsClick,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(38.dp))

                LastRideHeader()

                Spacer(modifier = Modifier.height(18.dp))

                LastRideCard(modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun GreetingHeader(
    modifier: Modifier = Modifier
) {
    val appColors = MaterialTheme.vahakColors

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Column {
            Text(
                text = "Tuesday, 28 July",
                color = appColors.mutedText,
                fontSize = 14.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Good morning,\nManish",
                color = appColors.headingText,
                fontSize = 32.sp,
                lineHeight = 37.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.sp
            )
        }

        CircleIconButton(
            icon = HomeIcon.Bell,
            onClick = {},
            modifier = Modifier
                .padding(top = 24.dp)
                .size(48.dp)
        )
    }
}

@Composable
private fun HeroStatusStack(
    heroHeight: Dp,
    modifier: Modifier = Modifier
) {
    val backgroundVeil = MaterialTheme.colorScheme.background.copy(alpha = 0.88f)

    Box(
        modifier = modifier.height(heroHeight + 52.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(heroHeight),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.rider_hero),
                contentDescription = "Illustration of a smiling rider on a motorcycle",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )

            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(74.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, backgroundVeil)
                        )
                    )
            )
        }

        SystemStatusCard(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
        )
    }
}

@Composable
private fun SystemStatusCard(
    modifier: Modifier = Modifier
) {
    val appColors = MaterialTheme.vahakColors

    ElevatedHomeSurface(
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
                modifier = Modifier
                    .size(9.dp)
                    .background(appColors.success, CircleShape)
            )

            Spacer(modifier = Modifier.width(15.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "All systems ready",
                    color = appColors.headingText,
                    fontSize = 18.sp,
                    lineHeight = 22.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Crash sensors and GPS are active",
                    color = appColors.mutedText,
                    fontSize = 13.sp,
                    lineHeight = 17.sp,
                    fontWeight = FontWeight.Normal
                )
            }

        }
    }
}

@Composable
private fun StartRideButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val appColors = MaterialTheme.vahakColors
    val onPrimary = MaterialTheme.colorScheme.onPrimary

    Surface(
        modifier = modifier
            .height(68.dp)
            .shadow(14.dp, RoundedCornerShape(24.dp), clip = false)
            .clickable(onClick = onClick),
        color = appColors.primary,
        shape = RoundedCornerShape(24.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 28.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Start ride",
                    color = onPrimary,
                    fontSize = 20.sp,
                    lineHeight = 24.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Protection turns on instantly",
                    color = onPrimary.copy(alpha = 0.82f),
                    fontSize = 13.sp,
                    lineHeight = 17.sp
                )
            }

            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(onPrimary.copy(alpha = 0.16f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                VahakIcon(
                    icon = HomeIcon.ArrowRight,
                    color = onPrimary,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(
    text: String,
    modifier: Modifier = Modifier
) {
    val appColors = MaterialTheme.vahakColors

    Text(
        text = text,
        modifier = modifier,
        color = appColors.mutedText,
        fontSize = 16.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Bold
    )
}

@Composable
private fun QuickAccessGrid(
    onMedicalClick: () -> Unit,
    onContactsClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    ElevatedHomeSurface(
        modifier = modifier.height(282.dp),
        cornerRadius = 28.dp
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(modifier = Modifier.weight(1f)) {
                QuickActionCell(
                    title = "Medical",
                    subtitle = "Profile",
                    icon = HomeIcon.Medical,
                    onClick = onMedicalClick,
                    modifier = Modifier.weight(1f)
                )

                VerticalDivider()

                QuickActionCell(
                    title = "Contacts",
                    subtitle = "3 saved",
                    icon = HomeIcon.Contacts,
                    onClick = onContactsClick,
                    modifier = Modifier.weight(1f)
                )
            }

            HorizontalDivider()

            Row(modifier = Modifier.weight(1f)) {
                QuickActionCell(
                    title = "History",
                    subtitle = "24 rides",
                    icon = HomeIcon.Route,
                    onClick = onHistoryClick,
                    modifier = Modifier.weight(1f)
                )

                VerticalDivider()

                QuickActionCell(
                    title = "Settings",
                    subtitle = "Sensors",
                    icon = HomeIcon.Gear,
                    onClick = onSettingsClick,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun QuickActionCell(
    title: String,
    subtitle: String,
    icon: HomeIcon,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val appColors = MaterialTheme.vahakColors

    Column(
        modifier = modifier
            .fillMaxHeight()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.Center
    ) {
        IconBubble(icon = icon)

        Spacer(modifier = Modifier.height(18.dp))

        Text(
            text = title,
            color = appColors.headingText,
            fontSize = 17.sp,
            lineHeight = 21.sp,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = subtitle,
            color = appColors.mutedText,
            fontSize = 13.sp,
            lineHeight = 17.sp
        )
    }
}

@Composable
private fun LastRideHeader(
    modifier: Modifier = Modifier
) {
    val appColors = MaterialTheme.vahakColors

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        SectionTitle(text = "Last ride")

        Row(
            modifier = Modifier.clickable(onClick = {}),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Details",
                color = appColors.primary,
                fontSize = 13.sp,
                lineHeight = 17.sp,
                fontWeight = FontWeight.Bold
            )

            VahakIcon(
                icon = HomeIcon.ChevronRight,
                color = appColors.primary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun LastRideCard(
    modifier: Modifier = Modifier
) {
    val appColors = MaterialTheme.vahakColors

    ElevatedHomeSurface(
        modifier = modifier.height(140.dp),
        cornerRadius = 28.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 24.dp)
        ) {
            Text(
                text = "Yesterday, 6:12 PM",
                color = appColors.mutedText,
                fontSize = 13.sp,
                lineHeight = 17.sp
            )

            Spacer(modifier = Modifier.weight(1f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                RideMetric(
                    value = "18.4",
                    unit = "km",
                    label = "Distance"
                )

                RideMetric(
                    value = "42",
                    unit = "min",
                    label = "Duration"
                )

                RideMetric(
                    value = "Safe",
                    label = "Status",
                    valueColor = appColors.success,
                    textAlign = TextAlign.Start
                )
            }
        }
    }
}

@Composable
private fun RideMetric(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    unit: String? = null,
    valueColor: Color = Color.Unspecified,
    textAlign: TextAlign = TextAlign.Start
) {
    val appColors = MaterialTheme.vahakColors
    val resolvedValueColor = if (valueColor == Color.Unspecified) appColors.headingText else valueColor

    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = value,
                color = resolvedValueColor,
                fontSize = 24.sp,
                lineHeight = 28.sp,
                fontWeight = FontWeight.Bold,
                textAlign = textAlign
            )

            if (unit != null) {
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                    text = unit,
                    modifier = Modifier.offset(y = (-2).dp),
                    color = appColors.mutedText,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = label,
            color = appColors.mutedText,
            fontSize = 12.sp,
            lineHeight = 16.sp
        )
    }
}

@Composable
private fun ElevatedHomeSurface(
    modifier: Modifier = Modifier,
    cornerRadius: Dp,
    content: @Composable () -> Unit
) {
    val appColors = MaterialTheme.vahakColors

    Surface(
        modifier = modifier.shadow(
            elevation = 10.dp,
            shape = RoundedCornerShape(cornerRadius),
            clip = false
        ),
        shape = RoundedCornerShape(cornerRadius),
        color = appColors.card,
        tonalElevation = 0.dp,
        content = content
    )
}

@Composable
private fun CircleIconButton(
    icon: HomeIcon,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val appColors = MaterialTheme.vahakColors

    Surface(
        modifier = modifier
            .shadow(10.dp, CircleShape, clip = false)
            .clickable(onClick = onClick),
        shape = CircleShape,
        color = appColors.cardHigh,
        tonalElevation = 0.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            VahakIcon(
                icon = icon,
                color = appColors.mutedText,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
private fun IconBubble(
    icon: HomeIcon,
    modifier: Modifier = Modifier,
    iconColor: Color = Color.Unspecified,
    containerColor: Color = Color.Unspecified
) {
    val appColors = MaterialTheme.vahakColors
    val resolvedIconColor = if (iconColor == Color.Unspecified) appColors.primary else iconColor
    val resolvedContainerColor = if (containerColor == Color.Unspecified) {
        appColors.primaryContainer
    } else {
        containerColor
    }

    Box(
        modifier = modifier
            .size(40.dp)
            .background(resolvedContainerColor, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        VahakIcon(
            icon = icon,
            color = resolvedIconColor,
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
private fun VerticalDivider() {
    val appColors = MaterialTheme.vahakColors

    Box(
        modifier = Modifier
            .width(1.dp)
            .fillMaxHeight()
            .background(appColors.divider)
    )
}

@Composable
private fun HorizontalDivider() {
    val appColors = MaterialTheme.vahakColors

    Box(
        modifier = Modifier
            .height(1.dp)
            .fillMaxWidth()
            .background(appColors.divider)
    )
}

private enum class HomeIcon {
    ArrowRight,
    Bell,
    ChevronRight,
    Contacts,
    Gear,
    Medical,
    Route,
    ShieldCheck
}

@Composable
private fun VahakIcon(
    icon: HomeIcon,
    color: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = Stroke(
            width = size.minDimension * 0.09f,
            cap = StrokeCap.Round
        )

        when (icon) {
            HomeIcon.ArrowRight -> {
                drawLine(color, Offset(w * 0.22f, h * 0.50f), Offset(w * 0.76f, h * 0.50f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.54f, h * 0.28f), Offset(w * 0.76f, h * 0.50f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.54f, h * 0.72f), Offset(w * 0.76f, h * 0.50f), strokeWidth = stroke.width, cap = StrokeCap.Round)
            }

            HomeIcon.Bell -> {
                val bell = Path().apply {
                    moveTo(w * 0.28f, h * 0.66f)
                    lineTo(w * 0.72f, h * 0.66f)
                    quadraticBezierTo(w * 0.65f, h * 0.54f, w * 0.65f, h * 0.38f)
                    quadraticBezierTo(w * 0.65f, h * 0.18f, w * 0.50f, h * 0.18f)
                    quadraticBezierTo(w * 0.35f, h * 0.18f, w * 0.35f, h * 0.38f)
                    quadraticBezierTo(w * 0.35f, h * 0.54f, w * 0.28f, h * 0.66f)
                }
                drawPath(bell, color, style = stroke)
                drawLine(color, Offset(w * 0.44f, h * 0.78f), Offset(w * 0.56f, h * 0.78f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawCircle(color, radius = w * 0.13f, center = Offset(w * 0.76f, h * 0.22f))
            }

            HomeIcon.ChevronRight -> {
                drawLine(color, Offset(w * 0.38f, h * 0.24f), Offset(w * 0.62f, h * 0.50f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.38f, h * 0.76f), Offset(w * 0.62f, h * 0.50f), strokeWidth = stroke.width, cap = StrokeCap.Round)
            }

            HomeIcon.Contacts -> {
                drawCircle(color, radius = w * 0.13f, center = Offset(w * 0.39f, h * 0.38f), style = stroke)
                drawCircle(color, radius = w * 0.10f, center = Offset(w * 0.66f, h * 0.42f), style = stroke)
                drawLine(color, Offset(w * 0.22f, h * 0.76f), Offset(w * 0.56f, h * 0.76f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.61f, h * 0.76f), Offset(w * 0.82f, h * 0.76f), strokeWidth = stroke.width, cap = StrokeCap.Round)
            }

            HomeIcon.Gear -> {
                drawCircle(color, radius = w * 0.19f, center = Offset(w * 0.5f, h * 0.5f), style = stroke)
                drawCircle(color, radius = w * 0.06f, center = Offset(w * 0.5f, h * 0.5f), style = stroke)
                val spokes = listOf(
                    Offset(0.50f, 0.15f) to Offset(0.50f, 0.25f),
                    Offset(0.50f, 0.75f) to Offset(0.50f, 0.85f),
                    Offset(0.15f, 0.50f) to Offset(0.25f, 0.50f),
                    Offset(0.75f, 0.50f) to Offset(0.85f, 0.50f),
                    Offset(0.26f, 0.26f) to Offset(0.33f, 0.33f),
                    Offset(0.74f, 0.26f) to Offset(0.67f, 0.33f),
                    Offset(0.26f, 0.74f) to Offset(0.33f, 0.67f),
                    Offset(0.74f, 0.74f) to Offset(0.67f, 0.67f)
                )
                spokes.forEach { (start, end) ->
                    drawLine(
                        color,
                        Offset(w * start.x, h * start.y),
                        Offset(w * end.x, h * end.y),
                        strokeWidth = stroke.width,
                        cap = StrokeCap.Round
                    )
                }
            }

            HomeIcon.Medical -> {
                val heart = Path().apply {
                    moveTo(w * 0.50f, h * 0.72f)
                    cubicTo(w * 0.18f, h * 0.48f, w * 0.22f, h * 0.22f, w * 0.42f, h * 0.30f)
                    cubicTo(w * 0.47f, h * 0.32f, w * 0.50f, h * 0.38f, w * 0.50f, h * 0.38f)
                    cubicTo(w * 0.50f, h * 0.38f, w * 0.53f, h * 0.32f, w * 0.58f, h * 0.30f)
                    cubicTo(w * 0.78f, h * 0.22f, w * 0.82f, h * 0.48f, w * 0.50f, h * 0.72f)
                }
                drawPath(heart, color, style = stroke)
                drawLine(color, Offset(w * 0.27f, h * 0.52f), Offset(w * 0.40f, h * 0.52f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.40f, h * 0.52f), Offset(w * 0.47f, h * 0.43f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.47f, h * 0.43f), Offset(w * 0.56f, h * 0.60f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.56f, h * 0.60f), Offset(w * 0.64f, h * 0.52f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.64f, h * 0.52f), Offset(w * 0.74f, h * 0.52f), strokeWidth = stroke.width, cap = StrokeCap.Round)
            }

            HomeIcon.Route -> {
                drawCircle(color, radius = w * 0.09f, center = Offset(w * 0.25f, h * 0.30f), style = stroke)
                drawCircle(color, radius = w * 0.09f, center = Offset(w * 0.72f, h * 0.50f), style = stroke)
                drawCircle(color, radius = w * 0.09f, center = Offset(w * 0.30f, h * 0.72f), style = stroke)
                drawLine(color, Offset(w * 0.34f, h * 0.32f), Offset(w * 0.63f, h * 0.48f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.63f, h * 0.52f), Offset(w * 0.39f, h * 0.70f), strokeWidth = stroke.width, cap = StrokeCap.Round)
            }

            HomeIcon.ShieldCheck -> {
                val shield = Path().apply {
                    moveTo(w * 0.50f, h * 0.14f)
                    lineTo(w * 0.75f, h * 0.24f)
                    lineTo(w * 0.70f, h * 0.62f)
                    quadraticBezierTo(w * 0.66f, h * 0.78f, w * 0.50f, h * 0.87f)
                    quadraticBezierTo(w * 0.34f, h * 0.78f, w * 0.30f, h * 0.62f)
                    lineTo(w * 0.25f, h * 0.24f)
                    close()
                }
                drawPath(shield, color, style = stroke)
                drawLine(color, Offset(w * 0.39f, h * 0.51f), Offset(w * 0.47f, h * 0.60f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.47f, h * 0.60f), Offset(w * 0.62f, h * 0.42f), strokeWidth = stroke.width, cap = StrokeCap.Round)
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 412, heightDp = 917)
@Composable
private fun HomeScreenPreview() {
    VahakVigilTheme(dynamicColor = false) {
        HomeScreen()
    }
}
