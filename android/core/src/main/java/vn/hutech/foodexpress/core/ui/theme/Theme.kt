package vn.hutech.foodexpress.core.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val FoodExpressLightColorScheme = lightColorScheme(
    primary = FoodExpressPrimary,
    onPrimary = Color.White,
    primaryContainer = FoodExpressPrimaryDark,
    onPrimaryContainer = Color.White,
    background = FoodExpressBackground,
    onBackground = Color.Black,
    surface = FoodExpressBackground,
    onSurface = Color.Black,
)

@Composable
fun FoodExpressTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = FoodExpressLightColorScheme,
        typography = FoodExpressTypography,
        content = content,
    )
}
