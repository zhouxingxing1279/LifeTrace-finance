package com.lifetrace.finance.data

import java.time.Instant
import java.util.UUID

class Diagnostics(private val dao: DiagnosticDao) {
    suspend fun event(component: String, code: String, message: String, level: String = "INFO", correlationId: String? = null) {
        dao.insert(DiagnosticEventEntity(UUID.randomUUID().toString(), Instant.now().toString(), level, component, code, redact(message), correlationId))
        dao.trim()
    }

    private fun redact(value: String): String = value
        .replace(Regex("(?i)bearer\\s+[A-Za-z0-9._-]+"), "Bearer <redacted>")
        .replace(Regex("lt_(?:at|rt)_[A-Za-z0-9._-]+"), "<redacted-token>")
}
