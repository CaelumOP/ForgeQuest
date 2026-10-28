package com.forgequest.app.ui

import androidx.compose.runtime.setValue
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import com.forgequest.app.ui.theme.ForgeMotion
import com.forgequest.app.ui.theme.LocalReducedMotion
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalConfiguration
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
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextAlign
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
    const val TOUR = "tour"
    const val WORKOUTS = "workouts"
    const val FOOD = "food"
    const val COACH = "coach"
    const val PROFILE = "profile"
    const val LIVE = "live"
    const val PLAN = "plan?day={day}"
    const val SCAN = "scan"
    const val EXERCISE = "exercise/{id}"
    const val HISTORY = "history"
    const val SESSION = "session/{id}"
    const val BADGES = "badges"
    const val LIBRARY = "library"
    const val PROGRESS = "progress"
    fun exercise(id: String) = "exercise/$id"
    fun session(id: Long) = "session/$id"
    /** The plan editor, opened on [day] (1 = Monday) or on today. */
    fun plan(day: Int = 0) = "plan?day=$day"
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
        // First run: language (both scripts shown), then the feature slides, then setup. Read from prefs so the
        // activity restart after picking a language re-reads it (saved state would bring the language screen back).
        var langDone by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(com.forgequest.app.ui.onboarding.TourPrefs.languageChosen(context)) }
        var tourDone by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(com.forgequest.app.ui.onboarding.TourPrefs.tourSeen(context)) }
        when {
            !langDone -> com.forgequest.app.ui.onboarding.LanguageChoice { langDone = true }
            !tourDone -> com.forgequest.app.ui.onboarding.AppTour(androidx.compose.ui.res.stringResource(R.string.tour_start)) {
                com.forgequest.app.ui.onboarding.TourPrefs.setTourSeen(context); tourDone = true
            }
            else -> OnboardingScreen()
        }
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
    val reduced = LocalReducedMotion.current
    // A Gemini meal shared into the app or picked in the Food tab inbox opens on the scan screen's review sheet.
    val context = LocalContext.current
    val review by context.container.meals.review.collectAsState()
    LaunchedEffect(review) { if (review != null && route != Routes.SCAN) nav.navigate(Routes.SCAN) { launchSingleTop = true } }

    // 600 dp and wider (tablets, unfolded foldables): the bar becomes a rail on the start edge and the content stays one
    // 640 dp column in the middle. Phones are unchanged.
    val wide = LocalConfiguration.current.screenWidthDp >= 600
    val itemColors = NavigationBarItemDefaults.colors(
        selectedIconColor = c.teal,
        selectedTextColor = c.textPrimary,
        indicatorColor = c.tealTrack,
        unselectedIconColor = c.textSecondary,
        unselectedTextColor = c.textSecondary,
    )

    CompositionLocalProvider(LocalSnackbar provides snackbar) {
        Row(Modifier.fillMaxSize()) {
        if (wide && showBar) {
            NavigationRail(containerColor = c.card) {
                tabs.forEach { tab ->
                    val selected = route == tab.route
                    NavigationRailItem(
                        selected = selected,
                        onClick = { nav.goToTab(tab.route) },
                        icon = { Icon(if (selected) tab.selectedIcon else tab.icon, contentDescription = null) },
                        label = { NavLabel(stringResource(tab.label)) },
                        colors = NavigationRailItemDefaults.colors(
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
        Scaffold(
            modifier = Modifier.weight(1f),
            containerColor = c.background,
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = {
                if (showBar && !wide) {
                    // Container is the theme's surfaceContainer, which is our card.
                    NavigationBar(tonalElevation = 0.dp) {
                        tabs.forEach { tab ->
                            val selected = route == tab.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = { nav.goToTab(tab.route) },
                                icon = { Icon(if (selected) tab.selectedIcon else tab.icon, contentDescription = null) },
                                label = { NavLabel(stringResource(tab.label)) },
                                colors = itemColors,
                            )
                        }
                    }
                }
            },
        ) { padding ->
          Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            NavHost(
                navController = nav,
                startDestination = Routes.HOME,
                // The bar's space counts as already used, so screens that pad for the keyboard
                // (Coach input) sit right on top of it instead of a bar-height above.
                modifier = (if (showBar) PaddingValues(bottom = padding.calculateBottomPadding()) else PaddingValues(0.dp)).let { barSpace ->
                    Modifier.padding(barSpace).consumeWindowInsets(barSpace)
                }.let { if (wide) it.widthIn(max = 640.dp).fillMaxHeight() else it },
                // Pushed detail screens slide in from the end edge (RTL-aware) and slide back out; tabs just fade.
                // With reduced motion on, nothing slides: every change is a short fade.
                enterTransition = {
                    if (isDetailRoute(targetState.destination.route) && !reduced) {
                        slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, navSlide()) + fadeIn(navSlide())
                    } else fadeIn(navFade(reduced))
                },
                exitTransition = { fadeOut(navFade(reduced)) },
                popEnterTransition = { fadeIn(navFade(reduced)) },
                popExitTransition = {
                    if (isDetailRoute(initialState.destination.route) && !reduced) {
                        slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, navSlide()) + fadeOut(navSlide())
                    } else fadeOut(navFade(reduced))
                },
            ) {
                composable(Routes.HOME) { HomeScreen(nav) }
                composable(Routes.WORKOUTS) { WorkoutsScreen(nav) }
                composable(Routes.FOOD) { FoodScreen(nav) }
                composable(Routes.COACH) { CoachScreen(nav) }
                composable(Routes.PROFILE) { ProfileScreen(nav) }
                composable(Routes.LIVE, enterTransition = { EnterTransition.None }, exitTransition = { ExitTransition.None }) { LiveWorkoutScreen(nav) }
                composable(Routes.PLAN, arguments = listOf(navArgument("day") { type = NavType.IntType; defaultValue = 0 })) { entry ->
                    PlanEditorScreen(nav, entry.arguments?.getInt("day") ?: 0)
                }
                composable(Routes.SCAN) { ScanScreen(nav) }
                composable(Routes.EXERCISE, arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
                    ExerciseDetailScreen(nav, entry.arguments?.getString("id").orEmpty())
                }
                composable(Routes.HISTORY) { HistoryScreen(nav) }
                composable(Routes.BADGES) { BadgesScreen(nav) }
                composable("readiness/{kind}") { entry ->
                    com.forgequest.app.ui.home.ReadinessDetailScreen(nav, com.forgequest.app.ui.home.ReadinessKind.valueOf(entry.arguments?.getString("kind") ?: "RECOVERY"))
                }
                composable(Routes.TOUR) { com.forgequest.app.ui.onboarding.AppTour(androidx.compose.ui.res.stringResource(R.string.tour_done)) { nav.popBackStack() } }
                composable(Routes.LIBRARY) { ExerciseLibraryScreen(nav) }
                composable(Routes.PROGRESS) { ProgressScreen(nav) }
                composable(Routes.SESSION, arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
                    SessionDetailScreen(nav, entry.arguments?.getLong("id") ?: 0L)
                }
            }
          }
        }
        }
    }
}

/** Detail screens slide on the Emphasized curve, the M3 token for screen-level moves. */
private fun <T> navSlide() = ForgeMotion.timed<T>(false, ForgeMotion.Duration.MEDIUM2, ForgeMotion.Easing.Emphasized)

/** Tab fades keep Compose's default fade, or the short reduced-motion fade. */
private fun <T> navFade(reduced: Boolean): androidx.compose.animation.core.FiniteAnimationSpec<T> =
    if (reduced) ForgeMotion.timed(true, 0, ForgeMotion.Easing.Standard) else androidx.compose.animation.core.spring(stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow)

/** Pushed screens (everything except the five tabs and the live workout, which has no transition). */
private fun isDetailRoute(route: String?): Boolean =
    route != null && route != Routes.LIVE && route != Routes.HOME && route != Routes.WORKOUTS &&
        route != Routes.FOOD && route != Routes.COACH && route != Routes.PROFILE

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

/**
 * A bar or rail label: one line, never split mid-word. This is the one kept text-size cap in the app (1.3x): a bar can't
 * reflow, so its labels stop growing there while everything else follows the person's text size.
 */
@Composable
private fun NavLabel(text: String) {
    val d = androidx.compose.ui.platform.LocalDensity.current
    androidx.compose.runtime.CompositionLocalProvider(
        androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(d.density, minOf(d.fontScale, 1.3f)),
    ) {
        // A long label (Arabic "الملف الشخصي") that still doesn't fit shrinks a step at a time until it does.
        var shrink by androidx.compose.runtime.remember(text, d.fontScale) { androidx.compose.runtime.mutableFloatStateOf(1f) }
        val style = MaterialTheme.typography.labelMedium
        Text(
            text, maxLines = 1, softWrap = false, textAlign = TextAlign.Center,
            style = style.copy(fontSize = style.fontSize * shrink),
            onTextLayout = { if (it.didOverflowWidth && shrink > 0.6f) shrink *= 0.92f },
            modifier = Modifier.padding(horizontal = 6.dp),
        )
    }
}
