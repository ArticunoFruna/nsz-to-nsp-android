package com.nszconverter

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.nszconverter.data.prefs.UserPreferences
import com.nszconverter.ui.navigation.Destination
import com.nszconverter.ui.navigation.NavGraph
import com.nszconverter.ui.theme.NSZConverterTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        splash.setKeepOnScreenCondition { viewModel.isLoading.value }

        setContent {
            val prefs by viewModel.preferences.collectAsStateWithLifecycle(initialValue = UserPreferences())
            NSZConverterTheme(themeMode = prefs.themeMode) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppScaffold(
                        start = if (prefs.onboardingComplete) Destination.Home.route else Destination.Onboarding.route,
                    )
                }
            }
        }
    }
}

@Composable
private fun AppScaffold(start: String) {
    val navController = rememberNavController()
    val entry by navController.currentBackStackEntryAsState()
    val currentRoute = entry?.destination?.route

    val showBottomBar = currentRoute in setOf(
        Destination.Home.route, Destination.History.route, Destination.Settings.route,
    )

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    NavBarItem(navController, currentRoute, Destination.Home, Icons.Filled.Home, R.string.nav_home)
                    NavBarItem(navController, currentRoute, Destination.History, Icons.Filled.History, R.string.nav_history)
                    NavBarItem(navController, currentRoute, Destination.Settings, Icons.Filled.Settings, R.string.nav_settings)
                }
            }
        },
    ) { padding ->
        NavGraph(navController = navController, startDestination = start, padding = padding)
    }
}

@Composable
private fun RowScope.NavBarItem(
    navController: NavHostController,
    currentRoute: String?,
    destination: Destination,
    icon: ImageVector,
    labelRes: Int,
) {
    NavigationBarItem(
        selected = currentRoute == destination.route,
        onClick = {
            navController.navigate(destination.route) {
                popUpTo(Destination.Home.route) { inclusive = false; saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        },
        icon = { Icon(icon, null) },
        label = { Text(stringResource(labelRes)) },
    )
}
