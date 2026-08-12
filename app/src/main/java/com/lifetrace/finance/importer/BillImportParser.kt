package com.lifetrace.finance.importer

import com.lifetrace.finance.core.TransactionStatus
import com.lifetrace.finance.core.TransactionType
import java.io.ByteArrayInputStream
import java.math.BigDecimal
import java.math.RoundingMode
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

/**
 * Android port of LifeTrace EPIC-13 `web-client/src/importer.ts` semantics.
 * File decoding is platform-specific, while header aliases, source inference,
 * amount/date/type/status mapping deliberately stay aligned with LifeTrace.
 */
data class ImportedBill(
    val amountCents: Long,
    val type: TransactionType,
    val status: TransactionStatus,
    val occurredAt: Instant,
    val merchant: String?,
    val item: String?,
    val note: String?,
    val externalTransactionId: String?,
    val sourceType: String,
)

data class BillImportPreview(
    val bills: List<ImportedBill>,
    val warnings: List<String>,
    val sourceType: String,
    val totalDataRows: Int,
)

object BillImportParser {
    private val directionHeaders = listOf("收/支", "收支类型", "交易类型", "type")
    private val statusHeaders = listOf("当前状态", "交易状态", "status")
    private val amountHeaders = listOf("金额(元)", "金额", "交易金额", "amount")
    private val timeHeaders = listOf("交易时间", "时间", "日期", "occurredat")
    private val merchantHeaders = listOf("交易对方", "商户名称", "交易对象", "merchant")
    private val itemHeaders = listOf("商品", "商品说明", "交易内容", "item")
    private val noteHeaders = listOf("备注", "note")
    private val externalIdHeaders = listOf("交易单号", "订单号", "流水号", "transactionid")
    private val canonicalHeaders = (directionHeaders + statusHeaders + amountHeaders + timeHeaders + merchantHeaders + itemHeaders + externalIdHeaders)
        .map(::normalizeHeader).toSet()

    fun parse(fileName: String, mimeType: String?, bytes: ByteArray, zoneId: ZoneId = ZoneId.systemDefault()): BillImportPreview {
        require(bytes.isNotEmpty()) { "文件为空" }
        val lower = fileName.lowercase(Locale.ROOT)
        val rows = when {
            lower.endsWith(".xlsx") || mimeType == "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" -> parseXlsx(bytes)
            lower.endsWith(".csv") || mimeType == "text/csv" || mimeType == "text/comma-separated-values" -> parseCsv(decodeText(bytes))
            lower.endsWith(".xls") || mimeType == "application/vnd.ms-excel" -> {
                // Some providers label CSV as application/vnd.ms-excel; detect ZIP/OOXML or text before rejecting legacy BIFF.
                when {
                    isZip(bytes) -> parseXlsx(bytes)
                    looksText(bytes) -> parseCsv(decodeText(bytes))
                    else -> error("暂不支持旧版二进制 XLS，请在账单平台导出 CSV 或 XLSX")
                }
            }
            isZip(bytes) -> parseXlsx(bytes)
            looksText(bytes) -> parseCsv(decodeText(bytes))
            else -> error("仅支持 CSV/XLSX 账单文件")
        }
        return mapRows(fileName, rows, zoneId)
    }

    fun parseCsv(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val value = StringBuilder()
        var quoted = false
        var index = 0
        while (index < text.length) {
            val ch = text[index]
            when {
                ch == '"' -> {
                    if (quoted && index + 1 < text.length && text[index + 1] == '"') {
                        value.append('"')
                        index++
                    } else quoted = !quoted
                }
                ch == ',' && !quoted -> {
                    row.add(value.toString())
                    value.setLength(0)
                }
                (ch == '\n' || ch == '\r') && !quoted -> {
                    if (ch == '\r' && index + 1 < text.length && text[index + 1] == '\n') index++
                    row.add(value.toString())
                    value.setLength(0)
                    if (row.any { it.isNotBlank() }) rows.add(row)
                    row = mutableListOf()
                }
                else -> value.append(ch)
            }
            index++
        }
        row.add(value.toString())
        if (row.any { it.isNotBlank() }) rows.add(row)
        return rows
    }

    private fun mapRows(fileName: String, rawRows: List<List<Any?>>, zoneId: ZoneId): BillImportPreview {
        if (rawRows.isEmpty()) return BillImportPreview(emptyList(), listOf("文件中没有可读取的行"), inferSource(fileName, emptyList()), 0)
        val headerIndex = findHeaderRow(rawRows)
        if (headerIndex < 0) return BillImportPreview(emptyList(), listOf("未找到 LifeTrace 支持的账单表头"), inferSource(fileName, emptyList()), 0)
        val headers = rawRows[headerIndex].map { it?.toString().orEmpty() }
        val sourceType = inferSource(fileName, headers)
        val body = rawRows.drop(headerIndex + 1).filter { row -> row.any { !it?.toString().isNullOrBlank() } }
        val bills = mutableListOf<ImportedBill>()
        val warnings = mutableListOf<String>()

        body.forEachIndexed { bodyIndex, values ->
            val row = headers.mapIndexed { index, header -> header to values.getOrNull(index) }.toMap()
            try {
                val direction = pick(row, directionHeaders)?.toString().orEmpty().ifBlank { "支出" }
                val statusText = pick(row, statusHeaders)?.toString().orEmpty()
                val amountCents = normalizeAmountCents(pick(row, amountHeaders))
                val occurredAt = normalizeDate(pick(row, timeHeaders), zoneId)
                val type = when {
                    Regex("收入|收款|income", RegexOption.IGNORE_CASE).containsMatchIn(direction) -> TransactionType.INCOME
                    Regex("退款|refund", RegexOption.IGNORE_CASE).containsMatchIn(direction) -> TransactionType.REFUND
                    else -> TransactionType.EXPENSE
                }
                val status = if (Regex("成功|完成|confirmed", RegexOption.IGNORE_CASE).containsMatchIn(statusText)) {
                    TransactionStatus.CONFIRMED
                } else TransactionStatus.CANDIDATE
                bills += ImportedBill(
                    amountCents = amountCents,
                    type = type,
                    status = status,
                    occurredAt = occurredAt,
                    merchant = pick(row, merchantHeaders).cleanString(),
                    item = pick(row, itemHeaders).cleanString(),
                    note = pick(row, noteHeaders).cleanString(),
                    externalTransactionId = pick(row, externalIdHeaders).cleanString(),
                    sourceType = sourceType,
                )
            } catch (error: Throwable) {
                warnings += "第 ${headerIndex + bodyIndex + 2} 行：${error.message ?: "无法解析"}"
            }
        }
        return BillImportPreview(bills, warnings, sourceType, body.size)
    }

    private fun findHeaderRow(rows: List<List<Any?>>): Int {
        return rows.take(30).indexOfFirst { row ->
            val normalized = row.map { normalizeHeader(it) }.toSet()
            normalized.intersect(canonicalHeaders).size >= 2 &&
                normalized.any { it in amountHeaders.map(::normalizeHeader) } &&
                normalized.any { it in timeHeaders.map(::normalizeHeader) }
        }
    }

    private fun pick(row: Map<String, Any?>, names: List<String>): Any? {
        for (name in names) {
            val target = normalizeHeader(name)
            val entry = row.entries.firstOrNull { normalizeHeader(it.key) == target }
            val value = entry?.value
            if (value != null && value.toString().isNotBlank()) return value
        }
        return null
    }

    private fun inferSource(fileName: String, headers: List<String>): String {
        val combined = "$fileName ${headers.joinToString(" ")}".lowercase(Locale.ROOT)
        return when {
            "微信" in combined || "wechat" in combined || "weixin" in combined -> "wechat_import"
            "支付宝" in combined || "alipay" in combined -> "alipay_import"
            "银行" in combined || "bank" in combined -> "bank_import"
            else -> "file_import"
        }
    }

    private fun normalizeAmountCents(value: Any?): Long {
        val cleaned = value?.toString().orEmpty()
            .replace(Regex("[￥¥,\\s]"), "")
            .removePrefix("+")
        if (cleaned.isBlank()) error("缺少金额")
        val amount = cleaned.toBigDecimalOrNull() ?: error("金额格式无法识别")
        if (amount <= BigDecimal.ZERO) error("金额必须大于 0")
        return amount.setScale(2, RoundingMode.UNNECESSARY).movePointRight(2).longValueExact()
    }

    private fun normalizeDate(value: Any?, zoneId: ZoneId): Instant {
        if (value is Instant) return value
        if (value is LocalDateTime) return value.atZone(zoneId).toInstant()
        if (value is LocalDate) return value.atStartOfDay(zoneId).toInstant()
        val raw = value?.toString()?.trim()?.replace('/', '-')?.takeIf { it.isNotBlank() } ?: error("缺少日期")
        runCatching { return Instant.parse(raw) }
        runCatching { return java.time.OffsetDateTime.parse(raw).toInstant() }
        val formats = listOf(
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
            DateTimeFormatter.ofPattern("yyyy-M-d H:m:s"),
            DateTimeFormatter.ofPattern("yyyy-M-d H:m"),
        )
        formats.forEach { format ->
            try { return LocalDateTime.parse(raw, format).atZone(zoneId).toInstant() } catch (_: DateTimeParseException) { }
        }
        runCatching { return LocalDate.parse(raw).atStartOfDay(zoneId).toInstant() }
        error("日期格式无法识别")
    }

    private fun normalizeHeader(value: Any?): String = value?.toString().orEmpty().trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), "")
    private fun Any?.cleanString(): String? = this?.toString()?.trim()?.takeIf { it.isNotEmpty() && it.lowercase(Locale.ROOT) != "null" }

    private fun decodeText(bytes: ByteArray): String {
        val bomOffset = if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) 3 else 0
        val utf8 = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
        val content = bytes.copyOfRange(bomOffset, bytes.size)
        return runCatching { utf8.decode(ByteBuffer.wrap(content)).toString() }
            .getOrElse { String(content, charset("GB18030")) }
    }

    private fun looksText(bytes: ByteArray): Boolean = bytes.take(512).count { it == 0.toByte() } < 4
    private fun isZip(bytes: ByteArray): Boolean = bytes.size >= 4 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()

    private fun parseXlsx(bytes: ByteArray): List<List<Any?>> {
        val entries = mutableMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) entries[entry.name] = zip.readBytes()
                entry = zip.nextEntry
            }
        }
        val sharedStrings = entries["xl/sharedStrings.xml"]?.let(::readSharedStrings).orEmpty()
        val dateStyles = entries["xl/styles.xml"]?.let(::readDateStyleIndexes).orEmpty()
        val sheetPath = firstSheetPath(entries) ?: "xl/worksheets/sheet1.xml"
        val sheet = entries[sheetPath] ?: error("XLSX 中没有工作表")
        return readWorksheet(sheet, sharedStrings, dateStyles)
    }

    private fun firstSheetPath(entries: Map<String, ByteArray>): String? {
        val workbook = entries["xl/workbook.xml"] ?: return null
        val rels = entries["xl/_rels/workbook.xml.rels"] ?: return null
        val workbookDoc = xml(workbook)
        val firstSheet = workbookDoc.getElementsByTagNameNS("*", "sheet").item(0) as? Element ?: return null
        val relationId = firstSheet.getAttributeNS("http://schemas.openxmlformats.org/officeDocument/2006/relationships", "id")
            .ifBlank { firstSheet.getAttribute("r:id") }
        val relDoc = xml(rels)
        val relationships = relDoc.getElementsByTagNameNS("*", "Relationship")
        for (i in 0 until relationships.length) {
            val relation = relationships.item(i) as? Element ?: continue
            if (relation.getAttribute("Id") == relationId) {
                val target = relation.getAttribute("Target").trimStart('/')
                return if (target.startsWith("xl/")) target else "xl/${target.removePrefix("../")}" 
            }
        }
        return null
    }

    private fun readSharedStrings(bytes: ByteArray): List<String> {
        val doc = xml(bytes)
        val items = doc.getElementsByTagNameNS("*", "si")
        return (0 until items.length).map { index ->
            val item = items.item(index) as Element
            val texts = item.getElementsByTagNameNS("*", "t")
            buildString { for (i in 0 until texts.length) append(texts.item(i).textContent) }
        }
    }

    private fun readDateStyleIndexes(bytes: ByteArray): Set<Int> {
        val doc = xml(bytes)
        val customDateIds = mutableSetOf<Int>()
        val numFmts = doc.getElementsByTagNameNS("*", "numFmt")
        for (i in 0 until numFmts.length) {
            val item = numFmts.item(i) as? Element ?: continue
            val id = item.getAttribute("numFmtId").toIntOrNull() ?: continue
            val code = item.getAttribute("formatCode").lowercase(Locale.ROOT)
            if (Regex("[ymdhis]").containsMatchIn(code.replace(Regex("\\[[^]]+]"), ""))) customDateIds += id
        }
        val builtInDateIds = (14..22).toSet() + setOf(45, 46, 47)
        val cellXfs = doc.getElementsByTagNameNS("*", "cellXfs").item(0) as? Element ?: return emptySet()
        val xfs = cellXfs.getElementsByTagNameNS("*", "xf")
        val result = mutableSetOf<Int>()
        for (index in 0 until xfs.length) {
            val xf = xfs.item(index) as? Element ?: continue
            val numFmtId = xf.getAttribute("numFmtId").toIntOrNull() ?: 0
            if (numFmtId in builtInDateIds || numFmtId in customDateIds) result += index
        }
        return result
    }

    private fun readWorksheet(bytes: ByteArray, sharedStrings: List<String>, dateStyles: Set<Int>): List<List<Any?>> {
        val doc = xml(bytes)
        val rowNodes = doc.getElementsByTagNameNS("*", "row")
        val result = mutableListOf<List<Any?>>()
        for (rowIndex in 0 until rowNodes.length) {
            val row = rowNodes.item(rowIndex) as Element
            val cells = row.getElementsByTagNameNS("*", "c")
            val values = mutableMapOf<Int, Any?>()
            var maxColumn = -1
            for (cellIndex in 0 until cells.length) {
                val cell = cells.item(cellIndex) as Element
                val ref = cell.getAttribute("r")
                val column = columnIndex(ref)
                maxColumn = maxOf(maxColumn, column)
                val type = cell.getAttribute("t")
                val style = cell.getAttribute("s").toIntOrNull()
                val raw = when (type) {
                    "inlineStr" -> cell.getElementsByTagNameNS("*", "t").item(0)?.textContent
                    else -> cell.getElementsByTagNameNS("*", "v").item(0)?.textContent
                }
                values[column] = when {
                    raw == null -> null
                    type == "s" -> sharedStrings.getOrNull(raw.toIntOrNull() ?: -1) ?: raw
                    style != null && style in dateStyles && raw.toDoubleOrNull() != null -> excelSerialToDateTime(raw.toDouble())
                    else -> raw
                }
            }
            if (maxColumn >= 0) result += (0..maxColumn).map { values[it] }
        }
        return result
    }

    private fun excelSerialToDateTime(serial: Double): LocalDateTime {
        val wholeDays = kotlin.math.floor(serial).toLong()
        val seconds = kotlin.math.round((serial - wholeDays) * 86_400.0).toLong()
        // Excel's 1900 date system includes the historical leap-year bug; 1899-12-30 is the conventional epoch.
        return LocalDate.of(1899, 12, 30).plusDays(wholeDays).atStartOfDay().plusSeconds(seconds)
    }

    private fun columnIndex(reference: String): Int {
        val letters = reference.takeWhile { it.isLetter() }.uppercase(Locale.ROOT)
        if (letters.isEmpty()) return 0
        var value = 0
        letters.forEach { value = value * 26 + (it - 'A' + 1) }
        return value - 1
    }

    private fun xml(bytes: ByteArray) = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        .newDocumentBuilder().parse(ByteArrayInputStream(bytes))
}
