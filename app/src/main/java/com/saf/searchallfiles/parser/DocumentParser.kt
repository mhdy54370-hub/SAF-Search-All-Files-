package com.saf.searchallfiles.parser

import java.io.File
import java.util.zip.ZipFile

object DocumentParser {

    val supportedExtensions = setOf(
        "txt", "text", "md", "markdown", "log", "ini", "cfg",
        "csv", "tsv", "json", "xml", "yaml", "yml",
        "java", "kt", "py", "js", "ts", "html", "htm", "css",
        "c", "cpp", "h", "cs", "php", "rb", "sh", "bat",
        "pdf", "docx", "odt"
    )

    private const val MAX_CONTENT_CHARS = 100_000

    fun extractText(file: File): String {
        return try {
            when (file.extension.lowercase()) {
                "pdf"  -> extractPdf(file)
                "docx" -> extractDocx(file)
                "odt"  -> extractOdt(file)
                else   -> file.readText(Charsets.UTF_8).take(MAX_CONTENT_CHARS)
            }
        } catch (e: Exception) {
            ""
        }
    }

    // ── PDF — no library needed ───────────────────────────────────────────────
    // Reads raw PDF bytes and extracts text from content stream operators.
    // Works for most text-based PDFs (not scanned/image-only PDFs).

    private fun extractPdf(file: File): String {
        val bytes = file.readBytes()
        val raw   = String(bytes, Charsets.ISO_8859_1)
        val sb    = StringBuilder()

        // Extract text between BT (begin text) and ET (end text) markers
        var pos = 0
        while (pos < raw.length) {
            val btIdx = raw.indexOf("BT", pos)
            if (btIdx == -1) break
            val etIdx = raw.indexOf("ET", btIdx)
            if (etIdx == -1) break

            val block = raw.substring(btIdx, etIdx)

            // Pull strings from parentheses: (Hello World) Tj  or [(H)(i)] TJ
            val parenRegex = Regex("""\(([^)]*)\)""")
            parenRegex.findAll(block).forEach { match ->
                val text = match.groupValues[1]
                    .replace("\\n", "\n")
                    .replace("\\r", " ")
                    .replace("\\t", " ")
                    // Unescape PDF octal e.g. \055 → -
                    .replace(Regex("""\\(\d{3})""")) { mr ->
                        mr.groupValues[1].toInt(8).toChar().toString()
                    }
                    .replace("\\(", "(")
                    .replace("\\)", ")")
                    .replace("\\\\", "\\")
                // Only keep printable ASCII
                val printable = text.filter { it.code in 32..126 || it == '\n' }
                if (printable.isNotBlank()) sb.append(printable).append(' ')
            }
            pos = etIdx + 2
        }

        return sb.toString().trim().take(MAX_CONTENT_CHARS)
    }

    // ── DOCX ─────────────────────────────────────────────────────────────────

    private fun extractDocx(file: File): String {
        return try {
            val zip   = ZipFile(file)
            val entry = zip.getEntry("word/document.xml") ?: run { zip.close(); return "" }
            val xml   = zip.getInputStream(entry).readBytes().toString(Charsets.UTF_8)
            zip.close()
            xml.stripXmlTags().take(MAX_CONTENT_CHARS)
        } catch (e: Exception) { "" }
    }

    // ── ODT ──────────────────────────────────────────────────────────────────

    private fun extractOdt(file: File): String {
        return try {
            val zip   = ZipFile(file)
            val entry = zip.getEntry("content.xml") ?: run { zip.close(); return "" }
            val xml   = zip.getInputStream(entry).readBytes().toString(Charsets.UTF_8)
            zip.close()
            xml.stripXmlTags().take(MAX_CONTENT_CHARS)
        } catch (e: Exception) { "" }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun String.stripXmlTags(): String =
        replace(Regex("<[^>]*>"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
}
