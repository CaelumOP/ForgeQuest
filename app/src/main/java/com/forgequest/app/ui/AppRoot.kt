package com.forgequest.app.ui

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.annotation.StringRes
import com.forgequest.app.R
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.forgequest.app.AppContainer
import com.forgequest.app.container
import com.forgequest.app.ui.coach.CoachScreen
import com.forgequest.app.ui.food.FoodScreen
import com.forgequest.app.ui.food.ScanScreen
import com.forgequest.app.ui.history.HistoryScreen
import com.forgequest.app.ui.history.SessionDetailScreen
import com.forgequest.app.ui.home.HomeScreen
import com.forgequest.app.ui.onboarding.OnboardingScreen
import com.forgequest.app.ui.profile.BadgesScreen
import com.forgequest.app.ui.profile.ProfileScreen
import com.forgequest.app.ui.progress.ProgressScreen
import com.forgequest.app.ui.theme.Fit
import com.forgequest.app.ui.workouts.ExerciseDetailScreen
import com.forgequest.app.ui.workouts.ExerciseLibraryScreen
import com.forgequest.app.ui.workouts.LiveWorkoutScreen
import com.forgequest.app.ui.workouts.PlanEditorScreen
import com.forgequest.app.ui.workouts.WorkoutsScreen

object Routes {
    const val HOME = "home"
    const val WORKOUTS = "workouts"
    const val FOOD = "food"
    const val COACH = "coach"
    const val PROFILE = "profile"
    const val LIVE = "live"
    const val PLAN = "plan"
    const val SCAN = "scan"
    const val EXERCISE = "exercise/{id}"
    const val HISTORY = "history"
    const val SESSION = "session/{id}"
    const val BADGES = "badges"
    const val LIBRARY = "library"
    const val PROGRESS = "progress"
    fun exercise(id: String) = "exercise/$id"
    fun session(id: Long) = "session/$id"
}

private data class Tab(val route: String, @StringRes val label: Int, val icon: ImageVector, val selectedIcon: ImageVector)

private val tabs = listOf(
    Tab(Routes.HOME, R.string.tab_home, Icons.Outlined.Home, Icons.Rounded.Home),
    Tab(Routes.WORKOUTS, R.string.tab_workouts, Icons.Outlined.FitnessCenter, Icons.Rounded.FitnessCenter),
    Tab(Routes.FOOD, R.string.tab_food, Icons.Outlined.Restaurant, Icons.Rounded.Restaurant),
    Tab(Routes.COACH, R.string.tab_coach, Icons.Outlined.AutoAwesome, Icons.Rounded.AutoAwesome),
    Tab(Routes.PROFILE, R.string.tab_profile, Icons.Outlined.AccountCircle, Icons.Rounded.AccountCircle),
)

/** App-wide snackbar so any screen can confirm an action in one line. */
val LocalSnackbar = staticCompositionLocalOf { SnackbarHostState() }

@Composable
inline fun <reified VM : ViewModel> appViewModel(crossinline create: (AppContainer) -> VM): VM {
    val context = LocalContext.current
    return viewModel(factory = viewModelFactory { initializer { create(context.container) } })
}

@Composable
fun AppRoot() {
    val context = LocalContext.current
    val settings by context.container.settings.settings.collectAsState(initial = null)
    val current = settings ?: run {
        Box(Modifier.fillMaxSize().background(Fit.colors.background))
        return
    }
    if (!current.onboarded) {
        OnboardingScreen()
        return
    }
    MainScaffold()
}

@Composable
private fun MainScaffold() {
    val nav = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val showBar = tabs.any { it.route == route }
    val c = Fit.colors

    CompositionLocalProvider(LocalSnackbar provides snackbar) {
        Scaffold(
            containerColor = c.background,
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = {
                if (showBar) {
                    NavigationBar(containerColor = c.card, tonalElevation = 0.dp) {
                        tabs.forEach { tab ->
                            val selected = route == tab.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = { nav.goToTab(tab.route) },
                                icon = { Icon(if (selected) tab.selectedIcon else tab.icon, contentDescription = null) },
                                label = { Text(stringResource(tab.label), maxLines = 1, style = MaterialTheme.typography.labelMedium) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = c.teal,
                                    selectedTextColor = c.textPrimary,
                                    indicatorColor = c.tealTrack,
                                    unselectedIconColor = c.textSecondary,
                                    unselectedTextColor = c.textSecondary,
                                ),
                            )
                        }
                    }
                }
            },
        ) { padding ->
            NavHost(
                navController = nav,
                startDestination = Routes.HOME,
                // The bar's space counts as already used, so screens that pad for the keyboard
                // (Coach input) sit right on top of it instead of a bar-height above.
                modifier = (if (showBar) PaddingValues(bottom = padding.calculateBottomPadding()) else PaddingValues(0.dp)).let { barSpace ->
                    Modifier.padding(barSpace).consumeWindowInsets(barSpace)
                },
                enterTransition = { fadeIn() },
                exitTransition = { fadeOut() },
                popEnterTransition = { fadeIn() },
                popExitTransition = { fadeOut() },
            ) {
                composable(Routes.HOME) { HomeScreen(nav) }
                composable(Routes.WORKOUTS) { WorkoutsScreen(nav) }
                composable(Routes.FOOD) { FoodScreen(nav) }
                composable(Routes.COACH) { CoachScreen(nav) }
                composable(Routes.PROFILE) { ProfileScreen(nav) }
                composable(Routes.LIVE, enterTransition = { EnterTransition.None }, exitTransition = { ExitTransition.None }) { LiveWorkoutScreen(nav) }
                composable(Routes.PLAN) { PlanEditorScreen(nav) }
                composable(Routes.SCAN) { ScanScreen(nav) }
                composable(Routes.EXERCISE, arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
                    ExerciseDetailScreen(nav, entry.arguments?.getString("id").orEmpty())
                }
                composable(Routes.HISTORY) { HistoryScreen(nav) }
                composable(Routes.BADGES) { BadgesScreen(nav) }
                composable(Routes.LIBRARY) { ExerciseLibraryScreen(nav) }
                composable(Routes.PROGRESS) { ProgressScreen(nav) }
                composable(Routes.SESSION, arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
                    SessionDetailScreen(nav, entry.arguments?.getLong("id") ?: 0L)
                }
            }
        }
    }
}

fun NavHostController.goToTab(route: String) {
    // Home is the start destination: navigating to it with saveState/restoreState restored the tab you were
    // leaving (tapping Home on Coach stayed on Coach). Going home is just popping back to it.
    if (route == Routes.HOME) {
        if (!popBackStack(Routes.HOME, inclusive = false)) navigate(Routes.HOME)
        return
    }
    navigate(route) {
        popUpTo(Routes.HOME) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
