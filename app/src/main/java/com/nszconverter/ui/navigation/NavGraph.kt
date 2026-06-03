package com.nszconverter.ui.navigation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.nszconverter.ui.detail.JobDetailScreen
import com.nszconverter.ui.history.HistoryScreen
import com.nszconverter.ui.home.HomeScreen
import com.nszconverter.ui.onboarding.OnboardingScreen
import com.nszconverter.ui.settings.SettingsScreen

@Composable
fun NavGraph(
    navController: NavHostController,
    startDestination: String,
    padding: PaddingValues,
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
    ) {
        composable(Destination.Onboarding.route) {
            OnboardingScreen(
                onFinished = {
                    navController.navigate(Destination.Home.route) {
                        popUpTo(Destination.Onboarding.route) { inclusive = true }
                    }
                },
            )
        }
        composable(Destination.Home.route) {
            HomeScreen(
                padding = padding,
                onJobClicked = { id -> navController.navigate(Destination.JobDetail.build(id)) },
            )
        }
        composable(Destination.History.route) {
            HistoryScreen(
                padding = padding,
                onItemClicked = { id -> navController.navigate(Destination.JobDetail.build(id)) },
            )
        }
        composable(Destination.Settings.route) {
            SettingsScreen(padding = padding)
        }
        composable(Destination.JobDetail.route) { entry ->
            val id = entry.arguments?.getString("id").orEmpty()
            JobDetailScreen(
                jobId = id,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
