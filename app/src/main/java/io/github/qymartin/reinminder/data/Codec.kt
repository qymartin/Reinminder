package io.github.qymartin.reinminder.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** JSON form of events, used for the app's own file and for backups. Unknown keys are ignored. */
object Codec {
    fun toJson(e: Event): JSONObject = JSONObject().apply {
        put("id", e.id)
        put("title", e.title)
        e.topic?.let { put("topic", it) }
        put("anchor", e.anchor.toString())
        put("date", e.date.toString())
        e.time?.let { put("time", it.toString()) }
        put("repeat", e.repeat.key)
        put("interval", e.interval)
        put("reminders", JSONArray(e.reminders))
        put("important", e.important)
        put("alarmStyle", e.alarmStyle)
        put("notes", e.notes)
        put("done", e.done)
        put("purge", e.purge)
        e.lastDoneOn?.let { put("lastDoneOn", it.toString()) }
        e.lastDoneTime?.let { put("lastDoneTime", it.toString()) }
        e.postponedTo?.let { put("postponedTo", it.toString()) }
        put("createdAt", e.createdAt)
    }

    fun fromJson(o: JSONObject): Event? = try {
        val date = LocalDate.parse(o.getString("date"))
        val rem = o.optJSONArray("reminders")
        Event(
            id = o.getLong("id"),
            title = o.optString("title", ""),
            // 1.0 stored a fixed category key; those keys are the ids of the seeded topics.
            topic = o.optString("topic", "").ifEmpty { o.optString("category", "") }.ifEmpty { null },
            anchor = o.optString("anchor", "").ifEmpty { null }?.let { LocalDate.parse(it) } ?: date,
            date = date,
            time = o.optString("time", "").ifEmpty { null }?.let { LocalTime.parse(it) },
            repeat = Repeat.of(o.optString("repeat", "none")),
            interval = o.optInt("interval", 1).coerceIn(1, 999),
            reminders = if (rem == null) emptyList() else (0 until rem.length()).map { rem.getInt(it) }.take(MAX_REMINDERS),
            important = o.optBoolean("important", false),
            alarmStyle = o.optBoolean("alarmStyle", false),
            notes = o.optString("notes", ""),
            done = o.optBoolean("done", false),
            purge = o.optBoolean("purge", false),
            lastDoneOn = o.optString("lastDoneOn", "").ifEmpty { null }?.let { LocalDate.parse(it) },
            lastDoneTime = o.optString("lastDoneTime", "").ifEmpty { null }?.let { LocalTime.parse(it) },
            postponedTo = o.optString("postponedTo", "").ifEmpty { null }?.let { LocalDateTime.parse(it) },
            createdAt = o.optLong("createdAt", 0),
        )
    } catch (e: Exception) {
        null
    }

    fun listToJson(list: List<Event>): JSONArray = JSONArray().apply { list.forEach { put(toJson(it)) } }

    fun listFromJson(a: JSONArray): List<Event> = (0 until a.length()).mapNotNull { i -> a.optJSONObject(i)?.let { fromJson(it) } }
}

object TopicCodec {
    fun listToJson(list: List<Topic>): JSONArray = JSONArray().apply {
        list.forEach { put(JSONObject().put("id", it.id).put("name", it.name)) }
    }

    fun listFromJson(a: JSONArray): List<Topic> = (0 until a.length()).mapNotNull { i ->
        val o = a.optJSONObject(i) ?: return@mapNotNull null
        val id = o.optString("id", "")
        val name = o.optString("name", "")
        if (id.isEmpty() || name.isEmpty()) null else Topic(id, name)
    }
}
