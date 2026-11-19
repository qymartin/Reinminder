package io.github.qymartin.reinminder.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import java.io.File

/** The user's topics (主题), in their own order, kept in a private JSON file next to the events. */
class TopicStore private constructor(private val file: File, seedEnglish: Boolean) {
    private val state = MutableStateFlow(load(seedEnglish))
    val topics: StateFlow<List<Topic>> = state

    private fun load(seedEnglish: Boolean): List<Topic> {
        if (!file.exists()) {
            // First start: the six topics of version 1.0 (their ids match 1.0's category keys).
            val seeded = defaults(seedEnglish)
            write(seeded)
            return seeded
        }
        return try {
            TopicCodec.listFromJson(JSONArray(file.readText()))
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun all(): List<Topic> = state.value

    fun name(id: String?): String? = id?.let { i -> state.value.firstOrNull { it.id == i }?.name }

    /** Topic with this name (case-insensitive), if any. */
    fun byName(name: String): Topic? = state.value.firstOrNull { it.name.equals(name.trim(), ignoreCase = true) }

    @Synchronized
    fun add(name: String): Topic {
        byName(name)?.let { return it }
        val t = Topic("t" + System.currentTimeMillis().toString(36), name.trim())
        save(state.value + t)
        return t
    }

    @Synchronized
    fun rename(id: String, name: String) = save(state.value.map { if (it.id == id) it.copy(name = name.trim()) else it })

    @Synchronized
    fun remove(id: String) = save(state.value.filterNot { it.id == id })

    @Synchronized
    fun replaceAll(list: List<Topic>) = save(list)

    private fun save(list: List<Topic>) {
        write(list)
        state.value = list
    }

    private fun write(list: List<Topic>) {
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(TopicCodec.listToJson(list).toString())
        if (!tmp.renameTo(file)) {
            file.writeText(tmp.readText())
            tmp.delete()
        }
    }

    companion object {
        @Volatile private var instance: TopicStore? = null

        fun get(context: Context): TopicStore = instance ?: synchronized(this) {
            instance ?: TopicStore(
                File(context.applicationContext.filesDir, "topics.json"),
                Lang.isEnglish(context),
            ).also { instance = it }
        }

        fun defaults(en: Boolean): List<Topic> = listOf(
            Topic("work", if (en) "Work" else "工作"),
            Topic("life", if (en) "Life" else "生活"),
            Topic("health", if (en) "Health" else "健康"),
            Topic("bills", if (en) "Bills" else "账单"),
            Topic("anniversary", if (en) "Anniversary" else "纪念日"),
            Topic("personal", if (en) "Personal" else "个人"),
        )
    }
}
