package com.lifetrace.finance.importer

import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class XlsxSecurityGuardTest {
    @Test
    fun acceptsSmallSafeOoxmlPackage() {
        val bytes = zipOf(
            "xl/workbook.xml" to "<?xml version=\"1.0\"?><workbook><sheets/></workbook>",
            "xl/worksheets/sheet1.xml" to "<?xml version=\"1.0\"?><worksheet><sheetData/></worksheet>",
        )

        XlsxSecurityGuard.validate(bytes)
    }

    @Test
    fun rejectsDoctypeAndExternalEntityDeclarations() {
        val bytes = zipOf(
            "xl/workbook.xml" to """
                <?xml version="1.0"?>
                <!DOCTYPE workbook [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
                <workbook>&xxe;</workbook>
            """.trimIndent(),
        )

        assertThrows(IllegalArgumentException::class.java) {
            XlsxSecurityGuard.validate(bytes)
        }
    }

    private fun zipOf(vararg entries: Pair<String, String>): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            entries.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray())
                zip.closeEntry()
            }
        }
        return output.toByteArray()
    }
}
