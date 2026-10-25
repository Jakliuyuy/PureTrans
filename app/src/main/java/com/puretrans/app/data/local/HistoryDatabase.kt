package com.puretrans.app.data.local

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.puretrans.app.data.model.HistoryItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class HistoryDatabase(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    private val _itemsFlow = MutableStateFlow<List<HistoryItem>>(emptyList())
    val itemsFlow: StateFlow<List<HistoryItem>> = _itemsFlow.asStateFlow()

    init {
        refreshFlow()
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE_NAME (
                id INTEGER PRIMARY KEY,
                source_text TEXT NOT NULL,
                translated_text TEXT NOT NULL,
                source_lang TEXT NOT NULL,
                target_lang TEXT NOT NULL,
                engine_label TEXT NOT NULL,
                timestamp INTEGER NOT NULL,
                is_favorite INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_NAME")
        onCreate(db)
    }

    fun refreshFlow() {
        val list = getAllItemsInternal()
        _itemsFlow.value = list
    }

    private fun getAllItemsInternal(): List<HistoryItem> {
        val list = mutableListOf<HistoryItem>()
        val db = readableDatabase
        val cursor = db.query(
            TABLE_NAME,
            null,
            null,
            null,
            null,
            null,
            "timestamp DESC",
            "500"
        )
        cursor.use {
            while (it.moveToNext()) {
                val id = it.getLong(it.getColumnIndexOrThrow("id"))
                val src = it.getString(it.getColumnIndexOrThrow("source_text"))
                val dst = it.getString(it.getColumnIndexOrThrow("translated_text"))
                val srcLang = it.getString(it.getColumnIndexOrThrow("source_lang"))
                val dstLang = it.getString(it.getColumnIndexOrThrow("target_lang"))
                val engine = it.getString(it.getColumnIndexOrThrow("engine_label"))
                val ts = it.getLong(it.getColumnIndexOrThrow("timestamp"))
                val fav = it.getInt(it.getColumnIndexOrThrow("is_favorite")) == 1

                list.add(
                    HistoryItem(
                        id = id,
                        sourceText = src,
                        translatedText = dst,
                        sourceLangCode = srcLang,
                        targetLangCode = dstLang,
                        engineLabel = engine,
                        timestamp = ts,
                        isFavorite = fav
                    )
                )
            }
        }
        return list
    }

    suspend fun insertOrUpdate(item: HistoryItem) = withContext(Dispatchers.IO) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("id", item.id)
            put("source_text", item.sourceText)
            put("translated_text", item.translatedText)
            put("source_lang", item.sourceLangCode)
            put("target_lang", item.targetLangCode)
            put("engine_label", item.engineLabel)
            put("timestamp", item.timestamp)
            put("is_favorite", if (item.isFavorite) 1 else 0)
        }
        db.insertWithOnConflict(TABLE_NAME, null, values, SQLiteDatabase.CONFLICT_REPLACE)
        refreshFlow()
    }

    suspend fun toggleFavorite(sourceText: String, targetText: String): Boolean = withContext(Dispatchers.IO) {
        val db = writableDatabase
        val newState: Boolean
        // 查询最近的一条匹配记录
        val cursor = db.rawQuery(
            "SELECT id, is_favorite FROM $TABLE_NAME WHERE source_text = ? AND translated_text = ? ORDER BY timestamp DESC LIMIT 1",
            arrayOf(sourceText, targetText)
        )
        cursor.use {
            if (it.moveToFirst()) {
                val id = it.getLong(0)
                val currentFav = it.getInt(1) == 1
                newState = !currentFav
                val values = ContentValues().apply {
                    put("is_favorite", if (newState) 1 else 0)
                }
                db.update(TABLE_NAME, values, "id = ?", arrayOf(id.toString()))
            } else {
                // 不存在则插入
                val values = ContentValues().apply {
                    put("id", System.currentTimeMillis())
                    put("source_text", sourceText)
                    put("translated_text", targetText)
                    put("source_lang", "auto")
                    put("target_lang", "zh")
                    put("engine_label", "PureTrans")
                    put("timestamp", System.currentTimeMillis())
                    put("is_favorite", 1)
                }
                db.insert(TABLE_NAME, null, values)
                newState = true
            }
        }
        refreshFlow()
        newState
    }

    suspend fun deleteItem(id: Long) = withContext(Dispatchers.IO) {
        val db = writableDatabase
        db.delete(TABLE_NAME, "id = ?", arrayOf(id.toString()))
        refreshFlow()
    }

    suspend fun clearHistory(onlyNonFavorites: Boolean = true) = withContext(Dispatchers.IO) {
        val db = writableDatabase
        if (onlyNonFavorites) {
            db.delete(TABLE_NAME, "is_favorite = 0", null)
        } else {
            db.delete(TABLE_NAME, null, null)
        }
        refreshFlow()
    }

    companion object {
        private const val DATABASE_NAME = "puretrans_history.db"
        private const val DATABASE_VERSION = 1
        private const val TABLE_NAME = "translation_history"

        @Volatile
        private var instance: HistoryDatabase? = null

        fun getInstance(context: Context): HistoryDatabase {
            return instance ?: synchronized(this) {
                instance ?: HistoryDatabase(context.applicationContext).also { instance = it }
            }
        }
    }
}
