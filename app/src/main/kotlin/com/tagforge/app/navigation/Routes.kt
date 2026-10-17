package com.tagforge.app.navigation

import kotlinx.serialization.Serializable

/**
 * Type-safe navigation destinations. Later phases add Tasks and their detail routes here.
 * Nested under [Route] so a destination can never clash with the container composable of the same feature.
 */
sealed interface Route {
    @Serializable
    data object Read : Route

    /** [templateId] opens the wizard prefilled from a saved template. */
    @Serializable
    data class Write(val templateId: Long? = null) : Route

    /** Safe tools hub: erase, format, memory dump, clone and technology info. */
    @Serializable
    data object Tools : Route

    @Serializable
    data object Dump : Route

    @Serializable
    data object Clone : Route

    @Serializable
    data object TechInfo : Route

    @Serializable
    data object More : Route

    @Serializable
    data object History : Route

    @Serializable
    data object Templates : Route

    /** A stored history entry rendered like a fresh read. */
    @Serializable
    data class TagDetail(val historyId: Long) : Route

    @Serializable
    data object Settings : Route

    @Serializable
    data object About : Route
}
