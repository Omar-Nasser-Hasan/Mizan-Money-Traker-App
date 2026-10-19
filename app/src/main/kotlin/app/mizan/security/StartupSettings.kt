// Copyright (c) 2026 Mizan contributors
package app.mizan.security

/** One preferences snapshot prevents first-run completion racing lock enablement. */
data class StartupSettings(
    val completed: Boolean,
    val newInterface: Boolean,
    val lockEnabled: Boolean,
    val timeout: Int,
    val secureScreens: Boolean,
)
