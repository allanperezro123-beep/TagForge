package com.tagforge.app.navigation

import kotlinx.serialization.Serializable

/**
 * Type-safe navigation destinations. Later phases add Write, Tools, Tasks and their detail routes here.
 * Nested under [Route] so a destination can never clash with the container composable of the same feature.
 */
sealed interface Route {
    @Serializable
    data object Read : Route

    @Serializable
    data object More : Route

    @Serializable
    data object Settings : Route

    @Serializable
    data object About : Route
}
