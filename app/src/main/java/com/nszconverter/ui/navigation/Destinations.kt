package com.nszconverter.ui.navigation

sealed class Destination(val route: String) {
    data object Onboarding : Destination("onboarding")
    data object Home : Destination("home")
    data object History : Destination("history")
    data object Settings : Destination("settings")
    data object JobDetail : Destination("job/{id}") {
        fun build(id: String) = "job/$id"
    }
}
