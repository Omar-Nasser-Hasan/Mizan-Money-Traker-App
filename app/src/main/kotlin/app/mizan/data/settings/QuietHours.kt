// Copyright (c) 2026 Mizan contributors
package app.mizan.data.settings

import java.time.LocalTime

object QuietHours {
    fun muted(
        enabled: Boolean,
        start: LocalTime,
        end: LocalTime,
        now: LocalTime,
    ): Boolean = enabled && start != end && if (start < end) now >= start && now < end else now >= start || now < end
}
