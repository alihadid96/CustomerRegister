package com.customerregister.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.customerregister.app.R
import com.customerregister.app.domain.StatusFilter
import com.customerregister.app.ui.screens.CustomerDetailScreen
import com.customerregister.app.ui.screens.CustomerEditScreen
import com.customerregister.app.ui.screens.CustomersScreen
import com.customerregister.app.ui.screens.DashboardScreen
import com.customerregister.app.ui.screens.NotificationsScreen
import com.customerregister.app.ui.screens.RenewScreen
import com.customerregister.app.ui.screens.SettingsScreen

private object Routes {
    const val DASHBOARD = "dashboard"
    const val CUSTOMERS = "customers"
    const val NOTIFICATIONS = "notifications"
    const val SETTINGS = "settings"
    const val DETAIL = "customer/{id}"
    const val EDIT = "edit?id={id}"
    const val RENEW = "renew/{id}"

    fun detail(id: Long) = "customer/$id"
    fun edit(id: Long? = null) = if (id == null) "edit" else "edit?id=$id"
    fun renew(id: Long) = "renew/$id"
}

private data class Tab(val route: String, val label: Int, val icon: ImageVector)

private val tabs = listOf(
    Tab(Routes.DASHBOARD, R.string.nav_dashboard, Icons.Rounded.Dashboard),
    Tab(Routes.CUSTOMERS, R.string.nav_customers, Icons.Rounded.People),
    Tab(Routes.NOTIFICATIONS, R.string.nav_notifications, Icons.Rounded.Notifications),
    Tab(Routes.SETTINGS, R.string.nav_settings, Icons.Rounded.Settings),
)

private fun NavHostController.navigateTab(route: String) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}

@Composable
fun AppRoot(
    canPost: Boolean,
    onRequestPermission: () -> Unit,
    openCustomerId: Long?,
    onCustomerOpened: () -> Unit,
) {
    val vm = appViewModel("main") { c, _ -> MainViewModel(c) }
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val destination = backStack?.destination
    val showBar = tabs.any { it.route == destination?.route }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refreshToday() }

    // Opened from a notification: go straight to the customer's profile.
    LaunchedEffect(openCustomerId) {
        if (openCustomerId != null && openCustomerId > 0) {
            nav.navigate(Routes.detail(openCustomerId)) { launchSingleTop = true }
            onCustomerOpened()
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            AnimatedVisibility(
                visible = showBar,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
            ) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                    tabs.forEach { tab ->
                        val selected = destination?.hierarchy?.any { it.route == tab.route } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = { nav.navigateTab(tab.route) },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(stringResource(tab.label), maxLines = 1) },
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                            ),
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = Routes.DASHBOARD,
            modifier = Modifier.padding(padding).consumeWindowInsets(padding),
            enterTransition = { fadeIn(tween(220)) + slideInHorizontally(tween(220)) { it / 10 } },
            exitTransition = { fadeOut(tween(150)) },
            popEnterTransition = { fadeIn(tween(220)) },
            popExitTransition = { fadeOut(tween(150)) + slideOutHorizontally(tween(220)) { it / 10 } },
        ) {
            composable(Routes.DASHBOARD) {
                DashboardScreen(
                    vm = vm,
                    onAddCustomer = { nav.navigate(Routes.edit()) },
                    onOpenCustomer = { nav.navigate(Routes.detail(it)) },
                    onOpenList = { f: StatusFilter ->
                        vm.filter.value = f
                        nav.navigateTab(Routes.CUSTOMERS)
                    },
                )
            }
            composable(Routes.CUSTOMERS) {
                CustomersScreen(
                    vm = vm,
                    onAddCustomer = { nav.navigate(Routes.edit()) },
                    onOpenCustomer = { nav.navigate(Routes.detail(it)) },
                )
            }
            composable(Routes.NOTIFICATIONS) {
                NotificationsScreen(
                    vm = vm,
                    canPost = canPost,
                    onRequestPermission = onRequestPermission,
                    onOpenCustomer = { nav.navigate(Routes.detail(it)) },
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(canPost = canPost, onRequestPermission = onRequestPermission)
            }
            composable(
                Routes.DETAIL,
                arguments = listOf(navArgument("id") { type = NavType.LongType }),
            ) { entry ->
                val id = entry.arguments?.getLong("id") ?: 0L
                CustomerDetailScreen(
                    id = id,
                    onBack = { nav.popBackStack() },
                    onEdit = { nav.navigate(Routes.edit(it)) },
                    onRenew = { nav.navigate(Routes.renew(it)) },
                )
            }
            composable(
                Routes.EDIT,
                arguments = listOf(navArgument("id") { type = NavType.LongType; defaultValue = -1L }),
            ) { entry ->
                val id = entry.arguments?.getLong("id")?.takeIf { it > 0 }
                CustomerEditScreen(
                    id = id,
                    onBack = { nav.popBackStack() },
                    onSaved = { savedId, isNew ->
                        nav.popBackStack()
                        if (isNew) nav.navigate(Routes.detail(savedId))
                    },
                )
            }
            composable(
                Routes.RENEW,
                arguments = listOf(navArgument("id") { type = NavType.LongType }),
            ) { entry ->
                val id = entry.arguments?.getLong("id") ?: 0L
                RenewScreen(id = id, onBack = { nav.popBackStack() })
            }
        }
    }
}
