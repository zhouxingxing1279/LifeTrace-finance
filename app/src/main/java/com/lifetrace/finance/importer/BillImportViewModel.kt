package com.lifetrace.finance.importer

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lifetrace.finance.AppGraph
import com.lifetrace.finance.data.AccountEntity
import com.lifetrace.finance.data.LedgerEntity
import com.lifetrace.finance.sync.SyncScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

data class BillImportUiState(
    val loading: Boolean = false,
    val fileName: String? = null,
    val preview: BillImportPreview? = null,
    val error: String? = null,
    val result: BillImportCommitResult? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class BillImportViewModel(application: Application) : AndroidViewModel(application) {
    private val graph = AppGraph.get(application)
    private val repo = graph.finance
    private val _profileId = MutableStateFlow<String?>(null)
    private val _ledgerId = MutableStateFlow<String?>(null)
    val ledgerId = _ledgerId.asStateFlow()
    private val _accountId = MutableStateFlow<String?>(null)
    val accountId = _accountId.asStateFlow()
    private val _state = MutableStateFlow(BillImportUiState())
    val state = _state.asStateFlow()

    val ledgers: StateFlow<List<LedgerEntity>> = _profileId.filterNotNull()
        .flatMapLatest { graph.bookkeeping.ledgers(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val accounts: StateFlow<List<AccountEntity>> = combine(_profileId, _ledgerId) { profile, ledger -> profile to ledger }
        .flatMapLatest { (profile, ledger) ->
            if (profile == null || ledger == null) flowOf(emptyList()) else graph.bookkeeping.accounts(profile, ledger)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            val profile = repo.ensureProfile()
            _profileId.value = profile.id
            val default = repo.ensureDefaultLedger(profile.id)
            val preferred = graph.ledgerSelection.selectedLedgerId(profile.id)
            _ledgerId.value = preferred ?: default.id
        }
        viewModelScope.launch {
            accounts.collect { rows ->
                if (_accountId.value != null && rows.none { it.id == _accountId.value }) _accountId.value = null
            }
        }
    }

    fun selectLedger(id: String) {
        val profile = _profileId.value ?: return
        _ledgerId.value = id
        _accountId.value = null
        graph.ledgerSelection.select(profile, id)
    }

    fun selectAccount(id: String?) { _accountId.value = id }

    fun load(uri: Uri) {
        viewModelScope.launch {
            _state.value = BillImportUiState(loading = true)
            runCatching {
                withContext(Dispatchers.IO) {
                    val resolver = getApplication<Application>().contentResolver
                    val fileName = displayName(uri)
                    val mime = resolver.getType(uri)
                    val bytes = when (uri.scheme) {
                        "file" -> File(requireNotNull(uri.path)).readBytes()
                        else -> resolver.openInputStream(uri)?.use { it.readBytes() } ?: error("无法读取账单文件")
                    }
                    require(bytes.size <= 25 * 1024 * 1024) { "账单文件超过 25 MiB" }
                    if (looksLikeXlsx(fileName, mime, bytes)) XlsxSecurityGuard.validate(bytes)
                    fileName to BillImportParser.parse(fileName, mime, bytes)
                }
            }.onSuccess { (name, preview) ->
                _state.value = BillImportUiState(fileName = name, preview = preview)
            }.onFailure { error ->
                _state.value = BillImportUiState(error = error.message ?: "账单解析失败")
            }
        }
    }

    fun commit() {
        val preview = _state.value.preview ?: return
        val profile = _profileId.value ?: return
        val ledger = _ledgerId.value ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null, result = null)
            runCatching { graph.billImport.commit(profile, ledger, _accountId.value, preview) }
                .onSuccess { result ->
                    _state.value = _state.value.copy(loading = false, result = result)
                    if (result.created > 0) SyncScheduler.scheduleNow(getApplication())
                }
                .onFailure { error -> _state.value = _state.value.copy(loading = false, error = error.message ?: "导入失败") }
        }
    }

    fun clear() { _state.value = BillImportUiState() }

    private fun looksLikeXlsx(fileName: String, mime: String?, bytes: ByteArray): Boolean {
        val lower = fileName.lowercase(Locale.ROOT)
        val zip = bytes.size >= 4 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()
        return lower.endsWith(".xlsx") || mime == "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" ||
            ((lower.endsWith(".xls") || mime == "application/vnd.ms-excel") && zip)
    }

    private fun displayName(uri: Uri): String {
        if (uri.scheme == "file") return File(requireNotNull(uri.path)).name
        val resolver = getApplication<Application>().contentResolver
        return runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull()?.takeIf(String::isNotBlank) ?: uri.lastPathSegment?.substringAfterLast('/') ?: "账单.csv"
    }
}
