package com.lifetrace.finance.importer

import com.lifetrace.finance.core.TransactionStatus
import com.lifetrace.finance.core.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.time.ZoneId
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class BillImportParserTest {
    private val zone = ZoneId.of("Asia/Shanghai")

    @Test
    fun csvUsesLifeTraceHeaderAliasesAndQuotedFields() {
        val csv = """
            说明,支付宝导出账单
            交易时间,收/支,交易对方,商品说明,金额(元),当前状态,交易单号,备注
            2026-08-12 09:30:00,支出,"瑞幸,咖啡",拿铁,28.50,交易成功,A-1001,早餐
            2026-08-12 10:00:00,退款,商户,退款,5.00,完成,A-1002,
        """.trimIndent()

        val preview = BillImportParser.parse("支付宝账单.csv", "text/csv", csv.toByteArray(), zone)

        assertEquals("alipay_import", preview.sourceType)
        assertEquals(2, preview.bills.size)
        assertTrue(preview.warnings.isEmpty())
        assertEquals(2850L, preview.bills[0].amountCents)
        assertEquals("瑞幸,咖啡", preview.bills[0].merchant)
        assertEquals(TransactionType.EXPENSE, preview.bills[0].type)
        assertEquals(TransactionStatus.CONFIRMED, preview.bills[0].status)
        assertEquals(TransactionType.REFUND, preview.bills[1].type)
    }

    @Test
    fun invalidRowsBecomeWarningsWithoutDroppingValidRows() {
        val csv = """
            时间,交易类型,交易金额,交易状态,交易对象
            2026-08-12 09:30:00,收入,100.00,成功,工资
            not-a-date,支出,xx,成功,坏数据
        """.trimIndent()

        val preview = BillImportParser.parse("bank.csv", "text/csv", csv.toByteArray(), zone)

        assertEquals(1, preview.bills.size)
        assertEquals(1, preview.warnings.size)
        assertEquals(TransactionType.INCOME, preview.bills.single().type)
        assertEquals(10_000L, preview.bills.single().amountCents)
    }

    @Test
    fun xlsxSharedStringsMapThroughSameImporter() {
        val xlsx = simpleXlsx(
            listOf(
                listOf("交易时间", "收支类型", "交易对方", "金额", "交易状态", "订单号"),
                listOf("2026-08-12 11:20:00", "支出", "微信支付商户", "36.00", "成功", "WX-1"),
            ),
        )

        val preview = BillImportParser.parse("微信支付账单.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", xlsx, zone)

        assertEquals("wechat_import", preview.sourceType)
        assertEquals(1, preview.bills.size)
        assertEquals(3600L, preview.bills.single().amountCents)
        assertEquals("WX-1", preview.bills.single().externalTransactionId)
    }

    private fun simpleXlsx(rows: List<List<String>>): ByteArray {
        val unique = rows.flatten().distinct()
        val index = unique.withIndex().associate { it.value to it.index }
        val shared = buildString {
            append("<?xml version=\"1.0\" encoding=\"UTF-8\"?><sst xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">")
            unique.forEach { append("<si><t>").append(xmlEscape(it)).append("</t></si>") }
            append("</sst>")
        }
        val sheet = buildString {
            append("<?xml version=\"1.0\" encoding=\"UTF-8\"?><worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData>")
            rows.forEachIndexed { r, row ->
                append("<row r=\"").append(r + 1).append("\">")
                row.forEachIndexed { c, value ->
                    append("<c r=\"").append(column(c)).append(r + 1).append("\" t=\"s\"><v>").append(index.getValue(value)).append("</v></c>")
                }
                append("</row>")
            }
            append("</sheetData></worksheet>")
        }
        val workbook = """<?xml version="1.0"?><workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="Sheet1" sheetId="1" r:id="rId1"/></sheets></workbook>"""
        val rels = """<?xml version="1.0"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Target="worksheets/sheet1.xml" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet"/></Relationships>"""
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            fun entry(name: String, text: String) {
                zip.putNextEntry(ZipEntry(name)); zip.write(text.toByteArray()); zip.closeEntry()
            }
            entry("xl/workbook.xml", workbook)
            entry("xl/_rels/workbook.xml.rels", rels)
            entry("xl/sharedStrings.xml", shared)
            entry("xl/worksheets/sheet1.xml", sheet)
        }
        return out.toByteArray()
    }

    private fun column(index: Int): String {
        var n = index + 1
        var result = ""
        while (n > 0) { val rem = (n - 1) % 26; result = ('A'.code + rem).toChar() + result; n = (n - 1) / 26 }
        return result
    }

    private fun xmlEscape(value: String): String = value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
}
