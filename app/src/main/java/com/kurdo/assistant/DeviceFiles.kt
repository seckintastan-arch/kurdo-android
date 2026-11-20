package com.kurdo.assistant

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private val readableExtensions = setOf("txt", "md", "csv", "json", "xml", "log", "html")
private val sensitiveName = Regex("(?i)(password|passwd|secret|credential|token|\\.env|id_rsa|keystore|wallet|backup)")
private val sensitiveContent = Regex("(?i)(api[_ -]?key|access[_ -]?token|password|passwd|private[_ -]?key|secret[_ -]?key)\\s*[:=]")

internal fun hasSharedStorageAccess(): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()

internal suspend fun readRelevantDeviceFiles(
    context: Context,
    question: String,
    selectedTreeUri: String?
): List<String> = withContext(Dispatchers.IO) {
    val terms = question.lowercase().split(Regex("[^\\p{L}\\p{N}]+"))
        .filter { it.length > 2 }
        .toSet()
    if (terms.isEmpty()) return@withContext emptyList()

    val matches = mutableListOf<Pair<Int, String>>()
    var visitedFiles = 0
    var visitedNodes = 0
    var readChars = 0
    val maxFiles = 48
    val maxNodes = 1_200
    val maxTotalChars = 12_000

    fun capture(name: String, open: () -> java.io.InputStream?) {
        if (visitedFiles >= maxFiles || readChars >= maxTotalChars || sensitiveName.containsMatchIn(name)) return
        val extension = name.substringAfterLast('.', "").lowercase()
        if (extension !in readableExtensions) return
        visitedFiles++
        val content = runCatching {
            open()?.bufferedReader(Charsets.UTF_8)?.use { it.readText().take(12_000) }
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: return
        if (sensitiveContent.containsMatchIn(content)) return
        val searchable = (name + " " + content).lowercase()
        val score = terms.count(searchable::contains)
        if (score == 0) return
        val remaining = (maxTotalChars - readChars).coerceAtLeast(0)
        val excerpt = content.take(minOf(4_000, remaining))
        if (excerpt.isNotBlank()) {
            readChars += excerpt.length
            matches += score to "Dosya: $name\\nİçerik: $excerpt"
        }
    }

    if (hasSharedStorageAccess()) {
        val root = Environment.getExternalStorageDirectory()
        fun visit(directory: File, depth: Int) {
            if (depth > 6 || visitedFiles >= maxFiles || visitedNodes >= maxNodes || readChars >= maxTotalChars) return
            val children = runCatching { directory.listFiles()?.take(200).orEmpty() }.getOrDefault(emptyList())
            children.forEach { child ->
                if (++visitedNodes > maxNodes) return@forEach
                if (child.isDirectory) {
                    if (child.name != "Android") visit(child, depth + 1)
                } else if (child.isFile && child.length() <= 1_000_000L) {
                    capture(child.name) { child.inputStream() }
                }
            }
        }
        visit(root, 0)
    } else {
        val treeUri = selectedTreeUri?.let(Uri::parse)
        val root = treeUri?.let { DocumentFile.fromTreeUri(context, it) }
        fun visit(node: DocumentFile, depth: Int) {
            if (depth > 6 || visitedFiles >= maxFiles || visitedNodes >= maxNodes || readChars >= maxTotalChars) return
            if (node.isDirectory) {
                runCatching { node.listFiles().forEach { child ->
                    if (++visitedNodes <= maxNodes) visit(child, depth + 1)
                } }
            } else if (node.isFile) {
                capture(node.name.orEmpty()) { context.contentResolver.openInputStream(node.uri) }
            }
        }
        root?.let { visit(it, 0) }
    }

    matches.sortedByDescending { it.first }.take(4).map { it.second }
}
