package vn.hutech.foodexpress.driver.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import vn.hutech.foodexpress.driver.R

internal data class DriverOnboardingPage(
    val number: Int,
    val illustrationResource: Int,
    val illustrationCrop: ArtworkCrop,
    val illustrationDescriptionResource: Int,
    val titleResource: Int,
    val bodyResource: Int,
    val actionResource: Int,
    val wideAction: Boolean = false,
)

internal val OrdersOnboardingPage = DriverOnboardingPage(
    number = 1,
    illustrationResource = R.drawable.driver_onboarding_orders_reference,
    illustrationCrop = ArtworkCrop(left = 18, top = 92, width = 308, height = 360),
    illustrationDescriptionResource = R.string.onboarding_orders_illustration_description,
    titleResource = R.string.onboarding_orders_title,
    bodyResource = R.string.onboarding_orders_body,
    actionResource = R.string.onboarding_continue,
)

internal val NavigationOnboardingPage = DriverOnboardingPage(
    number = 2,
    illustrationResource = R.drawable.driver_onboarding_navigation_reference,
    illustrationCrop = ArtworkCrop(left = 14, top = 86, width = 313, height = 374),
    illustrationDescriptionResource = R.string.onboarding_navigation_illustration_description,
    titleResource = R.string.onboarding_navigation_title,
    bodyResource = R.string.onboarding_navigation_body,
    actionResource = R.string.onboarding_continue,
)

internal val IncomeOnboardingPage = DriverOnboardingPage(
    number = 3,
    illustrationResource = R.drawable.driver_onboarding_income_reference,
    illustrationCrop = ArtworkCrop(left = 20, top = 90, width = 316, height = 368),
    illustrationDescriptionResource = R.string.onboarding_income_illustration_description,
    titleResource = R.string.onboarding_income_title,
    bodyResource = R.string.onboarding_income_body,
    actionResource = R.string.onboarding_start,
    wideAction = true,
)

@Composable
internal fun DriverOnboardingScreen(
    page: DriverOnboardingPage,
    onSkip: () -> Unit,
    onBack: (() -> Unit)?,
    onContinue: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.64f),
        ) {
            ReferenceArtwork(
                imageResource = page.illustrationResource,
                crop = page.illustrationCrop,
                contentDescription = stringResource(page.illustrationDescriptionResource),
                modifier = Modifier.fillMaxSize(),
                verticalBias = 1f,
            )
            TextButton(
                onClick = onSkip,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 8.dp, end = 12.dp),
            ) {
                Text(
                    text = stringResource(R.string.onboarding_skip),
                    color = FoodExpressInk,
                    fontSize = 15.sp,
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.36f)
                .padding(start = 24.dp, top = 18.dp, end = 24.dp, bottom = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(page.titleResource),
                color = FoodExpressInk,
                fontSize = 23.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(page.bodyResource),
                modifier = Modifier.padding(top = 10.dp),
                color = FoodExpressMuted,
                fontSize = 15.sp,
                lineHeight = 22.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
            ) {
                if (onBack != null) {
                    NavigationButton(
                        text = "←",
                        contentDescription = stringResource(R.string.onboarding_back),
                        onClick = onBack,
                        filled = false,
                        modifier = Modifier.align(Alignment.CenterStart),
                    )
                }
                PageIndicator(
                    selectedPage = page.number,
                    modifier = Modifier.align(Alignment.Center),
                )
                NavigationButton(
                    text = if (page.wideAction) {
                        "${stringResource(page.actionResource)}  →"
                    } else {
                        "→"
                    },
                    contentDescription = stringResource(page.actionResource),
                    onClick = onContinue,
                    filled = true,
                    wide = page.wideAction,
                    modifier = Modifier.align(Alignment.CenterEnd),
                )
            }
        }
    }
}

@Composable
private fun PageIndicator(
    selectedPage: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(3) { index ->
            Box(
                modifier = Modifier
                    .size(if (index + 1 == selectedPage) 12.dp else 10.dp)
                    .clip(CircleShape)
                    .background(
                        if (index + 1 == selectedPage) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            Color(0xFFF0DADB)
                        },
                    ),
            )
        }
    }
}

@Composable
private fun NavigationButton(
    text: String,
    contentDescription: String,
    onClick: () -> Unit,
    filled: Boolean,
    modifier: Modifier = Modifier,
    wide: Boolean = false,
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .height(50.dp)
            .then(if (wide) Modifier.fillMaxWidth(0.37f) else Modifier.size(50.dp)),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (filled) MaterialTheme.colorScheme.primary else Color.Transparent,
            contentColor = if (filled) Color.White else MaterialTheme.colorScheme.primary,
        ),
        border = if (filled) null else androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
        ),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp),
    ) {
        Text(
            text = text,
            fontSize = if (wide) 14.sp else 25.sp,
            fontWeight = if (wide) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
        )
    }
}
