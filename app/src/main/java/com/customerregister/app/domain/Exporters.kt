package com.customerregister.app.domain

import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** A flat table: header row + data rows. Cells are written as text (dates as dd/MM/yyyy). */
data class ExportTable(val sheetName: String, val header: List<String>, val rows: List<List<String>>)

object CsvExporter {

    fun escape(cell: String): String {
        // Neutralise spreadsheet formula injection, then apply RFC 4180 quoting.
        val safe = if (cell.isNotEmpty() && cell[0] in "=+-@" && !cell.matches(Regex("^[+-]?\\d+$"))) "'$cell" else cell
        val needsQuotes = safe.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        return if (needsQuotes) "\"" + safe.replace("\"", "\"\"") + "\"" else safe
    }

    fun toCsv(table: ExportTable): String = buildString {
        append('﻿') // UTF-8 BOM so Excel shows Arabic names correctly
        (listOf(table.header) + table.rows).forEach { row ->
            append(row.joinToString(",") { escape(it) })
            append("\r\n")
        }
    }

    fun write(table: ExportTable, out: OutputStream) {
        out.write(toCsv(table).toByteArray(Charsets.UTF_8))
        out.flush()
    }
}

/**
 * Minimal, dependency-free XLSX (Office Open XML) writer. Produces a valid workbook with one
 * or more sheets, inline strings, a bold header row and frozen top row.
 */
object XlsxExporter {

    private fun xml(s: String): String = buildString {
        s.forEach { c ->
            when {
                c == '&' -> append("&amp;")
                c == '<' -> append("&lt;")
                c == '>' -> append("&gt;")
                c == '"' -> append("&quot;")
                c == '\t' || c == '\n' || c == '\r' -> append(c)
                c < ' ' -> Unit // control characters are illegal in XML 1.0
                else -> append(c)
            }
        }
    }

    fun columnName(index: Int): String {
        var n = index + 1
        val sb = StringBuilder()
        while (n > 0) {
            val rem = (n - 1) % 26
            sb.insert(0, ('A' + rem))
            n = (n - 1) / 26
        }
        return sb.toString()
    }

    private fun sheetXml(table: ExportTable): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")
        append("""<sheetViews><sheetView workbookViewId="0"><pane ySplit="1" topLeftCell="A2" activePane="bottomLeft" state="frozen"/></sheetView></sheetViews>""")
        append("<cols>")
        table.header.indices.forEach { i ->
            val width = (listOf(table.header[i]) + table.rows.map { it.getOrElse(i) { "" } })
                .maxOf { it.length }.coerceIn(8, 50) + 2
            append("""<col min="${i + 1}" max="${i + 1}" width="$width" customWidth="1"/>""")
        }
        append("</cols><sheetData>")
        (listOf(table.header) + table.rows).forEachIndexed { r, row ->
            append("""<row r="${r + 1}">""")
            row.forEachIndexed { c, value ->
                val ref = columnName(c) + (r + 1)
                val style = if (r == 0) """ s="1"""" else ""
                append("""<c r="$ref" t="inlineStr"$style><is><t xml:space="preserve">${xml(value)}</t></is></c>""")
            }
            append("</row>")
        }
        append("</sheetData></worksheet>")
    }

    fun write(tables: List<ExportTable>, out: OutputStream) {
        val zip = ZipOutputStream(out)
        fun entry(name: String, content: String) {
            zip.putNextEntry(ZipEntry(name))
            zip.write(content.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
        entry(
            "[Content_Types].xml",
            buildString {
                append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
                append("""<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">""")
                append("""<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>""")
                append("""<Default Extension="xml" ContentType="application/xml"/>""")
                append("""<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>""")
                append("""<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>""")
                tables.indices.forEach {
                    append("""<Override PartName="/xl/worksheets/sheet${it + 1}.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>""")
                }
                append("</Types>")
            }
        )
        entry(
            "_rels/.rels",
            """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>"""
        )
        entry(
            "xl/workbook.xml",
            buildString {
                append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
                append("""<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets>""")
                tables.forEachIndexed { i, t ->
                    val name = xml(t.sheetName.replace(Regex("[\\\\/?*\\[\\]:]"), " ").take(31).ifBlank { "Sheet${i + 1}" })
                    append("""<sheet name="$name" sheetId="${i + 1}" r:id="rId${i + 1}"/>""")
                }
                append("</sheets></workbook>")
            }
        )
        entry(
            "xl/_rels/workbook.xml.rels",
            buildString {
                append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
                append("""<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""")
                tables.indices.forEach {
                    append("""<Relationship Id="rId${it + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet${it + 1}.xml"/>""")
                }
                append("""<Relationship Id="rId${tables.size + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>""")
                append("</Relationships>")
            }
        )
        entry(
            "xl/styles.xml",
            """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><fonts count="2"><font><sz val="11"/><name val="Calibri"/></font><font><b/><sz val="11"/><color rgb="FFFFFFFF"/><name val="Calibri"/></font></fonts><fills count="3"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill><fill><patternFill patternType="solid"><fgColor rgb="FF0B1430"/><bgColor indexed="64"/></patternFill></fill></fills><borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders><cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs><cellXfs count="2"><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/><xf numFmtId="0" fontId="1" fillId="2" borderId="0" xfId="0" applyFont="1" applyFill="1"/></cellXfs><cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles></styleSheet>"""
        )
        tables.forEachIndexed { i, t -> entry("xl/worksheets/sheet${i + 1}.xml", sheetXml(t)) }
        zip.finish()
        zip.flush()
    }
}
