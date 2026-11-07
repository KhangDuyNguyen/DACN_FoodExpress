package vn.hutech.foodexpress.driver.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
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
import vn.hutech.foodexpress.driver.ui.profile.DriverProfileScreen
import vn.hutech.foodexpress.driver.ui.profile.DriverProfileViewModel
import vn.hutech.foodexpress.driver.ui.dashboard.DriverDashboardViewModel
import vn.hutech.foodexpress.driver.ui.dashboard.DriverOrderKnowledge
import vn.hutech.foodexpress.driver.ui.offer.DriverOffersViewModel
import vn.hutech.foodexpress.driver.ui.offer.DriverOfferDialog
import vn.hutech.foodexpress.driver.ui.offer.DriverOfferStatusCard
import vn.hutech.foodexpress.driver.ui.order.DriverCurrentOrderViewModel
import vn.hutech.foodexpress.driver.ui.order.DriverCurrentOrderCard
import vn.hutech.foodexpress.driver.ui.order.DriverCurrentOrderUiState
import vn.hutech.foodexpress.driver.ui.order.DriverOrderDetailScreen
import vn.hutech.foodexpress.driver.ui.order.GoogleMapsDirections
import vn.hutech.foodexpress.driver.ui.order.forDriver
import vn.hutech.foodexpress.driver.data.offer.OfferAction
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
private const val PROFILE_ROUTE = "driver-profile"
private const val ORDER_DETAIL_ROUTE = "driver-order-detail"
private const val REGISTER_INTRO_ROUTE = "register-intro"
private const val REGISTER_ACCOUNT_ROUTE = "register-account"
private const val REGISTER_VEHICLE_ROUTE = "register-vehicle"
private const val REGISTER_SUCCESS_ROUTE = "register-success"
private val REGISTER_ROUTES = setOf(REGISTER_INTRO_ROUTE, REGISTER_ACCOUNT_ROUTE, REGISTER_VEHICLE_ROUTE, REGISTER_SUCCESS_ROUTE)

@Composable
fun DriverAppNavigation() {
    val navController = rememberNavController()
    val context = LocalContext.current
    // Activity owner supplies Hilt's default factory; no extra Hilt Navigation dependency.
    val auth: DriverAuthViewModel = viewModel()
    val state by auth.state.collectAsStateWithLifecycle()
    val register: DriverRegisterViewModel = viewModel()
    val registration by register.state.collectAsStateWithLifecycle()
    val profile: DriverProfileViewModel = viewModel()
    val profileState by profile.state.collectAsStateWithLifecycle()
    val dashboard: DriverDashboardViewModel = viewModel()
    val dashboardState by dashboard.state.collectAsStateWithLifecycle()
    val offers: DriverOffersViewModel = viewModel()
    val offerState by offers.state.collectAsStateWithLifecycle()
    val orders: DriverCurrentOrderViewModel = viewModel()
    val orderState by orders.state.collectAsStateWithLifecycle()
    var foreground by androidx.compose.runtime.remember { mutableStateOf(false) }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    var profileSessionNotice by rememberSaveable { mutableStateOf<String?>(null) }
    val lifecycleOwner = LocalLifecycleOwner.current
    // Read live VM values at lifecycle/logout boundaries, not the last rendered frame.
    val syncDeliveryKnowledge: () -> Unit = {
        val session = auth.state.value
        session.user?.id?.takeIf { session.phase == DriverAuthPhase.AUTHENTICATED }?.let { id ->
            val current = orders.state.value
            val decision = offers.state.value
            val knowledge = when {
                current.ownerId != id || !current.verified -> DriverOrderKnowledge.UNKNOWN
                current.order != null -> DriverOrderKnowledge.ACTIVE
                else -> DriverOrderKnowledge.IDLE
            }
            dashboard.updateDeliveryKnowledge(id, knowledge,
                decision.pending?.action == OfferAction.ACCEPT || decision.acceptedOrderId != null)
        }
    }
    val logout: () -> Unit = {
        syncDeliveryKnowledge()
        dashboard.clear() // Capture the old owner's policy/JWT before clearing other state.
        orders.clear(); offers.clear(); auth.logout()
    }

    DisposableEffect(orders, offers) {
        orders.connectOffers(offers)
        onDispose { }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) { foreground = true; orders.setForeground(true); syncDeliveryKnowledge(); dashboard.setForeground(true); offers.setForeground(true) }
            if (event == Lifecycle.Event.ON_STOP) { syncDeliveryKnowledge(); foreground = false; orders.setForeground(false); dashboard.setForeground(false); offers.setForeground(false) }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        dashboard.setForeground(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
        foreground = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        offers.setForeground(foreground)
        orders.setForeground(foreground)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            syncDeliveryKnowledge()
            dashboard.setForeground(false)
            foreground = false
            offers.setForeground(false)
            orders.setForeground(false)
        }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { auth.onResume() }

    LaunchedEffect(state.phase, state.user?.id) {
        when (state.phase) {
            DriverAuthPhase.AUTHENTICATED -> state.user?.id?.let { offers.activateSession(it); orders.activateSession(it); dashboard.activateSession(it); syncDeliveryKnowledge() }
            DriverAuthPhase.SIGNED_OUT -> { dashboard.clear(); orders.clear(); offers.clear() }
            else -> { dashboard.pauseSessionVerification(); orders.pauseSessionVerification(); offers.pauseSessionVerification() }
        }
    }

    LaunchedEffect(state.phase, state.user?.id, orderState.ownerId, orderState.verified, orderState.order?.orderId,
        offerState.pending, offerState.acceptedOrderId) { syncDeliveryKnowledge() }

    LaunchedEffect(dashboardState.offlineConflict) {
        if (dashboardState.offlineConflict) orders.refresh()
    }

    LaunchedEffect(dashboardState.profile?.isOnline, dashboardState.profile?.status, dashboardState.statusUnknown,
        dashboardState.saving, dashboardState.loading, dashboardState.orderKnowledge,
        dashboardState.acceptPending, dashboardState.offlineConflict) {
        offers.setOnline(dashboardState.profile?.isOnline == true && dashboardState.profile?.status == "APPROVED"
            && !dashboardState.statusUnknown && !dashboardState.saving && !dashboardState.loading
            && dashboardState.orderKnowledge == DriverOrderKnowledge.IDLE && !dashboardState.acceptPending
            && !dashboardState.offlineConflict)
    }
    LaunchedEffect(offerState.sessionExpired, orderState.sessionExpired) {
        if (offerState.sessionExpired || orderState.sessionExpired) auth.checkSession()
    }

    LaunchedEffect(currentRoute, state.phase) {
        if (currentRoute == PROFILE_ROUTE && state.phase == DriverAuthPhase.AUTHENTICATED) profile.load()
        else profile.clear()
        if (currentRoute == SESSION_ROUTE && state.phase == DriverAuthPhase.AUTHENTICATED) dashboard.load()
    }
    LaunchedEffect(profileState.sessionExpired, dashboardState.sessionExpired) {
        if (profileState.sessionExpired || dashboardState.sessionExpired) {
            profileSessionNotice = "Phiên đăng nhập không còn hợp lệ. Vui lòng đăng nhập lại."
            logout()
        }
    }
    LaunchedEffect(state.phase) {
        if (state.phase == DriverAuthPhase.AUTHENTICATED) profileSessionNotice = null
    }

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
            currentRoute in setOf(SESSION_ROUTE, PROFILE_ROUTE, ORDER_DETAIL_ROUTE) && state.phase == DriverAuthPhase.SIGNED_OUT -> LOGIN_ROUTE
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
            DriverLoginScreen(state = state.copy(message = state.message ?: profileSessionNotice),
                onLogin = { identifier, password, remember ->
                    profileSessionNotice = null
                    auth.login(identifier, password, remember)
                },
                onInputChanged = { profileSessionNotice = null; auth.clearInputErrors() },
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
        composable(
            SESSION_ROUTE,
            enterTransition = { if (initialState.destination.route == PROFILE_ROUTE) EnterTransition.None else null },
            exitTransition = { if (targetState.destination.route == PROFILE_ROUTE) ExitTransition.None else null },
            popEnterTransition = { if (initialState.destination.route == PROFILE_ROUTE) EnterTransition.None else null },
            popExitTransition = { if (targetState.destination.route == PROFILE_ROUTE) ExitTransition.None else null },
        ) {
            DriverSessionScreen(state = state, onRetry = auth::checkSession, onLogout = logout,
                onProfile = { navController.navigate(PROFILE_ROUTE) { launchSingleTop = true } },
                dashboardState = dashboardState, onDashboardRetry = { dashboard.load(); orders.refresh() },
                onReceivingOrders = dashboard::changeReceivingOrders, onGpsRetry = dashboard::refreshGps,
                offerContent = { if (offerState.acceptedOrderId == null) DriverOfferStatusCard(offerState, offers::retryPending, offers::refresh) },
                currentOrderContent = {
                    val visible = orderState.forDriver(state.user?.id?.takeIf { state.phase == DriverAuthPhase.AUTHENTICATED })
                    DriverCurrentOrderCard(if (offerState.pending?.action == OfferAction.ACCEPT && visible.order == null)
                        visible.copy(verified = false, error = "Kết quả Nhận đơn vẫn cần được xác minh bằng đúng thao tác trước đó.")
                        else visible, orders::refresh, onOpenDetails = {
                            val session = auth.state.value
                            val current = orders.state.value.forDriver(session.user?.id?.takeIf { session.phase == DriverAuthPhase.AUTHENTICATED })
                            if (current.verified && current.order != null && current.error == null)
                                navController.navigate(ORDER_DETAIL_ROUTE) { launchSingleTop = true }
                        })
                },
                orderRefreshing = orderState.loading)
        }
        composable(ORDER_DETAIL_ROUTE) {
            DriverOrderDetailScreen(orderState,
                authenticatedUserId = state.user?.id?.takeIf { state.phase == DriverAuthPhase.AUTHENTICATED },
                onBack = { navController.popBackStack() },
                onRetry = { if (auth.state.value.phase == DriverAuthPhase.AUTHENTICATED) orders.refresh() else auth.checkSession() },
                onDirections = { target ->
                    val session = auth.state.value
                    GoogleMapsDirections.openCurrentOrder(context, orders.state.value,
                        session.user?.id?.takeIf { session.phase == DriverAuthPhase.AUTHENTICATED }, target)
                })
        }
        composable(
            PROFILE_ROUTE,
            enterTransition = { if (initialState.destination.route == SESSION_ROUTE) EnterTransition.None else null },
            exitTransition = { if (targetState.destination.route == SESSION_ROUTE) ExitTransition.None else null },
            popEnterTransition = { if (initialState.destination.route == SESSION_ROUTE) EnterTransition.None else null },
            popExitTransition = { if (targetState.destination.route == SESSION_ROUTE) ExitTransition.None else null },
        ) {
            if (state.phase == DriverAuthPhase.AUTHENTICATED) {
                DriverProfileScreen(
                    state = profileState, onRetry = profile::load, onEdit = profile::startEditing,
                    onCancel = profile::cancelEditing, onVehicle = profile::selectVehicle,
                    onPlate = profile::updatePlate, onSave = profile::save,
                    onHome = { navController.popBackStack(SESSION_ROUTE, false) }, onLogout = logout,
                )
            } else {
                DriverSessionScreen(state = state, onRetry = auth::checkSession, onLogout = logout, onProfile = {},
                    dashboardState = dashboardState, onDashboardRetry = dashboard::load,
                    onReceivingOrders = dashboard::changeReceivingOrders, onGpsRetry = dashboard::refreshGps)
            }
        }
    }
    if (currentRoute == SESSION_ROUTE && state.phase == DriverAuthPhase.AUTHENTICATED && foreground) {
        DriverOfferDialog(offerState, offers::accept, offers::reject, offers::refresh)
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
