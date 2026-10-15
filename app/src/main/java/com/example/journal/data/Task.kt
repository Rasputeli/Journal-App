package com.example.journal.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

data class Task(
    val id: Long,
    val title: String,
    val done: Boolean = false,
    val completedAt: Long? = null,
    val dueDate: LocalDate? = null,
    val dueTimeMinutes: Int? = null,
    val note: String = "",
    val notebookId: Long = NotebookSet.DEFAULT_ID,
    val createdAt: Long,
    val updatedAt: Long,
    val tags: List<String> = emptyList(),
)

object TaskCodec {

    fun encode(tasks: List<Task>): String {
        val array = JSONArray()
        tasks.forEach { task ->
            val item = JSONObject()
            item.put("id", task.id)
            item.put("title", task.title)
            item.put("done", task.done)
            task.completedAt?.let { item.put("completedAt", it) }
            task.dueDate?.let { item.put("dueDate", it.toString()) }
            task.dueTimeMinutes?.let { item.put("dueTime", it) }
            if (task.note.isNotBlank()) item.put("note", task.note)
            item.put("notebook", task.notebookId)
            item.put("createdAt", task.createdAt)
            item.put("updatedAt", task.updatedAt)
            array.put(item)
        }
        return array.toString()
    }

    fun decode(json: String): List<Task> = runCatching {
        val array = JSONArray(json)
        val out = mutableListOf<Task>()
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val title = item.optString("title")
            if (title.isBlank()) continue

            val due: LocalDate? = if (item.has("dueDate") && !item.isNull("dueDate")) {
                runCatching { LocalDate.parse(item.optString("dueDate")) }.getOrNull()
            } else null

            val completed: Long? = if (item.has("completedAt") && !item.isNull("completedAt")) {
                item.optLong("completedAt")
            } else null

            val dueTime: Int? = if (item.has("dueTime") && !item.isNull("dueTime")) {
                item.optInt("dueTime")
            } else null

            out += Task(
                id = item.optLong("id", 0L),
                title = title,
                done = item.optBoolean("done", false),
                completedAt = completed,
                dueDate = due,
                dueTimeMinutes = dueTime,
                note = item.optString("note"),
                notebookId = item.optLong("notebook", NotebookSet.DEFAULT_ID),
                createdAt = item.optLong("createdAt", 0L),
                updatedAt = item.optLong("updatedAt", 0L),
            )
        }
        out.filter { it.id > 0L }
    }.getOrDefault(emptyList())
}
