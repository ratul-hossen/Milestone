package com.milestone.app.ui.navigation

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.milestone.app.MilestoneApp
import com.milestone.app.R
import com.milestone.app.ui.UiMessage
import com.milestone.app.ui.history.HistoryScreen
import com.milestone.app.ui.home.HomeScreen
import com.milestone.app.ui.session.EditSessionScreen
import com.milestone.app.ui.session.EditSessionViewModel
import com.milestone.app.ui.session.SessionDetailScreen
import com.milestone.app.ui.stats.StatsScreen
import com.milestone.app.ui.subjects.SubjectsScreen
import com.milestone.app.ui.theme.ErrorContainer
import com.milestone.app.ui.theme.InverseSurface
import com.milestone.app.ui.theme.LabelMedium
import com.milestone.app.ui.theme.OnErrorContainer
import com.milestone.app.ui.theme.OnSurface
import com.milestone.app.ui.theme.OnSurfaceVariant
import com.milestone.app.ui.theme.OutlineVariant
import com.milestone.app.ui.theme.PrimaryContainer
import com.milestone.app.ui.theme.Surface
import com.milestone.app.ui.timer.TimerScreen
import com.milestone.app.util.AppLog
import kotlinx.coroutines.launch

object Routes {
    const val HOME = "home"
    const val HISTORY = "history"
    const val SUBJECTS = "subjects"
    const val STATS = "stats"
    const val SESSION = "session/{${EditSessionViewModel.ARG_SESSION}}"
    const val EDIT_SESSION = "edit?${EditSessionViewModel.ARG_SESSION}={${EditSessionViewModel.ARG_SESSION}}&${EditSessionViewModel.ARG_SUBJECT}={${EditSessionViewModel.ARG_SUBJECT}}"
    const val TIMER = "timer?${EditSessionViewModel.ARG_SUBJECT}={${EditSessionViewModel.ARG_SUBJECT}}"

    fun session(id: Long) = "session/$id"
    fun edit(sessionId: Long? = null, subjectId: Long? = null) =
        "edit?${EditSessionViewModel.ARG_SESSION}=${sessionId ?: -1}&${EditSessionViewModel.ARG_SUBJECT}=${subjectId ?: -1}"
    fun timer(subjectId: Long? = null) = "timer?${EditSessionViewModel.ARG_SUBJECT}=${subjectId ?: -1}"
    const val SUBJECT = "subject/{${EditSessionViewModel.ARG_SUBJECT}}"
    fun subject(id: Long) = "subject/$id"
}

private data class Tab(val route: String, val label: String, @DrawableRes val icon: Int)

private val Tabs = listOf(
    Tab(Routes.HOME, "Home", R.drawable.ic_home),
    Tab(Routes.HISTORY, "History", R.drawable.ic_history),
    Tab(Routes.SUBJECTS, "Subjects", R.drawable.ic_book),
    Tab(Routes.STATS, "Stats", R.drawable.ic_chart),
)

@Composable
fun MilestoneNavHost(openTimer: Boolean, onTimerOpened: () -> Unit) {
    val nav = rememberNavController()
    val app = LocalContext.current.applicationContext as MilestoneApp
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var currentIsError by remember { mutableStateOf(false) }
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val showBar = Tabs.any { it.route == route }

    LaunchedEffect(Unit) {
        app.messages.messages.collect { msg: UiMessage ->
            currentIsError = msg.isError
            snackbar.currentSnackbarData?.dismiss()
            scope.launch {
                // Long, not Indefinite: an Undo must not stay tappable for hours.
                val result = snackbar.showSnackbar(msg.text, msg.actionLabel, withDismissAction = msg.actionLabel != null,
                    duration = SnackbarDuration.Long)
                if (result == SnackbarResult.ActionPerformed) {
                    AppLog.d(AppLog.UI, "Snackbar action '${msg.actionLabel}' tapped")
                    msg.onAction()
                }
            }
        }
    }
    LaunchedEffect(openTimer) {
        if (openTimer) { nav.navigate(Routes.timer()) { launchSingleTop = true }; onTimerOpened() }
    }
    LaunchedEffect(route) { route?.let { AppLog.d(AppLog.UI, "Screen: $it") } }

    Scaffold(
        containerColor = Surface,
        snackbarHost = {
            SnackbarHost(snackbar) { data ->
                // Errors use the Figma "validation" snackbar (error container); everything else is the dark one.
                Snackbar(
                    data,
                    shape = RoundedCornerShape(8.dp),
                    containerColor = if (currentIsError) ErrorContainer else InverseSurface,
                    contentColor = if (currentIsError) OnErrorContainer else Color.White,
                    actionColor = PrimaryContainer,
                    dismissActionContentColor = if (currentIsError) OnErrorContainer else Color.White,
                )
            }
        },
        bottomBar = { if (showBar) BottomBar(route) { tab -> nav.navigateToTab(tab) } },
    ) { padding ->
        NavHost(nav, startDestination = Routes.HOME, modifier = Modifier.fillMaxSize().padding(padding)) {
            composable(Routes.HOME) {
                HomeScreen(
                    onLogSession = { nav.navigate(Routes.edit(subjectId = it)) },
                    onOpenTimer = { nav.navigate(Routes.timer(it)) },
                    onSeeAllSubjects = { nav.navigateToTab(Routes.SUBJECTS) },
                    onOpenSubject = { nav.navigate(Routes.subject(it)) },
                )
            }
            composable(Routes.HISTORY) {
                HistoryScreen(onOpenSession = { nav.navigate(Routes.session(it)) }, onLogSession = { nav.navigate(Routes.edit(subjectId = it)) })
            }
            composable(Routes.SUBJECTS) {
                SubjectsScreen(onOpenSubject = { nav.navigate(Routes.subject(it)) })
            }
            composable(
                Routes.SUBJECT,
                arguments = listOf(navArgument(EditSessionViewModel.ARG_SUBJECT) { type = NavType.LongType }),
            ) {
                // A subject's page = its full history, reusing the History screen with a fixed filter.
                HistoryScreen(
                    onOpenSession = { nav.navigate(Routes.session(it)) },
                    onLogSession = { nav.navigate(Routes.edit(subjectId = it)) },
                    onBack = { nav.popBackStack() },
                )
            }
            composable(Routes.STATS) { StatsScreen(onLogSession = { nav.navigate(Routes.edit()) }) }
            composable(
                Routes.SESSION,
                arguments = listOf(navArgument(EditSessionViewModel.ARG_SESSION) { type = NavType.LongType }),
            ) {
                SessionDetailScreen(onBack = { nav.popBackStack() }, onEdit = { nav.navigate(Routes.edit(sessionId = it)) })
            }
            composable(
                Routes.EDIT_SESSION,
                arguments = listOf(
                    navArgument(EditSessionViewModel.ARG_SESSION) { type = NavType.LongType; defaultValue = -1L },
                    navArgument(EditSessionViewModel.ARG_SUBJECT) { type = NavType.LongType; defaultValue = -1L },
                ),
            ) {
                EditSessionScreen(
                    onDone = { nav.popBackStack() },
                    onOpenTimer = { id -> nav.navigate(Routes.timer(id)) { popUpTo(Routes.EDIT_SESSION) { inclusive = true } } },
                    onAddSubject = { nav.navigateToTab(Routes.SUBJECTS) },
                )
            }
            composable(
                Routes.TIMER,
                arguments = listOf(navArgument(EditSessionViewModel.ARG_SUBJECT) { type = NavType.LongType; defaultValue = -1L }),
            ) { entry ->
                val subject = entry.arguments?.getLong(EditSessionViewModel.ARG_SUBJECT)?.takeIf { it > 0 }
                TimerScreen(
                    subject,
                    onBack = { nav.popBackStack() },
                    onAddSubject = { nav.navigateToTab(Routes.SUBJECTS) },
                    // After saving, open the saved session so the student can add notes (Cancel keeps it as is).
                    onSaved = { id -> nav.navigate(Routes.edit(sessionId = id)) { popUpTo(Routes.TIMER) { inclusive = true } } },
                )
            }
        }
    }
}

private fun NavHostController.navigateToTab(route: String) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}

/** Figma "Navigation Bar": 80dp, top border, 64×32 pill indicator on the active tab. */
@Composable
private fun BottomBar(current: String?, onSelect: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().background(Color.White).navigationBarsPadding()) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(OutlineVariant))
        Row(Modifier.fillMaxWidth().height(80.dp).padding(start = 8.dp, end = 8.dp, top = 12.dp, bottom = 16.dp)) {
            Tabs.forEach { tab ->
                val selected = tab.route == current
                Column(
                    Modifier.weight(1f).selectable(selected, role = Role.Tab) { onSelect(tab.route) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Box(
                        Modifier.width(64.dp).height(32.dp).clip(RoundedCornerShape(16.dp))
                            .background(if (selected) PrimaryContainer else Color.Transparent),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(painterResource(tab.icon), contentDescription = null,
                            tint = if (selected) com.milestone.app.ui.theme.OnPrimaryContainer else OnSurfaceVariant,
                            modifier = Modifier.size(24.dp))
                    }
                    // Bold label on the active tab: the pill alone is a weak cue (#FFE2E2 on white is 1.22:1).
                    Text(tab.label, style = if (selected) LabelMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold) else LabelMedium,
                        color = if (selected) OnSurface else OnSurfaceVariant)
                }
            }
        }
    }
}
