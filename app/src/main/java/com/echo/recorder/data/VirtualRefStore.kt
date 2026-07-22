package com.echo.recorder.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.echo.recorder.domain.model.Recording
import com.echo.recorder.domain.model.RecordingCategory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

/**
 * 公共目录虚引用的持久化存储.
 *
 * 虚引用文件位于 Downloads/EchoBackup, 不在 FilesystemRecordingDataSource 扫描范围内,
 * 故需单独持久化, 保证重启后仍在列表显示.
 */
private val Context.virtualRefStore by preferencesDataStore(name = "echo_virtual_refs")

class VirtualRefStore(private val context: Context) {

    private val key = stringPreferencesKey("refs_json")

    val refs: Flow<List<Recording>> = context.virtualRefStore.data.map { prefs ->
        parse(prefs[key])
    }

    suspend fun add(rec: Recording) {
        context.virtualRefStore.edit { prefs ->
            val current = parse(prefs[key])
            if (current.any { it.id == rec.id }) return@edit
            prefs[key] = serialize(current + rec)
        }
    }

    suspend fun remove(id: String) {
        context.virtualRefStore.edit { prefs ->
            val current = parse(prefs[key])
            val next = current.filterNot { it.id == id }
            if (next.size == current.size) return@edit
            prefs[key] = serialize(next)
        }
    }

    /** 是否已存在指向该文件路径的虚引用 (去重用). */
    suspend fun exists(fileUrl: String): Boolean =
        context.virtualRefStore.data.first().let { parse(it[key]) }.any { it.fileUrl == fileUrl }

    private fun parse(json: String?): List<Recording> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Recording(
                    id = o.getString("id"),
                    displayName = o.getString("name"),
                    fileUrl = o.getString("url"),
                    createdAt = o.getLong("ts"),
                    durationMs = o.getLong("dur"),
                    category = RecordingCategory.LONG_TERM,
                    isPublicVirtual = true,
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun serialize(list: List<Recording>): String {
        val arr = JSONArray()
        list.forEach { rec ->
            arr.put(JSONObject().apply {
                put("id", rec.id)
                put("name", rec.displayName)
                put("url", rec.fileUrl)
                put("ts", rec.createdAt)
                put("dur", rec.durationMs)
            })
        }
        return arr.toString()
    }
}
