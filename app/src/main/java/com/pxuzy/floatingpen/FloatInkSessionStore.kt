package com.pxuzy.floatingpen

import com.pxuzy.floatingpen.core.DrawingBoard
import com.pxuzy.floatingpen.core.DrawingElement
import com.pxuzy.floatingpen.core.DrawingLayer
import com.pxuzy.floatingpen.core.DrawingSession
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Versioned JSON payload used inside a .floatink session file. */
data class DecodedFloatInkSession(
    val sessionId: String,
    val session: DrawingSession,
)

object FloatInkSessionCodec {
    const val FORMAT_VERSION = 1

    fun encode(session: DrawingSession, sessionId: String): String {
        val root = JSONObject()
            .put("formatVersion", FORMAT_VERSION)
            .put("sessionId", sessionId)
            .put("activeBoardId", session.currentBoard.id)
            .put("activeLayerId", session.currentLayer.id)
        root.put("boards", JSONArray().apply {
            session.boards.forEach { board ->
                put(JSONObject()
                    .put("id", board.id)
                    .put("name", board.name)
                    .put("activeLayerId", board.activeLayerId)
                    .put("layers", JSONArray().apply {
                        board.layers.forEach { layer ->
                            put(JSONObject()
                                .put("id", layer.id)
                                .put("name", layer.name)
                                .put("visible", layer.visible)
                                .put("elements", encodeElements(layer.elements)))
                        }
                    }))
            }
        })
        return root.toString()
    }

    fun decode(payload: String): DecodedFloatInkSession {
        val root = JSONObject(payload)
        require(root.optInt("formatVersion") == FORMAT_VERSION) { "不支持的 FloatInk 文件版本" }
        val boards = root.getJSONArray("boards")
        require(boards.length() > 0) { "FloatInk 文件没有画板" }
        val decodedBoards = (0 until boards.length()).map { index ->
            decodeBoard(boards.getJSONObject(index))
        }
        val session = DrawingSession(decodedBoards.first())
        session.boards.clear()
        session.boards += decodedBoards
        val activeBoardId = root.optString("activeBoardId", session.boards.first().id)
        session.selectBoard(activeBoardId)
        val activeLayerId = root.optString("activeLayerId", session.currentBoard.activeLayerId)
        session.selectLayer(activeLayerId)
        return DecodedFloatInkSession(root.optString("sessionId", "imported"), session)
    }

    private fun encodeElements(elements: List<DrawingElement>) = JSONArray().apply {
        elements.forEach { element ->
            val json = JSONObject().put("color", element.drawColor).put("width", element.drawWidth)
            when (element) {
                is DrawingElement.Stroke -> json.put("type", "stroke").put("points", encodePoints(element.points))
                is DrawingElement.Line -> json.put("type", "line").put("start", encodePoint(element.start)).put("end", encodePoint(element.end))
                is DrawingElement.Arrow -> json.put("type", "arrow").put("start", encodePoint(element.start)).put("end", encodePoint(element.end)).put("headLengthDp", element.headLengthDp)
                is DrawingElement.Rect -> json.put("type", "rect").put("start", encodePoint(element.start)).put("end", encodePoint(element.end))
                is DrawingElement.Circle -> json.put("type", "circle").put("center", encodePoint(element.center)).put("radius", element.radius)
            }
            put(json)
        }
    }

    private fun decodeBoard(json: JSONObject): DrawingBoard {
        val layers = mutableListOf<DrawingLayer>()
        val layersJson = json.getJSONArray("layers")
        for (index in 0 until layersJson.length()) {
            val layerJson = layersJson.getJSONObject(index)
            layers += DrawingLayer(
                name = layerJson.getString("name"),
                id = layerJson.getString("id"),
                visible = layerJson.optBoolean("visible", true),
                elements = decodeElements(layerJson.getJSONArray("elements")),
            )
        }
        require(layers.isNotEmpty()) { "画板没有图层" }
        return DrawingBoard(
            name = json.getString("name"),
            id = json.getString("id"),
            layers = layers,
            activeLayerId = json.optString("activeLayerId", layers.first().id).takeIf { id -> layers.any { it.id == id } } ?: layers.first().id,
        )
    }

    private fun decodeElements(json: JSONArray): MutableList<DrawingElement> = mutableListOf<DrawingElement>().apply {
        for (index in 0 until json.length()) {
            val item = json.getJSONObject(index)
            val color = item.getInt("color")
            val width = item.getDouble("width").toFloat()
            when (item.getString("type")) {
                "stroke" -> add(DrawingElement.Stroke(decodePoints(item.getJSONArray("points")), color, width))
                "line" -> add(DrawingElement.Line(decodePoint(item.getJSONArray("start")), decodePoint(item.getJSONArray("end")), color, width))
                "arrow" -> add(DrawingElement.Arrow(decodePoint(item.getJSONArray("start")), decodePoint(item.getJSONArray("end")), color, width, item.getDouble("headLengthDp").toFloat()))
                "rect" -> add(DrawingElement.Rect(decodePoint(item.getJSONArray("start")), decodePoint(item.getJSONArray("end")), color, width))
                "circle" -> add(DrawingElement.Circle(decodePoint(item.getJSONArray("center")), item.getDouble("radius").toFloat(), color, width))
                else -> Unit
            }
        }
    }

    private fun encodePoint(point: Pair<Float, Float>) = JSONArray().put(point.first).put(point.second)
    private fun encodePoints(points: List<Pair<Float, Float>>) = JSONArray().apply { points.forEach { put(encodePoint(it)) } }
    private fun decodePoint(json: JSONArray) = json.getDouble(0).toFloat() to json.getDouble(1).toFloat()
    private fun decodePoints(json: JSONArray) = MutableList(json.length()) { decodePoint(json.getJSONArray(it)) }
}

object FloatInkSessionStore {
    data class LoadResult(val decoded: DecodedFloatInkSession, val recoveredFromBackup: Boolean)

    fun save(file: File, session: DrawingSession, sessionId: String) {
        file.parentFile?.mkdirs()
        val temp = File(file.path + ".tmp")
        if (file.exists()) file.copyTo(File(file.path + ".bak"), overwrite = true)
        temp.writeText(FloatInkSessionCodec.encode(session, sessionId), Charsets.UTF_8)
        if (file.exists() && !file.delete()) error("无法替换旧 FloatInk 文件")
        require(temp.renameTo(file)) { "无法完成 FloatInk 文件原子替换" }
    }

    fun load(file: File): DecodedFloatInkSession =
        FloatInkSessionCodec.decode(file.readText(Charsets.UTF_8))

    fun loadWithBackup(file: File): LoadResult {
        return runCatching { LoadResult(load(file), false) }.getOrElse { primaryError ->
            val backup = File(file.path + ".bak")
            if (!backup.exists()) throw primaryError
            val decoded = FloatInkSessionCodec.decode(backup.readText(Charsets.UTF_8))
            backup.copyTo(file, overwrite = true)
            LoadResult(decoded, true)
        }
    }
}
