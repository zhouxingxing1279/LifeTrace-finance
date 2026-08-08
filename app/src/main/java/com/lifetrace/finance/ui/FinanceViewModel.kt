package com.lifetrace.finance.ui

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lifetrace.finance.AppGraph
import com.lifetrace.finance.core.MoneyParser
import com.lifetrace.finance.core.TransactionType
import com.lifetrace.finance.data.*
import com.lifetrace.finance.sync.SyncScheduler
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate

data class UiMessage(val text: String, val error: Boolean = false)

@OptIn(ExperimentalCoroutinesApi::class)
class FinanceViewModel(application: Application) : AndroidViewModel(application) {
    private val graph = AppGraph.get(application)
    private val repo = graph.finance
    private val _profile = MutableStateFlow<LocalProfileEntity?>(null)
    val profile = _profile.asStateFlow()
    private val _message = MutableStateFlow<UiMessage?>(null)
    val message = _message.asStateFlow()
    private val _authenticated = MutableStateFlow(graph.auth.currentUserId != null)
    val authenticated = _authenticated.asStateFlow()

    val transactions: StateFlow<List<TransactionEntity>> = _profile.filterNotNull()
        .flatMapLatest { repo.transactions(it.id) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val inbox: StateFlow<List<TransactionEntity>> = _profile.filterNotNull()
        .flatMapLatest { repo.inbox(it.id) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val accounts: StateFlow<List<AccountEntity>> = _profile.filterNotNull()
        .flatMapLatest { repo.accounts(it.id) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val categories: StateFlow<List<CategoryEntity>> = _profile.filterNotNull()
        .flatMapLatest { repo.categories(it.id) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val pendingSync = graph.db.syncDao().pendingCount().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
    val conflicts = graph.db.syncDao().conflicts().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val conflictCount = graph.db.syncDao().conflictCount().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
    val syncState = graph.db.syncDao().stateFlow().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val diagnostics = graph.db.diagnosticDao().recent(200).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val lastNotificationCapture = graph.db.notificationDao().latestCapture().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        viewModelScope.launch {
            val local = repo.ensureProfile()
            _profile.value = local
            _authenticated.value = graph.auth.restore()
            if (_authenticated.value) {
                graph.auth.currentUserId?.let { _profile.value = repo.activateCloudProfile(it) }
                SyncScheduler.scheduleNow(application)
            }
        }
    }

    fun save(type: TransactionType, amountText: String, accountId: String?, toAccountId: String? = null, categoryId: String? = null, merchant: String? = null, note: String? = null) {
        val amount = MoneyParser.parseCents(amountText)
        if (amount == null || amount <= 0) { _message.value = UiMessage("请输入有效金额", true); return }
        val profileId = _profile.value?.id ?: return
        viewModelScope.launch {
            runCatching { repo.createTransaction(profileId, type, amount, accountId, toAccountId, categoryId, merchant, note) }
                .onSuccess { _message.value = UiMessage("已保存，本地立即生效"); SyncScheduler.scheduleNow(getApplication()) }
                .onFailure { _message.value = UiMessage(it.message ?: "保存失败", true) }
        }
    }

    fun updateTransaction(id: String, amountText: String, categoryId: String?, merchant: String?, note: String?) {
        val cents = MoneyParser.parseCents(amountText)
        if (cents == null || cents <= 0) { _message.value = UiMessage("请输入有效金额", true); return }
        viewModelScope.launch {
            runCatching { repo.updateTransaction(id, cents, categoryId, merchant, note) }
                .onSuccess { _message.value = UiMessage("账单已更新"); SyncScheduler.scheduleNow(getApplication()) }
                .onFailure { _message.value = UiMessage(it.message ?: "更新失败", true) }
        }
    }

    fun deleteTransaction(id: String) = viewModelScope.launch {
        runCatching { repo.deleteTransaction(id) }
            .onSuccess { _message.value = UiMessage("账单已删除，可通过同步墓碑传播"); SyncScheduler.scheduleNow(getApplication()) }
            .onFailure { _message.value = UiMessage(it.message ?: "删除失败", true) }
    }

    fun confirm(id: String, categoryId: String? = null) = viewModelScope.launch { repo.confirmCandidate(id, categoryId); SyncScheduler.scheduleNow(getApplication()) }
    fun ignore(id: String) = viewModelScope.launch { repo.ignoreCandidate(id); SyncScheduler.scheduleNow(getApplication()) }

    fun addAccount(name: String, type: String) = viewModelScope.launch {
        _profile.value?.let { repo.createAccount(it.id, name, type); SyncScheduler.scheduleNow(getApplication()) }
    }

    fun archiveAccount(id: String) {
        if (accounts.value.size <= 1) {
            _message.value = UiMessage("至少保留一个可用账户", true)
            return
        }
        viewModelScope.launch {
            runCatching { repo.archiveAccount(id) }
                .onSuccess { _message.value = UiMessage("账户已归档，历史账单仍保留"); SyncScheduler.scheduleNow(getApplication()) }
                .onFailure { _message.value = UiMessage(it.message ?: "账户归档失败", true) }
        }
    }

    fun addCategory(name: String, type: TransactionType) = viewModelScope.launch {
        _profile.value?.let { repo.createCategory(it.id, name, type); SyncScheduler.scheduleNow(getApplication()) }
    }

    fun archiveCategory(id: String) = viewModelScope.launch {
        runCatching { repo.archiveCategory(id) }
            .onSuccess { _message.value = UiMessage("分类已归档，历史账单仍保留"); SyncScheduler.scheduleNow(getApplication()) }
            .onFailure { _message.value = UiMessage(it.message ?: "分类归档失败", true) }
    }

    fun login(email: String, password: String) = viewModelScope.launch {
        runCatching {
            graph.auth.login(email, password)
            val cloudUserId = requireNotNull(graph.auth.currentUserId)
            repo.activateCloudProfile(cloudUserId)
        }.onSuccess { profile ->
            _profile.value = profile
            _authenticated.value = true
            _message.value = UiMessage("登录成功")
            SyncScheduler.scheduleNow(getApplication())
        }.onFailure { _message.value = UiMessage("登录失败：${it.message}", true) }
    }

    fun logout() = viewModelScope.launch {
        graph.auth.logout()
        SyncScheduler.cancelNow(getApplication())
        _authenticated.value = false
        _message.value = UiMessage("已退出，当前本地数据保留")
    }

    fun resolveKeepLocal(id: String) = viewModelScope.launch {
        graph.syncEngine.resolveConflictKeepLocal(id)
            .onSuccess { _message.value = UiMessage("已选择保留本地版本，将重新同步"); SyncScheduler.scheduleNow(getApplication()) }
            .onFailure { _message.value = UiMessage("冲突处理失败：${it.message}", true) }
    }

    fun resolveUseRemote(id: String) = viewModelScope.launch {
        graph.syncEngine.resolveConflictUseRemote(id)
            .onSuccess { _message.value = UiMessage("已采用云端版本") }
            .onFailure { _message.value = UiMessage("冲突处理失败：${it.message}", true) }
    }

    fun syncNow() = SyncScheduler.scheduleNow(getApplication())
    fun snapshot() = viewModelScope.launch { graph.syncEngine.snapshot().onFailure { _message.value = UiMessage("Snapshot 失败：${it.message}", true) } }
    fun setBaseUrl(value: String) {
        runCatching { graph.settings.baseUrl = value }
            .onSuccess { _message.value = UiMessage("服务器地址已保存") }
            .onFailure { _message.value = UiMessage(it.message ?: "服务器地址无效", true) }
    }
    fun baseUrl(): String = graph.settings.baseUrl

    fun clearNotificationCache() = viewModelScope.launch {
        graph.db.notificationDao().clearAll()
        _message.value = UiMessage("通知证据缓存已清空；已创建的候选账单不会被删除")
    }

    fun shareDiagnostics() {
        val body = diagnostics.value.joinToString("\n") { event -> "${event.timestamp} ${event.level} ${event.component}/${event.eventCode} ${event.message}" }
            .ifBlank { "No diagnostic events." }
        val intent = Intent(Intent.ACTION_SEND).setType("text/plain")
            .putExtra(Intent.EXTRA_SUBJECT, "LifeTrace Finance diagnostics")
            .putExtra(Intent.EXTRA_TEXT, body).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        getApplication<Application>().startActivity(Intent.createChooser(intent, "导出脱敏诊断日志").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun monthRange(): Pair<LocalDate, LocalDate> {
        val now = LocalDate.now(); return now.withDayOfMonth(1) to now.withDayOfMonth(now.lengthOfMonth())
    }
}
