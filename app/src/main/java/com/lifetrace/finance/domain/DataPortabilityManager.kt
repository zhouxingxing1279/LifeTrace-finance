package com.lifetrace.finance.domain

import android.content.Context
import android.net.Uri
import com.lifetrace.finance.AppGraph
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.time.Instant
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class RestorePreview(val transactionCount: Int, val attachmentCount: Int, val exportedAt: String)

class DataPortabilityManager(private val context: Context) {
    private val graph get() = AppGraph.get(context)
    private val pendingRoot get() = File(context.filesDir, "pending_finance_restore")

    suspend fun exportCurrentLedgerCsv(uri: Uri): Int = withContext(Dispatchers.IO) {
        val profile = graph.finance.ensureProfile()
        val ledger = graph.finance.ensureDefaultLedger(profile.id)
        val selected = graph.ledgerSelection.selectedLedgerId(profile.id) ?: ledger.id
        val rows = graph.bookkeeping.transactions(profile.id, selected).first().filter { it.deletedAt == null }
        val accounts = graph.bookkeeping.accounts(profile.id, selected).first().associateBy { it.id }
        val categories = graph.bookkeeping.categories(profile.id, selected).first().associateBy { it.id }
        val tags = graph.bookkeeping.tags(profile.id, selected).first().associateBy { it.id }
        val relations = graph.bookkeeping.transactionTags(profile.id).first().groupBy { it.transactionId }
        context.contentResolver.openOutputStream(uri, "wt")!!.bufferedWriter(Charsets.UTF_8).use { writer ->
            writer.write("\uFEFF类型,分类,金额,账户,转出账户,转入账户,商家,备注,日期,时间,标签,附件数,币种\r\n")
            rows.forEach { tx ->
                val tagNames = relations[tx.id].orEmpty().mapNotNull { tags[it.tagId]?.name }.joinToString("|")
                val attachmentCount = graph.bookkeeping.attachmentsForTransaction(tx.id).first().size
                val fields = listOf(
                    when (tx.transactionType) { "expense" -> "支出"; "income" -> "收入"; "transfer" -> "转账"; "refund" -> "退款"; else -> tx.transactionType },
                    categories[tx.categoryId]?.name.orEmpty(),
                    "%.2f".format((tx.nativeAmountCents ?: tx.amountCents) / 100.0),
                    accounts[tx.accountId]?.name.orEmpty(), accounts[tx.accountId]?.name.orEmpty(), accounts[tx.toAccountId]?.name.orEmpty(),
                    tx.merchant.orEmpty(), tx.note.orEmpty(), tx.localDate, tx.occurredAt, tagNames, attachmentCount.toString(), tx.nativeCurrency ?: tx.currency,
                )
                writer.write(fields.joinToString(",", postfix = "\r\n") { csv(it) })
            }
        }
        rows.size
    }

    suspend fun exportBackup(uri: Uri): RestorePreview = withContext(Dispatchers.IO) {
        val profile = graph.finance.ensureProfile()
        val transactions = graph.finance.transactions(profile.id).first().count { it.deletedAt == null }
        val attachmentRoot = File(context.filesDir, "transaction_attachments")
        graph.db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").use { while (it.moveToNext()) Unit }
        val db = context.getDatabasePath("lifetrace-finance.db")
        require(db.isFile) { "数据库文件不存在" }
        val exportedAt = Instant.now().toString()
        context.contentResolver.openOutputStream(uri, "wt")!!.use { output ->
            ZipOutputStream(BufferedOutputStream(output)).use { zip ->
                val manifest = JSONObject().put("format", "lifetrace-finance-backup").put("version", 1)
                    .put("databaseVersion", 2).put("exportedAt", exportedAt).put("transactionCount", transactions)
                putBytes(zip, "manifest.json", manifest.toString(2).toByteArray())
                putFile(zip, "database/lifetrace-finance.db", db)
                attachmentRoot.listFiles()?.filter(File::isFile)?.forEach { putFile(zip, "attachments/${it.name}", it) }
            }
        }
        RestorePreview(transactions, attachmentRoot.listFiles()?.count(File::isFile) ?: 0, exportedAt)
    }

    suspend fun stageRestore(uri: Uri): RestorePreview = withContext(Dispatchers.IO) {
        if (pendingRoot.exists()) pendingRoot.deleteRecursively()
        pendingRoot.mkdirs()
        var entries = 0
        var total = 0L
        context.contentResolver.openInputStream(uri)!!.use { input ->
            ZipInputStream(BufferedInputStream(input)).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    entries++
                    require(entries <= 10_000) { "备份文件条目过多" }
                    val target = File(pendingRoot, entry.name).canonicalFile
                    require(target.toPath().startsWith(pendingRoot.canonicalFile.toPath())) { "备份包含非法路径" }
                    if (entry.isDirectory) target.mkdirs() else {
                        target.parentFile?.mkdirs()
                        target.outputStream().use { output ->
                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                            while (true) {
                                val read = zip.read(buffer)
                                if (read < 0) break
                                total += read
                                require(total <= 2L * 1024 * 1024 * 1024) { "备份文件超过 2 GB" }
                                output.write(buffer, 0, read)
                            }
                        }
                    }
                    zip.closeEntry()
                }
            }
        }
        val manifestFile = File(pendingRoot, "manifest.json")
        val databaseFile = File(pendingRoot, "database/lifetrace-finance.db")
        require(manifestFile.isFile && databaseFile.isFile) { "不是有效的 LifeTrace Finance 备份" }
        val sqliteHeader = ByteArray(16)
        val headerSize = databaseFile.inputStream().use { it.read(sqliteHeader) }
        require(headerSize == 16 && sqliteHeader.toString(Charsets.US_ASCII) == "SQLite format 3\u0000") { "数据库文件损坏" }
        val manifest = JSONObject(manifestFile.readText())
        require(manifest.optString("format") == "lifetrace-finance-backup" && manifest.optInt("databaseVersion") == 2) { "备份版本不兼容" }
        RestorePreview(manifest.optInt("transactionCount"), File(pendingRoot, "attachments").listFiles()?.count(File::isFile) ?: 0, manifest.optString("exportedAt"))
    }

    fun confirmRestore() {
        require(File(pendingRoot, "database/lifetrace-finance.db").isFile)
        context.getSharedPreferences("finance_restore", Context.MODE_PRIVATE).edit().putBoolean("pending", true).commit()
    }

    companion object {
        fun applyPendingRestore(context: Context): Boolean {
            val prefs = context.getSharedPreferences("finance_restore", Context.MODE_PRIVATE)
            if (!prefs.getBoolean("pending", false)) return false
            val pending = File(context.filesDir, "pending_finance_restore")
            val sourceDb = File(pending, "database/lifetrace-finance.db")
            if (!sourceDb.isFile) { prefs.edit().clear().commit(); return false }
            val targetDb = context.getDatabasePath("lifetrace-finance.db")
            targetDb.parentFile?.mkdirs()
            sourceDb.copyTo(targetDb, overwrite = true)
            File("${targetDb.path}-wal").delete(); File("${targetDb.path}-shm").delete()
            val targetAttachments = File(context.filesDir, "transaction_attachments")
            if (targetAttachments.exists()) targetAttachments.deleteRecursively()
            File(pending, "attachments").takeIf(File::exists)?.copyRecursively(targetAttachments, overwrite = true)
            pending.deleteRecursively(); prefs.edit().clear().commit()
            return true
        }

        private fun csv(value: String): String = if (value.any { it == ',' || it == '"' || it == '\r' || it == '\n' }) "\"${value.replace("\"", "\"\"")}\"" else value
        private fun putBytes(zip: ZipOutputStream, name: String, bytes: ByteArray) { zip.putNextEntry(ZipEntry(name)); zip.write(bytes); zip.closeEntry() }
        private fun putFile(zip: ZipOutputStream, name: String, file: File) { zip.putNextEntry(ZipEntry(name)); file.inputStream().use { it.copyTo(zip) }; zip.closeEntry() }
    }
}
