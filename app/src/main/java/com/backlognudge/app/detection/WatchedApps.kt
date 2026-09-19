package com.backlognudge.app.detection

import android.content.Context

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

    fun isInstalled(context: Context, pkg: String): Boolean =
        runCatching { context.packageManager.getApplicationInfo(pkg, 0) }.isSuccess

    /** True if none of the given packages are installed - watching would silently do nothing. */
    fun noneInstalled(context: Context, packages: Set<String>): Boolean =
        packages.isNotEmpty() && packages.none { isInstalled(context, it) }
}
