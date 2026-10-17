// Copyright (c) 2026 Mizan contributors
package app.mizan.sms

import app.mizan.data.repository.MizanRepository
import app.mizan.domain.entry.SmsParser
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InboxCapture @Inject constructor(
    private val repository: MizanRepository,
) {
    suspend fun capture(
        source: String,
        sender: String,
        message: String,
        occurredAt: Instant,
    ) {
        SmsParser.parse(sender, message, occurredAt)?.let { repository.captureInboxDraft(source, it) }
    }
}
