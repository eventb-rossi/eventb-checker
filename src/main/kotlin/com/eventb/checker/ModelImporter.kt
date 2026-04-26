package com.eventb.checker

import com.eventb.checker.xml.XmlConstants
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.util.zip.ZipFile

internal fun normalizeModelPath(path: String): String = path.replace('\\', '/')

class ModelEntry(path: String, val data: ByteArray) {
    val path: String = normalizeModelPath(path)

    fun inputStream(): InputStream = ByteArrayInputStream(data)
}

data class ModelContents(
    val machines: List<ModelEntry>,
    val contexts: List<ModelEntry>,
    val eventbFiles: List<ModelEntry> = emptyList(),
    val proofFiles: List<ModelEntry> = emptyList(),
    val proofObligationFiles: List<ModelEntry> = emptyList(),
    val proofStatusFiles: List<ModelEntry> = emptyList(),
)

class ModelImporter {

    companion object {
        const val EXT_EVENTB = ".eventb"
    }

    fun import(modelPath: String): ModelContents {
        val file = File(modelPath)
        require(file.exists()) { "File not found: $modelPath" }

        return when {
            file.isDirectory -> importDirectory(file)
            file.extension == "zip" -> importZip(file)
            file.extension == "eventb" -> importSingleEventbFile(file)
            else -> throw IllegalArgumentException("Expected a .zip file, directory, or .eventb file: $modelPath")
        }
    }

    private fun importZip(file: File): ModelContents {
        val acc = ModelContentsAccumulator()

        ZipFile(file).use { zip ->
            val entries = mutableListOf<java.util.zip.ZipEntry>()
            val enumeration = zip.entries()
            while (enumeration.hasMoreElements()) {
                val entry = enumeration.nextElement()
                if (!entry.isDirectory) {
                    entries.add(entry)
                }
            }

            for (entry in entries.sortedBy { normalizeModelPath(it.name) }) {
                val data = zip.getInputStream(entry).use { it.readAllBytes() }
                acc.categorize(entry.name, data)
            }
        }

        return acc.build()
    }

    private fun importSingleEventbFile(file: File): ModelContents {
        val parentName = file.parentFile?.name ?: "unknown"
        val entryPath = normalizeModelPath("$parentName/${file.name}")
        val data = file.readBytes()
        return ModelContents(
            machines = emptyList(),
            contexts = emptyList(),
            eventbFiles = listOf(ModelEntry(entryPath, data)),
        )
    }

    private fun importDirectory(dir: File): ModelContents {
        val acc = ModelContentsAccumulator()
        val dirName = dir.name

        dir.walkTopDown()
            .filter { it.isFile }
            .sortedBy { normalizeModelPath(it.relativeTo(dir).path) }
            .forEach { file ->
                val relativePath = normalizeModelPath(file.relativeTo(dir).path)
                val entryPath = normalizeModelPath("$dirName/$relativePath")
                acc.categorize(entryPath, file.readBytes())
            }

        return acc.build()
    }

    private class ModelContentsAccumulator {
        val machines = mutableListOf<ModelEntry>()
        val contexts = mutableListOf<ModelEntry>()
        val eventbFiles = mutableListOf<ModelEntry>()
        val proofFiles = mutableListOf<ModelEntry>()
        val proofObligationFiles = mutableListOf<ModelEntry>()
        val proofStatusFiles = mutableListOf<ModelEntry>()

        fun categorize(path: String, data: ByteArray) {
            val normalizedPath = normalizeModelPath(path)
            val ext = normalizedPath.substringAfterLast('.', "").let { ".$it" }
            when (ext) {
                XmlConstants.EXT_MACHINE -> machines.add(ModelEntry(normalizedPath, data))
                XmlConstants.EXT_CONTEXT -> contexts.add(ModelEntry(normalizedPath, data))
                EXT_EVENTB -> eventbFiles.add(ModelEntry(normalizedPath, data))
                XmlConstants.EXT_PROOF -> proofFiles.add(ModelEntry(normalizedPath, data))
                XmlConstants.EXT_PROOF_OBLIGATIONS -> proofObligationFiles.add(ModelEntry(normalizedPath, data))
                XmlConstants.EXT_PROOF_STATUS -> proofStatusFiles.add(ModelEntry(normalizedPath, data))
                else -> Unit
            }
        }

        fun build() = ModelContents(machines, contexts, eventbFiles, proofFiles, proofObligationFiles, proofStatusFiles)
    }
}
