package com.openkeyboardsam.feature.clipboard

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class ClipItem(
    val id: Long,
    val text: String,
    val timestampMs: Long,
    val isPinned: Boolean
)

/**
 * Ultra-lightweight persistent clipboard store using raw SQLiteOpenHelper
 * (no Room/ORM memory overhead) with strict cap on unpinned history.
 */
class ClipboardRepository(context: Context) :
    SQLiteOpenHelper(context.applicationContext, DB_NAME, null, DB_VERSION) {

    companion object {
        private const val DB_NAME = "openkeyboardsam_clip.db"
        private const val DB_VERSION = 1
        private const val TABLE_CLIPS = "clips"
        private const val MAX_UNPINNED_ITEMS = 40
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE_CLIPS (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                content TEXT NOT NULL UNIQUE,
                timestamp_ms INTEGER NOT NULL,
                is_pinned INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_CLIPS")
        onCreate(db)
    }

    fun saveClip(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return

        val db = writableDatabase
        val now = System.currentTimeMillis()
        val values = ContentValues().apply {
            put("content", trimmed)
            put("timestamp_ms", now)
        }
        val updated = db.update(TABLE_CLIPS, values, "content = ?", arrayOf(trimmed))
        if (updated == 0) {
            values.put("is_pinned", 0)
            db.insertWithOnConflict(TABLE_CLIPS, null, values, SQLiteDatabase.CONFLICT_REPLACE)
        }
        pruneOldClips(db)
    }

    fun getRecentClips(limit: Int = 30): List<ClipItem> {
        val result = mutableListOf<ClipItem>()
        val db = readableDatabase
        db.rawQuery(
            "SELECT id, content, timestamp_ms, is_pinned FROM $TABLE_CLIPS ORDER BY is_pinned DESC, timestamp_ms DESC LIMIT ?",
            arrayOf(limit.toString())
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result.add(
                    ClipItem(
                        id = cursor.getLong(0),
                        text = cursor.getString(1),
                        timestampMs = cursor.getLong(2),
                        isPinned = cursor.getInt(3) == 1
                    )
                )
            }
        }
        return result
    }

    fun togglePin(id: Long) {
        writableDatabase.execSQL(
            "UPDATE $TABLE_CLIPS SET is_pinned = CASE WHEN is_pinned = 1 THEN 0 ELSE 1 END WHERE id = ?",
            arrayOf(id)
        )
    }

    fun deleteClip(id: Long) {
        writableDatabase.delete(TABLE_CLIPS, "id = ?", arrayOf(id.toString()))
    }

    fun clearUnpinned() {
        writableDatabase.delete(TABLE_CLIPS, "is_pinned = 0", null)
    }

    private fun pruneOldClips(db: SQLiteDatabase) {
        db.execSQL(
            """
            DELETE FROM $TABLE_CLIPS
            WHERE is_pinned = 0 AND id NOT IN (
                SELECT id FROM $TABLE_CLIPS WHERE is_pinned = 0 ORDER BY timestamp_ms DESC LIMIT $MAX_UNPINNED_ITEMS
            )
            """.trimIndent()
        )
    }
}
