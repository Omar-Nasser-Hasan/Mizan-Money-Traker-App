// Copyright (c) 2026 Mizan contributors
package app.mizan.data.backup

import java.io.File
import java.time.Instant

/** Local diagnostics deliberately exclude exception messages and user-entered values. */
object LocalCrashLog {
    fun sanitized(error: Throwable): String =
        buildString {
            appendLine(Instant.now())
            generateSequence(error) { it.cause }.take(8).forEach { cause ->
                appendLine(cause.javaClass.name)
                cause.stackTrace.take(40).forEach { appendLine("  at $it") }
            }
        }

    fun write(
        directory: File,
        error: Throwable,
    ) {
        directory.mkdirs()
        val destination = File(directory, "crash.log")
        val previous = if (destination.exists()) destination.readText().takeLast(32_000) else ""
        destination.writeText((previous + sanitized(error)).takeLast(64_000))
    }

    fun export(
        directory: File,
        destination: File,
        version: String,
    ): File {
        destination.parentFile?.mkdirs()
        val file = File(directory, "crash.log")
        destination.writeText("Mizan $version\n" + if (file.exists()) file.readText() else "No local crash events.\n")
        return destination
    }
}
