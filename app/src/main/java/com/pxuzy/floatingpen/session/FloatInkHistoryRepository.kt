package com.pxuzy.floatingpen

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

 data class FloatInkHistoryEntry(
    val sessionId: String,
    val name: String,
    val file: File,
    val modifiedAt: Long,
    val sizeBytes: Long,
)

class FloatInkHistoryRepository(private val context: Context) {
    private val root get() = FloatInkStorage.rootDirectory(context)
    private val sessions get() = FloatInkStorage.sessionsDirectory(context)
    private val trash get() = File(root, "trash").also { it.mkdirs() }
    private val indexFile get() = File(root, "index.json")

    fun list(): List<FloatInkHistoryEntry> = listFiles(sessions)
    fun listTrash(): List<FloatInkHistoryEntry> = listFiles(trash)

    fun register(sessionId: String, name: String = sessionId) {
        val index = readIndex()
        index.put(sessionId, name.trim().ifEmpty { sessionId })
        writeIndex(index)
    }

    fun rename(sessionId: String, name: String): Boolean {
        val file = FloatInkStorage.sessionFile(context, sessionId)
        if (!file.exists()) return false
        register(sessionId, name)
        return true
    }

    fun delete(sessionId: String): Boolean {
        val source = FloatInkStorage.sessionFile(context, sessionId)
        if (!source.exists()) return false
        val target = File(trash, source.name)
        copySessionArtifacts(source, target, overwrite = true)
        deleteSessionArtifacts(source)
        return true
    }

    fun restore(sessionId: String): Boolean {
        val source = File(trash, "$sessionId.floatink")
        if (!source.exists()) return false
        val target = FloatInkStorage.sessionFile(context, sessionId)
        copySessionArtifacts(source, target, overwrite = true)
        deleteSessionArtifacts(source)
        return true
    }

    fun clearTrash() {
        trash.listFiles()?.forEach { it.delete() }
    }

    fun copy(sessionId: String): FloatInkHistoryEntry? {
        val source = FloatInkStorage.sessionFile(context, sessionId)
        if (!source.exists()) return null
        val newId = "session-${UUID.randomUUID()}"
        val target = FloatInkStorage.sessionFile(context, newId)
        copySessionArtifacts(source, target)
        val originalName = readIndex().optString(sessionId, sessionId)
        register(newId, "$originalName 副本")
        return list().first { it.sessionId == newId }
    }

    fun import(source: File): FloatInkHistoryEntry {
        require(source.isFile && source.extension == "floatink") { "请选择 .floatink 文件" }
        val decoded = FloatInkSessionCodec.decode(source.readText(Charsets.UTF_8))
        val newId = "session-${UUID.randomUUID()}"
        val target = FloatInkStorage.sessionFile(context, newId)
        target.writeText(FloatInkSessionCodec.encode(decoded.session, newId), Charsets.UTF_8)
        register(newId, decoded.sessionId.ifBlank { source.nameWithoutExtension })
        return list().first { it.sessionId == newId }
    }

    private fun listFiles(directory: File): List<FloatInkHistoryEntry> {
        val index = readIndex()
        return directory.listFiles()
            ?.filter { it.isFile && it.extension == "floatink" }
            ?.map { file ->
                val id = file.nameWithoutExtension
                FloatInkHistoryEntry(id, index.optString(id, id), file, file.lastModified(), file.length())
            }
            ?.sortedByDescending { it.modifiedAt }
            ?: emptyList()
    }

    private fun copySessionArtifacts(source: File, target: File, overwrite: Boolean = false) {
        source.copyTo(target, overwrite = overwrite)
        File(source.path + ".bak")
            .takeIf(File::exists)
            ?.copyTo(File(target.path + ".bak"), overwrite = overwrite)
    }

    private fun deleteSessionArtifacts(file: File) {
        file.delete()
        File(file.path + ".bak").delete()
    }

    private fun readIndex(): JSONObject = runCatching {
        if (indexFile.exists()) JSONObject(indexFile.readText(Charsets.UTF_8)) else JSONObject()
    }.getOrDefault(JSONObject())

    private fun writeIndex(index: JSONObject) {
        val temp = File(indexFile.path + ".tmp")
        temp.writeText(index.toString(), Charsets.UTF_8)
        if (indexFile.exists()) indexFile.delete()
        require(temp.renameTo(indexFile)) { "无法保存历史索引" }
    }
}
