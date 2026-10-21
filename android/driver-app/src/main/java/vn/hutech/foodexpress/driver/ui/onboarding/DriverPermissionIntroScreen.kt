package vn.hutech.foodexpress.driver.ui.onboarding

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import vn.hutech.foodexpress.driver.R

@Composable
fun DriverPermissionIntroScreen(
    onAgree: () -> Unit,
    onLater: () -> Unit,
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        val availableHeight = maxHeight
        val compactLayout = availableHeight < 700.dp
        val scrollState = rememberScrollState()

        PermissionBackground(
            modifier = Modifier
                .fillMaxWidth()
                .height(availableHeight * 0.52f),
        )
        Column(
            modifier = (if (compactLayout) {
                Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
            } else {
                Modifier.fillMaxSize()
            }).padding(horizontal = 24.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(
                modifier = Modifier.height(
                    if (compactLayout) 72.dp else availableHeight * 0.20f,
                ),
            )
            PermissionCard(compactLayout = compactLayout)
            Text(
                text = stringResource(R.string.permissions_title),
                modifier = Modifier.padding(top = 22.dp),
                color = FoodExpressInk,
                fontSize = 21.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(R.string.permissions_body),
                modifier = Modifier.padding(top = 8.dp),
                color = FoodExpressMuted,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                textAlign = TextAlign.Center,
            )
            if (compactLayout) {
                Spacer(modifier = Modifier.height(16.dp))
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }
            Button(
                onClick = onAgree,
                modifier = Modifier
                    .widthIn(max = 342.dp)
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(
                    text = stringResource(R.string.permission_agree),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            TextButton(
                onClick = onLater,
                modifier = Modifier.padding(bottom = 2.dp),
            ) {
                Text(
                    text = stringResource(R.string.permission_later),
                    color = FoodExpressMuted,
                    fontSize = 15.sp,
                )
            }
        }
    }
}

@Composable
private fun PermissionBackground(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.drawable.driver_onboarding_permissions_reference),
        contentDescription = null,
        modifier = modifier,
        alignment = Alignment.TopCenter,
        contentScale = ContentScale.Crop,
    )
}

@Composable
private fun PermissionCard(compactLayout: Boolean) {
    Surface(
        modifier = Modifier
            .widthIn(max = 300.dp)
            .fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = Color.White,
        shadowElevation = 3.dp,
    ) {
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            PermissionRow(
                icon = PermissionIconType.Location,
                title = stringResource(R.string.permission_location_title),
                body = stringResource(R.string.permission_location_body),
                compactLayout = compactLayout,
            )
            PermissionRow(
                icon = PermissionIconType.Notification,
                title = stringResource(R.string.permission_notification_title),
                body = stringResource(R.string.permission_notification_body),
                compactLayout = compactLayout,
            )
            PermissionRow(
                icon = PermissionIconType.Camera,
                title = stringResource(R.string.permission_camera_title),
                body = stringResource(R.string.permission_camera_body),
                compactLayout = compactLayout,
            )
        }
    }
}

@Composable
private fun PermissionRow(
    icon: PermissionIconType,
    title: String,
    body: String,
    compactLayout: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = if (compactLayout) 72.dp else 90.dp)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            modifier = Modifier.size(if (compactLayout) 48.dp else 54.dp),
            shape = RoundedCornerShape(15.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.11f),
        ) {
            PermissionIcon(
                type = icon,
                modifier = Modifier.padding(12.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = FoodExpressInk,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = body,
                modifier = Modifier.padding(top = 2.dp),
                color = FoodExpressMuted,
                fontSize = 12.sp,
                lineHeight = 16.sp,
            )
        }
    }
}

private enum class PermissionIconType {
    Location,
    Notification,
    Camera,
}

@Composable
private fun PermissionIcon(
    type: PermissionIconType,
    modifier: Modifier = Modifier,
) {
    val color = MaterialTheme.colorScheme.primary
    Canvas(modifier = modifier.fillMaxSize()) {
        when (type) {
            PermissionIconType.Location -> {
                val pin = Path().apply {
                    moveTo(size.width / 2f, size.height)
                    cubicTo(
                        size.width * 0.42f,
                        size.height * 0.75f,
                        size.width * 0.18f,
                        size.height * 0.62f,
                        size.width * 0.18f,
                        size.height * 0.38f,
                    )
                    cubicTo(
                        size.width * 0.18f,
                        size.height * 0.12f,
                        size.width * 0.34f,
                        0f,
                        size.width / 2f,
                        0f,
                    )
                    cubicTo(
                        size.width * 0.66f,
                        0f,
                        size.width * 0.82f,
                        size.height * 0.12f,
                        size.width * 0.82f,
                        size.height * 0.38f,
                    )
                    cubicTo(
                        size.width * 0.82f,
                        size.height * 0.62f,
                        size.width * 0.58f,
                        size.height * 0.75f,
                        size.width / 2f,
                        size.height,
                    )
                    close()
                }
                drawPath(pin, color)
                drawCircle(Color.White, radius = size.minDimension * 0.10f)
            }

            PermissionIconType.Notification -> {
                val bell = Path().apply {
                    moveTo(size.width * 0.20f, size.height * 0.72f)
                    lineTo(size.width * 0.30f, size.height * 0.60f)
                    lineTo(size.width * 0.30f, size.height * 0.35f)
                    cubicTo(
                        size.width * 0.30f,
                        size.height * 0.14f,
                        size.width * 0.70f,
                        size.height * 0.14f,
                        size.width * 0.70f,
                        size.height * 0.35f,
                    )
                    lineTo(size.width * 0.70f, size.height * 0.60f)
                    lineTo(size.width * 0.80f, size.height * 0.72f)
                    close()
                }
                drawPath(bell, color)
                drawLine(
                    color = color,
                    start = Offset(size.width * 0.42f, size.height * 0.82f),
                    end = Offset(size.width * 0.58f, size.height * 0.82f),
                    strokeWidth = size.width * 0.10f,
                    cap = StrokeCap.Round,
                )
            }

            PermissionIconType.Camera -> {
                val stroke = Stroke(width = size.width * 0.09f)
                drawRoundRect(
                    color = color,
                    topLeft = Offset(size.width * 0.10f, size.height * 0.25f),
                    size = androidx.compose.ui.geometry.Size(size.width * 0.80f, size.height * 0.62f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.width * 0.08f),
                    style = stroke,
                )
                drawLine(
                    color = color,
                    start = Offset(size.width * 0.34f, size.height * 0.25f),
                    end = Offset(size.width * 0.42f, size.height * 0.13f),
                    strokeWidth = size.width * 0.09f,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = color,
                    start = Offset(size.width * 0.42f, size.height * 0.13f),
                    end = Offset(size.width * 0.61f, size.height * 0.13f),
                    strokeWidth = size.width * 0.09f,
                    cap = StrokeCap.Round,
                )
                drawCircle(
                    color = color,
                    radius = size.minDimension * 0.15f,
                    center = center,
                    style = stroke,
                )
            }
        }
    }
}
