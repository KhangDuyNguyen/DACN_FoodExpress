package vn.hutech.foodexpress.customer.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import vn.hutech.foodexpress.customer.restaurants.ui.detail.RestaurantDetailScreen
import vn.hutech.foodexpress.customer.restaurants.ui.list.RestaurantListScreen

private const val RESTAURANT_LIST_ROUTE = "restaurants"
private const val RESTAURANT_DETAIL_ROUTE = "restaurants/{restaurantId}"

@Composable
fun CustomerAppNavigation() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = RESTAURANT_LIST_ROUTE,
    ) {
        // Màn 1: Danh sách quán
        composable(RESTAURANT_LIST_ROUTE) {
            RestaurantListScreen(
                onRestaurantClick = { restaurantId ->
                    navController.navigate("restaurants/$restaurantId")
                },
            )
        }

        // Màn 2: Chi tiết quán + menu
        composable(
            route = RESTAURANT_DETAIL_ROUTE,
            arguments = listOf(
                navArgument("restaurantId") { type = NavType.LongType }
            ),
        ) {
            RestaurantDetailScreen(
                onBack = { navController.popBackStack() },
            )
        }
    }
}
