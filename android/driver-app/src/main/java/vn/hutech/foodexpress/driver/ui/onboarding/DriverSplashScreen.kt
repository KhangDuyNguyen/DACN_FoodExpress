package vn.hutech.foodexpress.driver.ui.onboarding

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import vn.hutech.foodexpress.driver.R

@Composable
fun DriverSplashScreen(onFinished: () -> Unit) {
    val progress = remember { Animatable(0f) }
    val currentOnFinished = rememberUpdatedState(onFinished)

    LaunchedEffect(Unit) {
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 1_400, easing = LinearEasing),
        )
        currentOnFinished.value()
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        val heightScale = (maxHeight.value / 853f).coerceIn(0.78f, 1.08f)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(126.dp * heightScale))
            ReferenceArtwork(
                imageResource = R.drawable.driver_splash_reference,
                crop = ArtworkCrop(left = 126, top = 166, width = 150, height = 106),
                contentDescription = stringResource(R.string.splash_logo_description),
                modifier = Modifier
                    .fillMaxWidth(0.44f)
                    .height(96.dp * heightScale),
            )
            Spacer(modifier = Modifier.height(7.dp * heightScale))
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.splash_food),
                    color = FoodExpressInk,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = " ${stringResource(R.string.splash_express)}",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Text(
                text = stringResource(R.string.splash_tagline),
                modifier = Modifier.padding(top = 2.dp),
                color = FoodExpressMuted,
                fontSize = 16.sp,
            )
            Spacer(modifier = Modifier.height(8.dp * heightScale))
            ReferenceArtwork(
                imageResource = R.drawable.driver_splash_reference,
                crop = ArtworkCrop(left = 16, top = 342, width = 384, height = 338),
                contentDescription = stringResource(R.string.splash_illustration_description),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(350.dp * heightScale),
            )
            Spacer(modifier = Modifier.height(45.dp * heightScale))
            LinearProgressIndicator(
                progress = { progress.value },
                modifier = Modifier
                    .fillMaxWidth(0.52f)
                    .height(5.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = Color(0xFFF8DDDD),
                strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
            )
            Text(
                text = stringResource(R.string.splash_loading),
                modifier = Modifier.padding(top = 14.dp, bottom = 20.dp),
                color = FoodExpressMuted,
                fontSize = 14.sp,
            )
        }
    }
}

internal val FoodExpressInk = Color(0xFF142D3A)
internal val FoodExpressMuted = Color(0xFF61727E)
