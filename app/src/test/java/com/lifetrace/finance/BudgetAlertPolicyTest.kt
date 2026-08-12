package com.lifetrace.finance

import com.lifetrace.finance.domain.BudgetAlertLevel
import com.lifetrace.finance.domain.BudgetAlertPolicy
import org.junit.Assert.assertEquals
import org.junit.Test

class BudgetAlertPolicyTest {
    @Test fun classifiesBudgetThresholds() {
        assertEquals(BudgetAlertLevel.NONE, BudgetAlertPolicy.level(7_999, 10_000))
        assertEquals(BudgetAlertLevel.NEAR_LIMIT, BudgetAlertPolicy.level(8_000, 10_000))
        assertEquals(BudgetAlertLevel.EXCEEDED, BudgetAlertPolicy.level(10_000, 10_000))
        assertEquals(BudgetAlertLevel.NONE, BudgetAlertPolicy.level(100, 0))
    }
}
