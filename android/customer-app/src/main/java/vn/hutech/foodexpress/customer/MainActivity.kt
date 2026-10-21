package vn.hutech.foodexpress.customer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import dagger.hilt.android.AndroidEntryPoint
import vn.hutech.foodexpress.customer.navigation.CustomerAppNavigation
import vn.hutech.foodexpress.core.ui.theme.FoodExpressTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FoodExpressTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    CustomerAppNavigation()
                }
            }
        }
    }
}
