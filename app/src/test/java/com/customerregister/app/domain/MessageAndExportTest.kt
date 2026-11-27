package com.customerregister.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream

class MessageAndExportTest {

    @Test fun templateVariablesAreReplaced() {
        val t = "Hello {name},\n\nYour account will expire on {expiry_date}.\nID {id}, {remaining_days} days left."
        assertEquals(
            "Hello Ahmad,\n\nYour account will expire on 05/12/2026.\nID 1024, 3 days left.",
            MessageTemplate.render(t, "Ahmad", "1024", "05/12/2026", 3)
        )
        assertEquals("0", MessageTemplate.render("{remaining_days}", "a", "1", "x", -4))
    }

    @Test fun phoneNormalization() {
        assertEquals("963944123456", PhoneNumbers.normalize("+963 944-123-456", "963"))
        assertEquals("963944123456", PhoneNumbers.normalize("00963944123456", "963"))
        assertEquals("963944123456", PhoneNumbers.normalize("0944123456", "963"))
        assertEquals("963944123456", PhoneNumbers.normalize("944123456", "963"))
        assertEquals("4915112345678", PhoneNumbers.normalize("+49 151 12345678", "963"))
        assertTrue(PhoneNumbers.isValid("963944123456"))
        assertTrue(!PhoneNumbers.isValid("123"))
    }

    @Test fun csvEscaping() {
        assertEquals("plain", CsvExporter.escape("plain"))
        assertEquals("\"a,b\"", CsvExporter.escape("a,b"))
        assertEquals("\"say \"\"hi\"\"\"", CsvExporter.escape("say \"hi\""))
        assertEquals("'=SUM(A1)", CsvExporter.escape("=SUM(A1)"))
        assertEquals("+963944123456", CsvExporter.escape("+963944123456"))
        val csv = CsvExporter.toCsv(ExportTable("s", listOf("ID", "Name"), listOf(listOf("1001", "أحمد"))))
        assertEquals("﻿ID,Name\r\n1001,أحمد\r\n", csv)
    }

    @Test fun xlsxContainsAllParts() {
        val out = ByteArrayOutputStream()
        XlsxExporter.write(
            listOf(
                ExportTable("Customers", listOf("ID", "Name"), listOf(listOf("1001", "Ahmad & Sons <x>"))),
                ExportTable("History", listOf("A"), emptyList()),
            ),
            out
        )
        val names = mutableListOf<String>()
        var sheet1 = ""
        ZipInputStream(out.toByteArray().inputStream()).use { z ->
            while (true) {
                val e = z.nextEntry ?: break
                names += e.name
                val text = z.readBytes().toString(Charsets.UTF_8)
                if (e.name == "xl/worksheets/sheet1.xml") sheet1 = text
            }
        }
        assertTrue(names.containsAll(listOf("[Content_Types].xml", "_rels/.rels", "xl/workbook.xml",
            "xl/_rels/workbook.xml.rels", "xl/styles.xml", "xl/worksheets/sheet1.xml", "xl/worksheets/sheet2.xml")))
        assertTrue(sheet1.contains("Ahmad &amp; Sons &lt;x&gt;"))
        assertTrue(sheet1.contains("""<c r="B2" t="inlineStr">"""))
        assertEquals("A", XlsxExporter.columnName(0))
        assertEquals("Z", XlsxExporter.columnName(25))
        assertEquals("AA", XlsxExporter.columnName(26))
    }
}
