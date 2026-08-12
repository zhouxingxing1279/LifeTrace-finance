package com.lifetrace.finance.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lifetrace.finance.AppGraph
import com.lifetrace.finance.core.MoneyParser
import com.lifetrace.finance.core.TransactionType
import com.lifetrace.finance.data.*
import com.lifetrace.finance.sync.RecurringScheduler
import com.lifetrace.finance.sync.SyncScheduler
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class BookkeepingManagementViewModel(application: Application) : AndroidViewModel(application) {
    private val graph = AppGraph.get(application)
    private val repo = graph.finance
    private val manager = graph.bookkeeping

    private val _profile = MutableStateFlow<LocalProfileEntity?>(null)
    val profile = _profile.asStateFlow()
    private val _selectedLedgerId = MutableStateFlow<String?>(null)
    val selectedLedgerId = _selectedLedgerId.asStateFlow()
    private val _message = MutableStateFlow<UiMessage?>(null)
    val message = _message.asStateFlow()

    fun dismissMessage(message: UiMessage) {
        _message.compareAndSet(message, null)
    }

    val ledgers: StateFlow<List<LedgerEntity>> = _profile.filterNotNull()
        .flatMapLatest { manager.ledgers(it.id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val accounts: StateFlow<List<AccountEntity>> = selectionFlow { profileId, ledgerId -> manager.accounts(profileId, ledgerId) }
    val categories: StateFlow<List<CategoryEntity>> = selectionFlow { profileId, ledgerId -> manager.categories(profileId, ledgerId) }
    val transactions: StateFlow<List<TransactionEntity>> = selectionFlow { profileId, ledgerId -> manager.transactions(profileId, ledgerId) }
    val tags: StateFlow<List<TagEntity>> = selectionFlow { profileId, ledgerId -> manager.tags(profileId, ledgerId) }
    val budgets: StateFlow<List<BudgetEntity>> = selectionFlow { profileId, ledgerId -> manager.budgets(profileId, ledgerId) }
    val recurring: StateFlow<List<RecurringTransactionEntity>> = selectionFlow { profileId, ledgerId -> manager.recurring(profileId, ledgerId) }

    init {
        viewModelScope.launch {
            val profile = repo.ensureProfile()
            _profile.value = profile
            val default = repo.ensureDefaultLedger(profile.id)
            val preferred = graph.ledgerSelection.selectedLedgerId(profile.id)
            _selectedLedgerId.value = preferred ?: default.id
        }
        viewModelScope.launch {
            combine(_profile.filterNotNull(), ledgers) { profile, rows -> profile to rows }.collect { (profile, rows) ->
                if (rows.isEmpty()) return@collect
                val current = _selectedLedgerId.value
                if (current == null || rows.none { it.id == current }) {
                    selectLedger(rows.first().id, profile.id)
                }
            }
        }
    }

    private fun <T> selectionFlow(block: (String, String) -> Flow<List<T>>): StateFlow<List<T>> =
        combine(_profile, _selectedLedgerId) { profile, ledgerId -> profile?.id to ledgerId }
            .flatMapLatest { (profileId, ledgerId) ->
                if (profileId == null || ledgerId == null) flowOf(emptyList()) else block(profileId, ledgerId)
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun selectLedger(id: String) {
        val profileId = _profile.value?.id ?: return
        selectLedger(id, profileId)
    }

    private fun selectLedger(id: String, profileId: String) {
        _selectedLedgerId.value = id
        graph.ledgerSelection.select(profileId, id)
    }

    fun createLedger(name: String, currency: String, monthStartDay: Int) = mutate("账本已创建") {
        val profile = requireNotNull(_profile.value)
        val id = manager.createLedger(profile.id, name, currency, monthStartDay)
        selectLedger(id, profile.id)
    }

    fun archiveCurrentLedger() = mutate("账本已归档") {
        val id = requireNotNull(_selectedLedgerId.value)
        require(ledgers.value.size > 1) { "至少保留一个账本" }
        manager.archiveLedger(id)
        graph.ledgerSelection.clear(requireNotNull(_profile.value).id)
    }

    fun createAccount(
        name: String,
        type: String,
        currency: String,
        openingBalance: String,
        bankName: String,
        last4: String,
        creditLimit: String,
        billingDay: String,
        dueDay: String,
        note: String,
    ) = mutate("账户已创建") {
        manager.createAccount(
            profileId = requireNotNull(_profile.value).id,
            ledgerId = requireNotNull(_selectedLedgerId.value),
            name = name,
            accountType = type,
            currency = currency.uppercase(),
            openingBalanceCents = optionalMoney(openingBalance),
            bankName = bankName.ifBlank { null },
            last4 = last4.ifBlank { null },
            creditLimitCents = optionalMoney(creditLimit),
            billingDay = billingDay.toIntOrNull(),
            paymentDueDay = dueDay.toIntOrNull(),
            note = note.ifBlank { null },
        )
    }

    fun updateAccount(
        account: AccountEntity,
        name: String,
        type: String,
        currency: String,
        openingBalance: String,
        bankName: String,
        last4: String,
        creditLimit: String,
        billingDay: String,
        dueDay: String,
        note: String,
        hidden: Boolean,
    ) = mutate("账户已更新") {
        manager.updateAccount(
            account.id, name, type, currency.uppercase(), optionalMoney(openingBalance),
            bankName.ifBlank { null }, last4.ifBlank { null }, optionalMoney(creditLimit),
            billingDay.toIntOrNull(), dueDay.toIntOrNull(), note.ifBlank { null }, hidden,
        )
    }

    fun createCategory(name: String, type: TransactionType, parentId: String?, icon: String? = null) = mutate("分类已创建") {
        manager.createCategory(
            requireNotNull(_profile.value).id,
            requireNotNull(_selectedLedgerId.value),
            name,
            type,
            parentId,
            icon,
        )
    }

    fun archiveCategory(id: String) = mutate("分类已归档") { manager.archiveCategory(id) }

    fun updateCategory(id: String, name: String, parentId: String?, icon: String?) = mutate("分类已更新") {
        manager.updateCategory(id, name, parentId, icon)
    }

    fun moveCategory(id: String, direction: Int) = mutate("分类顺序已更新") { manager.moveCategory(id, direction) }

    fun createTag(name: String, color: String?) = mutate("标签已创建") {
        manager.createTag(requireNotNull(_profile.value).id, requireNotNull(_selectedLedgerId.value), name, color)
    }

    fun archiveTag(id: String) = mutate("标签已归档") { manager.archiveTag(id) }

    fun updateTag(id: String, name: String, color: String?) = mutate("标签已更新") { manager.updateTag(id, name, color) }

    fun addTag(transactionId: String, tagId: String) = mutate("已添加标签") {
        manager.addTag(requireNotNull(_profile.value).id, transactionId, tagId)
    }

    fun removeTag(transactionId: String, tagId: String) = mutate("已移除标签") { manager.removeTag(transactionId, tagId) }

    fun createBudget(amount: String, categoryId: String?, period: String, startDay: Int) {
        val cents = MoneyParser.parseCents(amount)
        if (cents == null || cents <= 0) { _message.value = UiMessage("预算金额无效", true); return }
        mutate("预算已创建") {
            manager.createBudget(
                requireNotNull(_profile.value).id,
                requireNotNull(_selectedLedgerId.value),
                cents,
                categoryId,
                period,
                startDay,
            )
        }
    }

    fun setBudgetEnabled(id: String, enabled: Boolean) = mutate(if (enabled) "预算已启用" else "预算已停用") {
        manager.setBudgetEnabled(id, enabled)
    }

    fun updateBudget(id: String, amount: String, categoryId: String?, period: String, startDay: Int) {
        val cents = MoneyParser.parseCents(amount)
        if (cents == null || cents <= 0) { _message.value = UiMessage("预算金额无效", true); return }
        mutate("预算已更新") { manager.updateBudget(id, cents, categoryId, period, startDay) }
    }

    fun archiveBudget(id: String) = mutate("预算已删除") { manager.archiveBudget(id) }

    fun createRecurring(
        type: TransactionType,
        amount: String,
        accountId: String?,
        toAccountId: String?,
        categoryId: String?,
        note: String?,
        frequency: String,
        interval: Int,
        startDate: LocalDate,
        dayOfMonth: Int?,
        dayOfWeek: Int?,
        monthOfYear: Int?,
        endDate: LocalDate?,
    ) {
        val cents = MoneyParser.parseCents(amount)
        if (cents == null || cents <= 0) { _message.value = UiMessage("周期金额无效", true); return }
        mutate("周期规则已创建") {
            manager.createRecurring(
                requireNotNull(_profile.value).id,
                requireNotNull(_selectedLedgerId.value),
                type,
                cents,
                accountId,
                toAccountId,
                categoryId,
                note,
                frequency,
                interval,
                startDate,
                dayOfMonth,
                dayOfWeek,
                monthOfYear,
                endDate,
            )
            RecurringScheduler.scheduleNow(getApplication())
        }
    }

    fun setRecurringEnabled(id: String, enabled: Boolean) = mutate(if (enabled) "周期规则已启用" else "周期规则已停用") {
        manager.setRecurringEnabled(id, enabled)
    }

    fun updateRecurring(id: String, form: RecurringFormData) {
        val cents = MoneyParser.parseCents(form.amount)
        if (cents == null || cents <= 0) { _message.value = UiMessage("周期金额无效", true); return }
        mutate("周期规则已更新") {
            manager.updateRecurring(id, form.type, cents, form.accountId, form.toAccountId, form.categoryId, form.note,
                form.frequency, form.interval, form.startDate, form.dayOfMonth, form.dayOfWeek, form.monthOfYear, form.endDate)
        }
    }

    fun archiveRecurring(id: String) = mutate("周期规则已删除") { manager.archiveRecurring(id) }

    fun runRecurringNow() = viewModelScope.launch {
        runCatching { manager.executeDueRecurring(requireNotNull(_profile.value).id) }
            .onSuccess { count ->
                _message.value = UiMessage(if (count == 0) "当前没有到期周期账单" else "已生成 $count 笔周期账单")
                if (count > 0) SyncScheduler.scheduleNow(getApplication())
            }
            .onFailure { _message.value = UiMessage(it.message ?: "周期记账执行失败", true) }
    }

    fun budgetUsage(budget: BudgetEntity): Long {
        val (from, to) = manager.budgetPeriod(budget)
        return transactions.value.asSequence()
            .filter { it.deletedAt == null && it.status == "confirmed" && it.transactionType == "expense" && !it.excludeFromBudget }
            .filter { it.localDate >= from.toString() && it.localDate <= to.toString() }
            .filter { budget.categoryId == null || it.categoryId == budget.categoryId }
            .sumOf { it.nativeAmountCents ?: it.amountCents }
    }

    private fun optionalMoney(value: String): Long? = value.trim().takeIf(String::isNotEmpty)?.let {
        MoneyParser.parseCents(it) ?: error("金额格式无效")
    }

    private fun mutate(success: String, block: suspend () -> Unit) = viewModelScope.launch {
        runCatching { block() }
            .onSuccess { _message.value = UiMessage(success); SyncScheduler.scheduleNow(getApplication()) }
            .onFailure { _message.value = UiMessage(it.message ?: "操作失败", true) }
    }
}

data class RecurringFormData(
    val type: TransactionType, val amount: String, val accountId: String?, val toAccountId: String?,
    val categoryId: String?, val note: String?, val frequency: String, val interval: Int,
    val startDate: LocalDate, val dayOfMonth: Int?, val dayOfWeek: Int?, val monthOfYear: Int?, val endDate: LocalDate?,
)
