package com.manish.vahakvigil.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.manish.vahakvigil.ui.theme.VahakVigilTheme
import com.manish.vahakvigil.ui.theme.vahakColors

@Composable
fun ProfileScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
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
                    .padding(top = 18.dp, bottom = 32.dp)
            ) {
                ProfileTopBar(onBackClick = onBackClick)

                Spacer(modifier = Modifier.height(28.dp))

                ProfileIdentity()

                Spacer(modifier = Modifier.height(28.dp))

                ProfileInfoCard(
                    title = "Medical Information",
                    subtitle = "Blood group, allergies, and conditions",
                    icon = ProfileIcon.Medical,
                    rows = listOf(
                        ProfileInfoRow("Blood Group", "O+"),
                        ProfileInfoRow("Allergies", "None added"),
                        ProfileInfoRow("Medication", "No active medication")
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                ProfileInfoCard(
                    title = "Emergency Contacts",
                    subtitle = "People notified during an emergency",
                    icon = ProfileIcon.Contacts,
                    rows = listOf(
                        ProfileInfoRow("Primary", "Ravi Sharma"),
                        ProfileInfoRow("Phone", "+91 98765 43210"),
                        ProfileInfoRow("Saved Contacts", "3 contacts")
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                ProfileInfoCard(
                    title = "Preferences",
                    subtitle = "Ride safety and alert settings",
                    icon = ProfileIcon.Gear,
                    rows = listOf(
                        ProfileInfoRow("Crash Alerts", "Enabled"),
                        ProfileInfoRow("Location Sharing", "Active"),
                        ProfileInfoRow("Language", "English")
                    )
                )

                Spacer(modifier = Modifier.height(24.dp))

                EditProfileButton(
                    onClick = {},
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun ProfileTopBar(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val appColors = MaterialTheme.vahakColors

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircleIconButton(
            icon = ProfileIcon.Back,
            onClick = onBackClick,
            modifier = Modifier.size(44.dp)
        )

        Text(
            text = "Profile",
            modifier = Modifier.weight(1f),
            color = appColors.headingText,
            fontSize = 20.sp,
            lineHeight = 24.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            letterSpacing = 0.sp
        )

        Spacer(modifier = Modifier.size(44.dp))
    }
}

@Composable
private fun ProfileIdentity(
    modifier: Modifier = Modifier
) {
    val appColors = MaterialTheme.vahakColors

    ElevatedProfileSurface(
        modifier = modifier.fillMaxWidth(),
        cornerRadius = 30.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ProfileAvatar()

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = "Manish",
                color = appColors.headingText,
                fontSize = 26.sp,
                lineHeight = 31.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "manish@example.com",
                color = appColors.mutedText,
                fontSize = 14.sp,
                lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "+91 98765 43210",
                color = appColors.mutedText,
                fontSize = 14.sp,
                lineHeight = 18.sp
            )
        }
    }
}

@Composable
private fun ProfileAvatar(
    modifier: Modifier = Modifier
) {
    val appColors = MaterialTheme.vahakColors

    Box(
        modifier = modifier
            .size(92.dp)
            .background(appColors.primaryContainer, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .background(appColors.cardHigh.copy(alpha = 0.72f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "M",
                color = appColors.primary,
                fontSize = 34.sp,
                lineHeight = 38.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun ProfileInfoCard(
    title: String,
    subtitle: String,
    icon: ProfileIcon,
    rows: List<ProfileInfoRow>,
    modifier: Modifier = Modifier
) {
    val appColors = MaterialTheme.vahakColors

    ElevatedProfileSurface(
        modifier = modifier.fillMaxWidth(),
        cornerRadius = 28.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBubble(
                    icon = icon,
                    iconColor = if (icon == ProfileIcon.Medical) appColors.success else appColors.primary,
                    containerColor = if (icon == ProfileIcon.Medical) appColors.successContainer else appColors.primaryContainer
                )

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        color = appColors.headingText,
                        fontSize = 18.sp,
                        lineHeight = 22.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    Spacer(modifier = Modifier.height(5.dp))

                    Text(
                        text = subtitle,
                        color = appColors.mutedText,
                        fontSize = 13.sp,
                        lineHeight = 17.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            rows.forEachIndexed { index, row ->
                if (index > 0) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(appColors.divider)
                    )
                }

                ProfileDetailRow(
                    label = row.label,
                    value = row.value,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun ProfileDetailRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    val appColors = MaterialTheme.vahakColors

    Row(
        modifier = modifier.padding(vertical = 13.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = appColors.mutedText,
            fontSize = 14.sp,
            lineHeight = 18.sp
        )

        Spacer(modifier = Modifier.width(18.dp))

        Text(
            text = value,
            color = appColors.headingText,
            fontSize = 14.sp,
            lineHeight = 18.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.End
        )
    }
}

@Composable
private fun EditProfileButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val appColors = MaterialTheme.vahakColors
    val onPrimary = MaterialTheme.colorScheme.onPrimary

    Surface(
        modifier = modifier
            .height(64.dp)
            .shadow(14.dp, RoundedCornerShape(24.dp), clip = false)
            .clickable(onClick = onClick),
        color = appColors.primary,
        shape = RoundedCornerShape(24.dp),
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ProfileIconCanvas(
                icon = ProfileIcon.Edit,
                color = onPrimary,
                modifier = Modifier.size(20.dp)
            )

            Spacer(modifier = Modifier.width(10.dp))

            Text(
                text = "Edit Profile",
                color = onPrimary,
                fontSize = 18.sp,
                lineHeight = 22.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun ElevatedProfileSurface(
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
    icon: ProfileIcon,
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
            ProfileIconCanvas(
                icon = icon,
                color = appColors.mutedText,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
private fun IconBubble(
    icon: ProfileIcon,
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
            .size(42.dp)
            .background(resolvedContainerColor, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        ProfileIconCanvas(
            icon = icon,
            color = resolvedIconColor,
            modifier = Modifier.size(22.dp)
        )
    }
}

private data class ProfileInfoRow(
    val label: String,
    val value: String
)

private enum class ProfileIcon {
    Back,
    Contacts,
    Edit,
    Gear,
    Medical
}

@Composable
private fun ProfileIconCanvas(
    icon: ProfileIcon,
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
            ProfileIcon.Back -> {
                drawLine(color, Offset(w * 0.72f, h * 0.20f), Offset(w * 0.30f, h * 0.50f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.30f, h * 0.50f), Offset(w * 0.72f, h * 0.80f), strokeWidth = stroke.width, cap = StrokeCap.Round)
            }

            ProfileIcon.Contacts -> {
                drawCircle(color, radius = w * 0.13f, center = Offset(w * 0.39f, h * 0.38f), style = stroke)
                drawCircle(color, radius = w * 0.10f, center = Offset(w * 0.66f, h * 0.42f), style = stroke)
                drawLine(color, Offset(w * 0.22f, h * 0.76f), Offset(w * 0.56f, h * 0.76f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.61f, h * 0.76f), Offset(w * 0.82f, h * 0.76f), strokeWidth = stroke.width, cap = StrokeCap.Round)
            }

            ProfileIcon.Edit -> {
                drawLine(color, Offset(w * 0.25f, h * 0.74f), Offset(w * 0.34f, h * 0.55f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.34f, h * 0.55f), Offset(w * 0.68f, h * 0.21f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.60f, h * 0.16f), Offset(w * 0.76f, h * 0.32f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.26f, h * 0.76f), Offset(w * 0.45f, h * 0.71f), strokeWidth = stroke.width, cap = StrokeCap.Round)
            }

            ProfileIcon.Gear -> {
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

            ProfileIcon.Medical -> {
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
        }
    }
}

@Preview(showBackground = true, widthDp = 412, heightDp = 917)
@Composable
private fun ProfileScreenPreview() {
    VahakVigilTheme(dynamicColor = false) {
        ProfileScreen(onBackClick = {})
    }
}
