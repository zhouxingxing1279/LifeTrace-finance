package com.lifetrace.finance.domain

enum class BudgetAlertLevel { NONE, NEAR_LIMIT, EXCEEDED }

object BudgetAlertPolicy {
    fun level(usedCents: Long, limitCents: Long): BudgetAlertLevel = when {
        limitCents <= 0 -> BudgetAlertLevel.NONE
        usedCents >= limitCents -> BudgetAlertLevel.EXCEEDED
        usedCents * 100 >= limitCents * 80 -> BudgetAlertLevel.NEAR_LIMIT
        else -> BudgetAlertLevel.NONE
    }
}
