package vn.hutech.foodexpress.driver.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import vn.hutech.foodexpress.driver.ui.onboarding.DriverLoginPlaceholderScreen
import vn.hutech.foodexpress.driver.ui.onboarding.DriverOnboardingScreen
import vn.hutech.foodexpress.driver.ui.onboarding.DriverPermissionIntroScreen
import vn.hutech.foodexpress.driver.ui.onboarding.DriverSplashScreen
import vn.hutech.foodexpress.driver.ui.onboarding.IncomeOnboardingPage
import vn.hutech.foodexpress.driver.ui.onboarding.NavigationOnboardingPage
import vn.hutech.foodexpress.driver.ui.onboarding.OrdersOnboardingPage

private const val SPLASH_ROUTE = "splash"
private const val ONBOARDING_ORDERS_ROUTE = "onboarding-orders"
private const val ONBOARDING_NAVIGATION_ROUTE = "onboarding-navigation"
private const val ONBOARDING_INCOME_ROUTE = "onboarding-income"
private const val PERMISSION_INTRO_ROUTE = "permission-intro"
private const val LOGIN_PLACEHOLDER_ROUTE = "login-placeholder"

@Composable
fun DriverAppNavigation() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = SPLASH_ROUTE,
    ) {
        composable(SPLASH_ROUTE) {
            DriverSplashScreen(
                onFinished = {
                    navController.navigate(ONBOARDING_ORDERS_ROUTE) {
                        popUpTo(SPLASH_ROUTE) { inclusive = true }
                        launchSingleTop = true
                    }
                },
            )
        }
        composable(ONBOARDING_ORDERS_ROUTE) {
            DriverOnboardingScreen(
                page = OrdersOnboardingPage,
                onSkip = { navController.navigateToPermissionIntro() },
                onBack = null,
                onContinue = {
                    navController.navigate(ONBOARDING_NAVIGATION_ROUTE) {
                        launchSingleTop = true
                    }
                },
            )
        }
        composable(ONBOARDING_NAVIGATION_ROUTE) {
            DriverOnboardingScreen(
                page = NavigationOnboardingPage,
                onSkip = { navController.navigateToPermissionIntro() },
                onBack = { navController.popBackStack() },
                onContinue = {
                    navController.navigate(ONBOARDING_INCOME_ROUTE) {
                        launchSingleTop = true
                    }
                },
            )
        }
        composable(ONBOARDING_INCOME_ROUTE) {
            DriverOnboardingScreen(
                page = IncomeOnboardingPage,
                onSkip = { navController.navigateToPermissionIntro() },
                onBack = { navController.popBackStack() },
                onContinue = { navController.navigateToPermissionIntro() },
            )
        }
        composable(PERMISSION_INTRO_ROUTE) {
            DriverPermissionIntroScreen(
                onAgree = { navController.navigateToLoginPlaceholder() },
                onLater = { navController.navigateToLoginPlaceholder() },
            )
        }
        composable(LOGIN_PLACEHOLDER_ROUTE) {
            DriverLoginPlaceholderScreen()
        }
    }
}

private fun androidx.navigation.NavHostController.navigateToPermissionIntro() {
    navigate(PERMISSION_INTRO_ROUTE) {
        popUpTo(ONBOARDING_ORDERS_ROUTE) { inclusive = true }
        launchSingleTop = true
    }
}

private fun androidx.navigation.NavHostController.navigateToLoginPlaceholder() {
    navigate(LOGIN_PLACEHOLDER_ROUTE) {
        popUpTo(PERMISSION_INTRO_ROUTE) { inclusive = true }
        launchSingleTop = true
    }
}
