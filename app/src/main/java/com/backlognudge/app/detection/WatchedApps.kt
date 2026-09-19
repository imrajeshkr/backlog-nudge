package com.backlognudge.app.detection

/**
 * Central place for which packages count as "scrolling" sessions.
 * Kept as a simple map so more apps can be added later (YouTube, TikTok, ...)
 * without touching the detection/service logic.
 */
object WatchedApps {
    const val INSTAGRAM = "com.instagram.android"

    val FRIENDLY_NAMES: Map<String, String> = mapOf(
        INSTAGRAM to "Instagram"
    )

    val DEFAULT_PACKAGES: Set<String> = setOf(INSTAGRAM)

    fun friendlyName(pkg: String): String = FRIENDLY_NAMES[pkg] ?: pkg
}
