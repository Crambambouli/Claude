package com.puzzle.android.data.storage

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.puzzle.android.game.EdgeType
import com.puzzle.android.game.JigsawPiece
import com.puzzle.android.game.JigsawState
import com.puzzle.android.game.PieceDefinition
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object GameStorage {

    private const val STATE_FILE  = "puzzle_save.json"
    private const val BITMAP_FILE = "puzzle_save.png"

    fun save(context: Context, state: JigsawState, bitmap: Bitmap?) {
        if (bitmap != null) {
            File(context.filesDir, BITMAP_FILE).outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        }
        val pieces = JSONArray()
        state.pieces.forEach { p ->
            pieces.put(JSONObject().apply {
                put("id",       p.id)
                put("row",      p.definition.row)
                put("col",      p.definition.col)
                put("top",      p.definition.top.name)
                put("right",    p.definition.right.name)
                put("bottom",   p.definition.bottom.name)
                put("left",     p.definition.left.name)
                put("x",        p.x.toDouble())
                put("y",        p.y.toDouble())
                put("isPlaced", p.isPlaced)
                put("groupId",  if (p.groupId != null) p.groupId else JSONObject.NULL)
            })
        }
        val json = JSONObject()
        json.put("rows",   state.rows)
        json.put("cols",   state.cols)
        json.put("pieces", pieces)
        File(context.filesDir, STATE_FILE).writeText(json.toString())
    }

    fun load(context: Context): Pair<JigsawState, Bitmap?>? {
        val stateFile = File(context.filesDir, STATE_FILE)
        if (!stateFile.exists()) return null
        return try {
            val json   = JSONObject(stateFile.readText())
            val rows   = json.getInt("rows")
            val cols   = json.getInt("cols")
            val arr    = json.getJSONArray("pieces")
            val pieces = (0 until arr.length()).map { i ->
                val p = arr.getJSONObject(i)
                JigsawPiece(
                    id         = p.getInt("id"),
                    definition = PieceDefinition(
                        row    = p.getInt("row"),
                        col    = p.getInt("col"),
                        top    = EdgeType.valueOf(p.getString("top")),
                        right  = EdgeType.valueOf(p.getString("right")),
                        bottom = EdgeType.valueOf(p.getString("bottom")),
                        left   = EdgeType.valueOf(p.getString("left"))
                    ),
                    x        = p.getDouble("x").toFloat(),
                    y        = p.getDouble("y").toFloat(),
                    isPlaced = p.getBoolean("isPlaced"),
                    groupId  = if (p.isNull("groupId")) null else p.getInt("groupId")
                )
            }
            val state  = JigsawState(rows = rows, cols = cols, pieces = pieces)
            val bmpFile = File(context.filesDir, BITMAP_FILE)
            val bitmap  = if (bmpFile.exists()) BitmapFactory.decodeFile(bmpFile.absolutePath) else null
            Pair(state, bitmap)
        } catch (_: Exception) {
            null
        }
    }

    fun hasSave(context: Context): Boolean =
        File(context.filesDir, STATE_FILE).exists()

    fun delete(context: Context) {
        File(context.filesDir, STATE_FILE).delete()
        File(context.filesDir, BITMAP_FILE).delete()
    }
}
