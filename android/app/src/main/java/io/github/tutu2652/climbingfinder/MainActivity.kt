package io.github.tutu2652.climbingfinder

import android.os.Bundle
import android.view.KeyEvent
import io.github.tutu2652.climbingfinder.ui.VolumeUpKey
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.CurrencyPound
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import io.github.tutu2652.climbingfinder.ui.CheapestScreen
import io.github.tutu2652.climbingfinder.ui.PricesForYouScreen
import io.github.tutu2652.climbingfinder.ui.ClassesScreen
import io.github.tutu2652.climbingfinder.ui.ClimbScreen
import io.github.tutu2652.climbingfinder.ui.ClimbingTheme
import io.github.tutu2652.climbingfinder.ui.ColourScaleScreen
import io.github.tutu2652.climbingfinder.ui.DealsScreen
import io.github.tutu2652.climbingfinder.ui.EventsScreen
import io.github.tutu2652.climbingfinder.ui.ExportScreen
import io.github.tutu2652.climbingfinder.ui.GalleryScreen
import io.github.tutu2652.climbingfinder.ui.GymDetailScreen
import io.github.tutu2652.climbingfinder.ui.LocalMediaViewer
import io.github.tutu2652.climbingfinder.ui.MediaViewer
import io.github.tutu2652.climbingfinder.ui.MediaViewerHost
import io.github.tutu2652.climbingfinder.ui.GymVisitsScreen
import io.github.tutu2652.climbingfinder.ui.GymsScreen
import io.github.tutu2652.climbingfinder.ui.LocalModel
import io.github.tutu2652.climbingfinder.ui.LocalNav
import io.github.tutu2652.climbingfinder.ui.LogScreen
import io.github.tutu2652.climbingfinder.ui.LogbookBackupScreen
import io.github.tutu2652.climbingfinder.ui.MapScreen
import io.github.tutu2652.climbingfinder.ui.MembershipsScreen
import io.github.tutu2652.climbingfinder.ui.MoreScreen
import io.github.tutu2652.climbingfinder.ui.NearbyScreen
import io.github.tutu2652.climbingfinder.ui.OriginScreen
import io.github.tutu2652.climbingfinder.ui.SessionLogScreen
import io.github.tutu2652.climbingfinder.ui.SessionScreen
import io.github.tutu2652.climbingfinder.ui.UpdateBanner
import io.github.tutu2652.climbingfinder.ui.StartSessionScreen
import io.github.tutu2652.climbingfinder.ui.StatsScreen
import io.github.tutu2652.climbingfinder.ui.VisitsByGymScreen
import kotlinx.coroutines.delay

private data class Tab(val route: String, val title: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("cheapest", "Cheapest", Icons.Filled.CurrencyPound),
    Tab("map", "Map", Icons.Filled.Map),
    Tab("gyms", "Gyms", Icons.Filled.Terrain),
    Tab("log", "Log", Icons.AutoMirrored.Filled.List),
    Tab("more", "More", Icons.Filled.MoreHoriz),
)

class MainActivity : ComponentActivity() {
    private val model: AppModel by viewModels()

    /** Volume up turns on a muted video's sound; the key still changes the volume as usual. */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_VOLUME_UP && event.action == KeyEvent.ACTION_DOWN) VolumeUpKey.onPress?.invoke()
        return super.dispatchKeyEvent(event)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        model.refreshBankHolidays()
        setContent {
            ClimbingTheme(isSystemInDarkTheme()) {
                val nav = rememberNavController()
                var tab by rememberSaveable { mutableStateOf("cheapest") }
                LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { model.becameActive() }
                LaunchedEffect(Unit) { while (true) { delay(60_000); model.followClock() } }
                // `adb shell am start -n …/.MainActivity -e tab map` opens a tab directly (used for screenshots).
                LaunchedEffect(Unit) {
                    val start = intent.getStringExtra("tab")
                    if (start != null && start != tab && tabs.any { it.route == start }) {
                        tab = start
                        nav.navigate(start) { popUpTo(nav.graph.findStartDestination().id) { saveState = true }; launchSingleTop = true }
                    }
                }
                val viewer = remember { MediaViewerHost() }
                CompositionLocalProvider(LocalModel provides model, LocalNav provides nav, LocalMediaViewer provides viewer) {
                    Box {
                        Scaffold(bottomBar = {
                            Column {
                            UpdateBanner()
                            NavigationBar {
                                for (t in tabs) {
                                    NavigationBarItem(
                                        selected = tab == t.route,
                                        onClick = {
                                            tab = t.route
                                            nav.navigate(t.route) {
                                                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        },
                                        icon = { Icon(t.icon, null) },
                                        label = { Text(t.title) },
                                    )
                                }
                            }
                            }
                        }) { padding ->
                            NavHost(nav, startDestination = "cheapest", modifier = Modifier.padding(bottom = padding.calculateBottomPadding())) {
                                composable("cheapest") { CheapestScreen() }
                                composable("map") { MapScreen() }
                                composable("gyms") { GymsScreen() }
                                composable("log") { LogScreen() }
                                composable("more") { MoreScreen() }
                                composable("gym/{id}") { GymDetailScreen(it.arguments?.getString("id") ?: "") }
                                composable("nearby") { NearbyScreen() }
                                composable("origin") { OriginScreen() }
                                composable("pricesForYou") { PricesForYouScreen() }
                                composable("deals") { DealsScreen() }
                                composable("memberships") { MembershipsScreen() }
                                composable("classes") { ClassesScreen() }
                                composable("events") { EventsScreen() }
                                composable("backup") { LogbookBackupScreen() }
                                composable("export") { ExportScreen() }
                                composable("stats") { StatsScreen() }
                                composable("visits") { VisitsByGymScreen() }
                                composable("gymVisits/{id}") { GymVisitsScreen(it.arguments?.getString("id") ?: "") }
                                composable("colours/{id}") { ColourScaleScreen(it.arguments?.getString("id") ?: "") }
                                composable(
                                    "start?past={past}&gym={gym}",
                                    arguments = listOf(
                                        navArgument("past") { type = NavType.BoolType; defaultValue = false },
                                        navArgument("gym") { type = NavType.StringType; nullable = true; defaultValue = null },
                                    ),
                                ) { StartSessionScreen(it.arguments?.getBoolean("past") ?: false, it.arguments?.getString("gym")) }
                                composable("session/{id}") { SessionScreen(it.arguments?.getString("id") ?: "") }
                                composable("session/{id}/log") { SessionLogScreen(it.arguments?.getString("id") ?: "") }
                                composable("media") { GalleryScreen() }
                                composable(
                                    "climb/{session}/{climb}?play={play}&sound={sound}",
                                    arguments = listOf(
                                        navArgument("play") { type = NavType.BoolType; defaultValue = false },
                                        navArgument("sound") { type = NavType.BoolType; defaultValue = false },
                                    ),
                                ) {
                                    ClimbScreen(it.arguments?.getString("session") ?: "", it.arguments?.getString("climb") ?: "",
                                        playVideo = it.arguments?.getBoolean("play") ?: false, withSound = it.arguments?.getBoolean("sound") ?: false)
                                }
                            }
                        }
                        // A route photo or video full screen, over everything.
                        viewer.shown?.let { MediaViewer(it) { viewer.shown = null } }
                    }
                }
            }
        }
    }
}
