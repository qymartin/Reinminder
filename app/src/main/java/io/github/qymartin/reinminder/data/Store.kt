package io.github.qymartin.reinminder.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import java.io.File

/**
 * All events, kept in one JSON file in the app's private storage (no one else can read it, and it is
 * excluded from cloud backup). The list is small, so every change rewrites the file atomically.
 */
class EventStore private constructor(private val file: File) {
    private val state = MutableStateFlow(load())
    val events: StateFlow<List<Event>> = state

    private fun load(): List<Event> = try {
        if (file.exists()) Codec.listFromJson(JSONArray(file.readText())) else emptyList()
    } catch (e: Exception) {
        emptyList()
    }

    @Synchronized
    fun all(): List<Event> = state.value

    fun get(id: Long): Event? = state.value.firstOrNull { it.id == id }

    @Synchronized
    fun upsert(e: Event) {
        val list = state.value.toMutableList()
        val i = list.indexOfFirst { it.id == e.id }
        if (i >= 0) list[i] = e else list.add(e)
        save(list)
    }

    @Synchronized
    fun update(id: Long, change: (Event) -> Event): Event? {
        val old = get(id) ?: return null
        val new = change(old)
        upsert(new)
        return new
    }

    @Synchronized
    fun delete(id: Long) = save(state.value.filterNot { it.id == id })

    @Synchronized
    fun replaceAll(list: List<Event>) = save(list)

    /** Deletes "完成并删除" events completed before [today]. Returns true if any were removed. */
    @Synchronized
    fun purge(today: java.time.LocalDate): Boolean {
        val keep = state.value.filterNot { it.purge && it.done && (it.lastDoneOn?.isBefore(today) ?: true) }
        if (keep.size == state.value.size) return false
        save(keep)
        return true
    }

    @Synchronized
    fun newId(): Long {
        val max = state.value.maxOfOrNull { it.id } ?: 0L
        return maxOf(max + 1, System.currentTimeMillis())
    }

    private fun save(list: List<Event>) {
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(Codec.listToJson(list).toString())
        if (!tmp.renameTo(file)) {
            file.writeText(tmp.readText())
            tmp.delete()
        }
        state.value = list
    }

    companion object {
        @Volatile private var instance: EventStore? = null

        fun get(context: Context): EventStore = instance ?: synchronized(this) {
            instance ?: EventStore(File(context.applicationContext.filesDir, "events.json")).also { instance = it }
        }
    }
}

/** Small settings in private SharedPreferences. */
class Prefs(context: Context) {
    private val p = context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /** "zh" (default) or "en". */
    var lang: String
        get() = if (p.getString("lang", "zh") == "en") "en" else "zh"
        set(v) = p.edit().putString("lang", v).apply()

    var snoozeMinutes: Int
        get() = p.getInt("snoozeMinutes", 10)
        set(v) = p.edit().putInt("snoozeMinutes", v).apply()

    var encryptBackup: Boolean
        get() = p.getBoolean("encryptBackup", true)
        set(v) = p.edit().putBoolean("encryptBackup", v).apply()

    var lastExportAt: Long
        get() = p.getLong("lastExportAt", 0)
        set(v) = p.edit().putLong("lastExportAt", v).apply()

    var lastExportEncrypted: Boolean
        get() = p.getBoolean("lastExportEncrypted", true)
        set(v) = p.edit().putBoolean("lastExportEncrypted", v).apply()

    var askedNotificationPermission: Boolean
        get() = p.getBoolean("askedNotif", false)
        set(v) = p.edit().putBoolean("askedNotif", v).apply()

    /** Keys ("<eventId>/<index>") of reminder alarms currently set, so they can be cancelled. */
    var scheduled: Set<String>
        get() = p.getStringSet("scheduled", emptySet()) ?: emptySet()
        set(v) = p.edit().putStringSet("scheduled", v).apply()
}
