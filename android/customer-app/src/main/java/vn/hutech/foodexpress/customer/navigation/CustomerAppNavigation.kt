package vn.hutech.foodexpress.customer.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import vn.hutech.foodexpress.customer.ui.test.CustomerTestScreenA
import vn.hutech.foodexpress.customer.ui.test.CustomerTestScreenB

private const val TEST_SCREEN_A_ROUTE = "test-a"
private const val TEST_SCREEN_B_ROUTE = "test-b"

@Composable
fun CustomerAppNavigation() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = TEST_SCREEN_A_ROUTE,
    ) {
        composable(TEST_SCREEN_A_ROUTE) {
            CustomerTestScreenA(
                onNavigateToB = {
                    navController.navigate(TEST_SCREEN_B_ROUTE) {
                        launchSingleTop = true
                    }
                },
            )
        }
        composable(TEST_SCREEN_B_ROUTE) {
            CustomerTestScreenB(
                onNavigateToA = { navController.popBackStack() },
            )
        }
    }
}
