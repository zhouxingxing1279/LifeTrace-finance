package com.lifetrace.finance.domain

import android.content.Context

/** Device-local UI preference. Ledger data itself remains synchronized through finance.ledger. */
class LedgerSelectionStore(context: Context) {
    private val prefs = context.getSharedPreferences("finance_ledger_selection", Context.MODE_PRIVATE)

    fun selectedLedgerId(profileId: String): String? =
        prefs.getString("ledger:$profileId", null)?.takeIf(String::isNotBlank)

    fun select(profileId: String, ledgerId: String) {
        require(ledgerId.isNotBlank())
        prefs.edit().putString("ledger:$profileId", ledgerId).apply()
    }

    fun clear(profileId: String) {
        prefs.edit().remove("ledger:$profileId").apply()
    }
}
