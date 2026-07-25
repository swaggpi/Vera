package app.vera.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material.icons.outlined.Construction
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.vera.feature.briefing.BriefingScreen
import app.vera.feature.sources.SourcesScreen
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
                BriefingScreen(onOpenSources = { navController.navigate("sources") })
            }
            composable("sources") {
                SourcesScreen(onBack = { navController.popBackStack() })
            }
            composable(Dest.Train.route) {
                ComingSoon(
                    "Fake-news training",
                    "Daily SIFT coaching and prebunking micro-games, with spaced repetition so the techniques stick."
                )
            }
            composable(Dest.Verify.route) {
                ComingSoon(
                    "Check what you heard",
                    "Speak or type a claim; Vera researches it on-device with live sources and coaches you through verifying it."
                )
            }
            composable(Dest.Insights.route) {
                ComingSoon(
                    "Your news diet",
                    "See how varied your sources are across countries and ownership — and get nudged out of echo chambers."
                )
            }
        }
    }
}

@Composable
private fun ComingSoon(title: String, blurb: String) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
    ) {
        Icon(Icons.Outlined.Construction, contentDescription = null, tint = Amber)
        Text(title, color = MaterialTheme.colorScheme.onBackground, fontSize = 20.sp,
            textAlign = TextAlign.Center, modifier = Modifier.padding(top = 12.dp))
        Text(blurb, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.5.sp,
            textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
        Text("Next milestone", color = Amber, fontSize = 11.sp, modifier = Modifier.padding(top = 12.dp))
    }
}
