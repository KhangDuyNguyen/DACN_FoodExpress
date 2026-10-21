package vn.hutech.foodexpress.driver

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import dagger.hilt.android.AndroidEntryPoint
import vn.hutech.foodexpress.core.ui.theme.FoodExpressTheme
import vn.hutech.foodexpress.driver.navigation.DriverAppNavigation

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FoodExpressTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    DriverAppNavigation()
                }
            }
        }
    }
}
