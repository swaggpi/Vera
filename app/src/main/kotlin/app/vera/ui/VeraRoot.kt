package app.vera.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.vera.feature.briefing.BriefingScreen
import app.vera.feature.insights.InsightsScreen
import app.vera.feature.research.ResearchScreen
import app.vera.feature.sources.SourcesScreen
import app.vera.feature.training.TrainingScreen
import app.vera.ui.theme.Amber

private enum class Dest(val route: String, val label: String, val icon: ImageVector) {
    Briefing("briefing", "Briefing", Icons.AutoMirrored.Filled.Article),
    Train("train", "Train", Icons.Filled.School),
    Verify("verify", "Verify", Icons.Filled.Search),
    Insights("insights", "Insights", Icons.Filled.Insights)
}

@Composable
fun VeraRoot() {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                Dest.entries.forEach { dest ->
                    val selected = currentRoute == dest.route ||
                        (dest == Dest.Briefing && currentRoute == "sources")
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(dest.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(dest.icon, contentDescription = dest.label) },
                        label = { Text(dest.label, fontSize = 11.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Amber,
                            selectedTextColor = Amber,
                            indicatorColor = Amber.copy(alpha = 0.16f)
                        )
                    )
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Dest.Briefing.route,
            modifier = Modifier.padding(padding)
        ) {
            composable(Dest.Briefing.route) {
                BriefingScreen(
                    onOpenSources = { navController.navigate("sources") },
                    onOpenResearch = {
                        navController.navigate(Dest.Verify.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
            composable("sources") {
                SourcesScreen(onBack = { navController.popBackStack() })
            }
            composable(Dest.Train.route) { TrainingScreen() }
            composable(Dest.Verify.route) { ResearchScreen() }
            composable(Dest.Insights.route) { InsightsScreen() }
        }
    }
}
