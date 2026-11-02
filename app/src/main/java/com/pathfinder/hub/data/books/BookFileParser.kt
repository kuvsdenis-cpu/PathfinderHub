package com.pathfinder.hub.data.books

import android.util.Log
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader

import android.content.Context
import android.net.Uri
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.File
import java.io.InputStreamReader

object BookFileParser {

    data class Parsed(val plainText: String, val extension: String)

    fun parse(context: Context, uri: Uri, mimeType: String?, displayName: String?): Parsed {
    // PDFBox-android требует инициализации ресурсов (glyphlist.txt и т.п.)
    // до первого парсинга. Иначе — ExceptionInInitializerError.
    try {
        PDFBoxResourceLoader.init(context.applicationContext)
    } catch (e: Throwable) {
        Log.w("BookFileParser", "PDFBoxResourceLoader.init failed", e)
    }

        val ext = detectExtension(mimeType, displayName)
        return when (ext) {
            "pdf" -> Parsed(parsePdf(context, uri), "pdf")
            "fb2" -> Parsed(parseFb2(context, uri), "fb2")
            else  -> Parsed(parseTxt(context, uri), "txt")
        }
    }

    private fun detectExtension(mimeType: String?, displayName: String?): String {
        when (mimeType) {
            "application/pdf" -> return "pdf"
            "application/x-fictionbook+xml", "application/fb2" -> return "fb2"
        }
        if (mimeType?.startsWith("text/") == true) return "txt"
        val name = displayName?.lowercase() ?: return "txt"
        return when {
            name.endsWith(".pdf") -> "pdf"
            name.endsWith(".fb2") -> "fb2"
            else -> "txt"
        }
    }

    private fun parseTxt(context: Context, uri: Uri): String {
        context.contentResolver.openInputStream(uri)?.use { input ->
            val bytes = input.readBytes()
            val utf8 = String(bytes, Charsets.UTF_8)
            return if (utf8.contains('\uFFFD')) String(bytes, charset("windows-1251")) else utf8
        }
        throw IllegalStateException("Не удалось открыть файл")
    }

    private fun parsePdf(context: Context, uri: Uri): String {
        context.contentResolver.openInputStream(uri)?.use { input ->
            val tmp = File.createTempFile("book_", ".pdf", context.cacheDir)
            tmp.outputStream().use { input.copyTo(it) }
            try {
                PDDocument.load(tmp).use { doc -> return PDFTextStripper().getText(doc) }
            } finally { tmp.delete() }
        }
        throw IllegalStateException("Не удалось открыть PDF")
    }

    private fun parseFb2(context: Context, uri: Uri): String {
        val factory = XmlPullParserFactory.newInstance()
        factory.isNamespaceAware = true
        val parser = factory.newPullParser()
        context.contentResolver.openInputStream(uri)?.use { input ->
            parser.setInput(InputStreamReader(input, Charsets.UTF_8))
            val sb = StringBuilder()
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> when (parser.name) {
                        "section", "p", "title" -> sb.append("\n")
                        "empty-line" -> sb.append("\n")
                    }
                    XmlPullParser.TEXT -> sb.append(parser.text)
                }
                event = parser.next()
            }
            return sb.toString().trim()
        }
        throw IllegalStateException("Не удалось открыть FB2")
    }
}
