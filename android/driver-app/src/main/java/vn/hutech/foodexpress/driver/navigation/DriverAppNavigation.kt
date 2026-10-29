package vn.hutech.foodexpress.driver.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import vn.hutech.foodexpress.driver.ui.auth.DriverAuthPhase
import vn.hutech.foodexpress.driver.ui.auth.DriverAuthViewModel
import vn.hutech.foodexpress.driver.ui.auth.DriverLoginScreen
import vn.hutech.foodexpress.driver.ui.auth.DriverSessionScreen
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
private const val LOGIN_ROUTE = "login"
private const val SESSION_ROUTE = "driver-session"

@Composable
fun DriverAppNavigation() {
    val navController = rememberNavController()
    // Activity owner supplies Hilt's default factory; no extra Hilt Navigation dependency.
    val auth: DriverAuthViewModel = viewModel()
    val state by auth.state.collectAsStateWithLifecycle()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { auth.onResume() }

    LaunchedEffect(state.phase, currentRoute) {
        val destination = when {
            currentRoute == LOGIN_ROUTE && state.phase == DriverAuthPhase.AUTHENTICATED -> SESSION_ROUTE
            currentRoute == SESSION_ROUTE && state.phase == DriverAuthPhase.SIGNED_OUT -> LOGIN_ROUTE
            else -> null
        }
        if (destination != null) {
            navController.navigate(destination) {
                popUpTo(navController.graph.id)
                launchSingleTop = true
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = SPLASH_ROUTE,
    ) {
        composable(SPLASH_ROUTE) {
            DriverSplashScreen(
                onFinished = {
                    val destination = when {
                        state.phase != DriverAuthPhase.SIGNED_OUT -> SESSION_ROUTE
                        state.skipOnboarding -> LOGIN_ROUTE
                        else -> ONBOARDING_ORDERS_ROUTE
                    }
                    navController.navigate(destination) {
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
                onAgree = { navController.navigateToLogin() },
                onLater = { navController.navigateToLogin() },
            )
        }
        composable(LOGIN_ROUTE) {
            DriverLoginScreen(state = state, onLogin = auth::login, onInputChanged = auth::clearInputErrors)
        }
        composable(SESSION_ROUTE) {
            DriverSessionScreen(state = state, onRetry = auth::checkSession, onLogout = auth::logout)
        }
    }
}

private fun androidx.navigation.NavHostController.navigateToPermissionIntro() {
    navigate(PERMISSION_INTRO_ROUTE) {
        popUpTo(ONBOARDING_ORDERS_ROUTE) { inclusive = true }
        launchSingleTop = true
    }
}

private fun androidx.navigation.NavHostController.navigateToLogin() {
    navigate(LOGIN_ROUTE) {
        popUpTo(PERMISSION_INTRO_ROUTE) { inclusive = true }
        launchSingleTop = true
    }
}
