package com.lifetrace.finance.importer

import java.io.ByteArrayInputStream
import java.util.Locale
import java.util.zip.ZipInputStream

/**
 * Defense-in-depth before BillImportParser opens OOXML parts with the platform XML parser.
 * Rejects XML entity declarations/DOCTYPE and caps total inflated content to avoid zip bombs.
 */
object XlsxSecurityGuard {
    private const val MAX_INFLATED_BYTES = 64L * 1024 * 1024
    private const val MAX_ENTRY_BYTES = 16L * 1024 * 1024
    private const val MAX_ENTRIES = 512

    fun validate(bytes: ByteArray) {
        var total = 0L
        var entries = 0
        val buffer = ByteArray(8192)
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    entries++
                    require(entries <= MAX_ENTRIES) { "XLSX 文件包含过多条目" }
                    var entryBytes = 0L
                    val sample = StringBuilder()
                    while (true) {
                        val read = zip.read(buffer)
                        if (read < 0) break
                        entryBytes += read
                        total += read
                        require(entryBytes <= MAX_ENTRY_BYTES) { "XLSX 单个内容块过大" }
                        require(total <= MAX_INFLATED_BYTES) { "XLSX 解压后内容过大" }
                        if (entry.name.lowercase(Locale.ROOT).endsWith(".xml") && sample.length < 8192) {
                            sample.append(String(buffer, 0, minOf(read, 8192 - sample.length), Charsets.UTF_8))
                        }
                    }
                    if (entry.name.lowercase(Locale.ROOT).endsWith(".xml")) {
                        val head = sample.toString().uppercase(Locale.ROOT)
                        require("<!DOCTYPE" !in head && "<!ENTITY" !in head) { "XLSX 包含不安全的 XML 声明" }
                    }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        require(entries > 0) { "XLSX 文件为空或损坏" }
    }
}
