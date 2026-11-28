package vn.hutech.foodexpress.customer.navigation

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import vn.hutech.foodexpress.customer.orders.ui.MyOrdersScreen
import vn.hutech.foodexpress.customer.restaurants.ui.detail.RestaurantDetailScreen
import vn.hutech.foodexpress.customer.restaurants.ui.list.RestaurantListScreen
import kotlinx.coroutines.flow.StateFlow
import vn.hutech.foodexpress.core.notification.InboxScreen
import vn.hutech.foodexpress.core.notification.InboxSystemNotifications
import vn.hutech.foodexpress.customer.notification.CustomerNotificationViewModel
import vn.hutech.foodexpress.customer.notification.customerOrderId

private const val RESTAURANT_LIST_ROUTE  = "restaurants"
private const val RESTAURANT_DETAIL_ROUTE = "restaurants/{restaurantId}"
private const val MY_ORDERS_ROUTE        = "orders"
private const val NOTIFICATIONS_ROUTE   = "notifications"

@Composable
fun CustomerAppNavigation(
    accessTokens: StateFlow<String?>? = null,
    inboxClick: StateFlow<Long?>? = null,
    onConsumeClick: () -> Unit = {},
    onOpenOrder: ((Long) -> Unit)? = null,
) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val notification: CustomerNotificationViewModel = viewModel()
    val inbox by notification.inbox.state.collectAsStateWithLifecycle()
    val clickedId = inboxClick?.collectAsStateWithLifecycle()?.value
    val token = accessTokens?.collectAsStateWithLifecycle()?.value
    val requestPush = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    DisposableEffect(accessTokens, notification) {
        notification.connect(accessTokens)
        onDispose { notification.connect(null) }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_START) { notification.inbox.refresh() }
    LaunchedEffect(clickedId, token) {
        if (clickedId != null) {
            if (!token.isNullOrBlank()) {
                navController.navigate(NOTIFICATIONS_ROUTE) { launchSingleTop = true }
                notification.tap(clickedId) { order -> onOpenOrder?.invoke(order) }
            }
            onConsumeClick()
        }
    }

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
                onOrdersClick = {
                    navController.navigate(MY_ORDERS_ROUTE)
                },
                unreadNotifications = inbox.unreadCount,
                onNotificationsClick = { navController.navigate(NOTIFICATIONS_ROUTE) { launchSingleTop = true } },
            )
        }

        // Màn 2: Chi tiết quán + menu
        composable(
            route = RESTAURANT_DETAIL_ROUTE,
            arguments = listOf(navArgument("restaurantId") { type = NavType.LongType }),
        ) {
            RestaurantDetailScreen(
                onBack = { navController.popBackStack() },
            )
        }

        // Màn 3: Lịch sử đơn + đánh giá
        composable(MY_ORDERS_ROUTE) {
            MyOrdersScreen(
                onBack = { navController.popBackStack() },
            )
        }
        composable(NOTIFICATIONS_ROUTE) {
            LaunchedEffect(Unit) { notification.inbox.refresh() }
            InboxScreen(inbox, Color(0xFFDF490F),
                onBack = { navController.popBackStack() }, onFilter = notification.inbox::filter,
                onRetry = notification.inbox::refresh, onMore = notification.inbox::more,
                onOpen = { id -> notification.inbox.open(id) { item, _ -> customerOrderId(item)?.let { onOpenOrder?.invoke(it) } } },
                onRequestPush = if (token != null && notification.pushConfigured && Build.VERSION.SDK_INT >= 33 &&
                    !InboxSystemNotifications.canPost(context)) ({ requestPush.launch(Manifest.permission.POST_NOTIFICATIONS) }) else null)
        }
    }
}
