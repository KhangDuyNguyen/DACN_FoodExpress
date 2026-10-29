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
import vn.hutech.foodexpress.driver.ui.auth.DriverRegisterIntroScreen
import vn.hutech.foodexpress.driver.ui.auth.DriverRegisterScreen
import vn.hutech.foodexpress.driver.ui.auth.DriverRegisterSuccessScreen
import vn.hutech.foodexpress.driver.ui.auth.DriverRegisterViewModel
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
private const val REGISTER_INTRO_ROUTE = "register-intro"
private const val REGISTER_ACCOUNT_ROUTE = "register-account"
private const val REGISTER_VEHICLE_ROUTE = "register-vehicle"
private const val REGISTER_SUCCESS_ROUTE = "register-success"
private val REGISTER_ROUTES = setOf(REGISTER_INTRO_ROUTE, REGISTER_ACCOUNT_ROUTE, REGISTER_VEHICLE_ROUTE, REGISTER_SUCCESS_ROUTE)

@Composable
fun DriverAppNavigation() {
    val navController = rememberNavController()
    // Activity owner supplies Hilt's default factory; no extra Hilt Navigation dependency.
    val auth: DriverAuthViewModel = viewModel()
    val state by auth.state.collectAsStateWithLifecycle()
    val register: DriverRegisterViewModel = viewModel()
    val registration by register.state.collectAsStateWithLifecycle()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { auth.onResume() }

    LaunchedEffect(registration.registered, registration.errorStep, currentRoute) {
        when {
            currentRoute == REGISTER_VEHICLE_ROUTE && registration.registered -> {
                navController.navigate(REGISTER_SUCCESS_ROUTE) {
                    popUpTo(REGISTER_INTRO_ROUTE) { inclusive = true }
                    launchSingleTop = true
                }
            }
            currentRoute == REGISTER_VEHICLE_ROUTE && (registration.errorStep == 1 || !register.hasValidAccount()) -> {
                if (!navController.popBackStack(REGISTER_ACCOUNT_ROUTE, false)) {
                    navController.navigate(REGISTER_ACCOUNT_ROUTE) {
                        popUpTo(REGISTER_VEHICLE_ROUTE) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            }
            currentRoute == REGISTER_SUCCESS_ROUTE && !registration.registered -> navController.finishRegistration()
            currentRoute != null && currentRoute !in REGISTER_ROUTES -> register.reset()
        }
    }

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
            DriverLoginScreen(state = state, onLogin = auth::login, onInputChanged = auth::clearInputErrors,
                onRegister = {
                    register.reset()
                    navController.navigate(REGISTER_INTRO_ROUTE) { launchSingleTop = true }
                })
        }
        composable(REGISTER_INTRO_ROUTE) {
            DriverRegisterIntroScreen(
                onBegin = { navController.navigate(REGISTER_ACCOUNT_ROUTE) { launchSingleTop = true } },
                onLogin = { navController.finishRegistration() },
            )
        }
        composable(REGISTER_ACCOUNT_ROUTE) {
            DriverRegisterScreen(step = 1, state = registration, onChange = register::update, onVehicle = register::selectVehicle,
                onBack = { navController.popBackStack() },
                onNext = { if (register.continueToVehicle()) navController.navigate(REGISTER_VEHICLE_ROUTE) { launchSingleTop = true } },
                onSubmit = register::submit)
        }
        composable(REGISTER_VEHICLE_ROUTE) {
            DriverRegisterScreen(step = 2, state = registration, onChange = register::update, onVehicle = register::selectVehicle,
                onBack = { navController.popBackStack() }, onNext = {}, onSubmit = register::submit)
        }
        composable(REGISTER_SUCCESS_ROUTE) {
            if (registration.registered) DriverRegisterSuccessScreen(onLogin = { navController.finishRegistration() })
        }
        composable(SESSION_ROUTE) {
            DriverSessionScreen(state = state, onRetry = auth::checkSession, onLogout = auth::logout)
        }
    }
}

private fun androidx.navigation.NavHostController.finishRegistration() {
    navigate(LOGIN_ROUTE) {
        popUpTo(LOGIN_ROUTE) { inclusive = true }
        launchSingleTop = true
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
