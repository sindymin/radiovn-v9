package vn.radio.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class Station(
    val id: String, val name: String, val url: String,
    val tags: String = "", val city: String = "", val hls: Boolean = false
)

data class RecTimer(
    val id: Long, val stationId: String, val stationName: String, val url: String,
    val hour: Int, val minute: Int, val durationMin: Int,
    val days: Int, val silent: Boolean, val enabled: Boolean = true
)

data class Prog(val id: Long, val stationName: String, val time: String, val title: String)

object Store {
    private fun sp(c: Context) = c.getSharedPreferences("radio", Context.MODE_PRIVATE)

    private fun <T> read(c: Context, k: String, f: (JSONObject) -> T): List<T> {
        val a = JSONArray(sp(c).getString(k, "[]"))
        return (0 until a.length()).map { f(a.getJSONObject(it)) }
    }

    private fun <T> write(c: Context, k: String, l: List<T>, f: (T) -> JSONObject) {
        val a = JSONArray()
        l.forEach { a.put(f(it)) }
        sp(c).edit().putString(k, a.toString()).apply()
    }

    private fun stFrom(o: JSONObject) = Station(
        o.getString("id"), o.getString("name"), o.getString("url"),
        o.optString("tags"), o.optString("city"), o.optBoolean("hls")
    )

    private fun stTo(s: Station) = JSONObject().put("id", s.id).put("name", s.name)
        .put("url", s.url).put("tags", s.tags).put("city", s.city).put("hls", s.hls)

    private var builtinCache: List<Station>? = null

    /** Danh sách đài có sẵn trong app (assets/stations.json). */
    private fun builtin(c: Context): List<Station> {
        builtinCache?.let { return it }
        val l = try {
            val a = JSONArray(c.assets.open("stations.json").bufferedReader().use { it.readText() })
            (0 until a.length()).map { stFrom(a.getJSONObject(it)) }
        } catch (e: Exception) {
            emptyList()
        }
        builtinCache = l
        return l
    }

    fun stations(c: Context): List<Station> {
        val seen = HashSet<String>()
        return (read(c, "custom") { stFrom(it) } + builtin(c) + read(c, "fetched") { stFrom(it) })
            .filter { seen.add(it.id) }
    }

    fun saveFetched(c: Context, l: List<Station>) = write(c, "fetched", l) { stTo(it) }
    fun addCustom(c: Context, s: Station) =
        write(c, "custom", read(c, "custom") { stFrom(it) } + s) { stTo(it) }

    fun favs(c: Context): Set<String> = sp(c).getStringSet("favs", emptySet())!!.toSet()
    fun toggleFav(c: Context, id: String) {
        val s = favs(c).toMutableSet()
        if (!s.add(id)) s.remove(id)
        sp(c).edit().putStringSet("favs", s).apply()
    }

    fun timers(c: Context): List<RecTimer> = read(c, "timers") {
        RecTimer(
            it.getLong("id"), it.getString("sid"), it.getString("sname"), it.getString("url"),
            it.getInt("h"), it.getInt("m"), it.getInt("dur"), it.getInt("days"),
            it.getBoolean("silent"), it.getBoolean("on")
        )
    }

    fun saveTimers(c: Context, l: List<RecTimer>) = write(c, "timers", l) {
        JSONObject().put("id", it.id).put("sid", it.stationId).put("sname", it.stationName)
            .put("url", it.url).put("h", it.hour).put("m", it.minute).put("dur", it.durationMin)
            .put("days", it.days).put("silent", it.silent).put("on", it.enabled)
    }

    fun progs(c: Context): List<Prog> = read(c, "progs") {
        Prog(it.getLong("id"), it.getString("st"), it.getString("time"), it.getString("title"))
    }

    fun saveProgs(c: Context, l: List<Prog>) = write(c, "progs", l) {
        JSONObject().put("id", it.id).put("st", it.stationName).put("time", it.time).put("title", it.title)
    }
}

object Api {
    private val hosts = listOf("de2", "de1", "all")

    /** Lấy danh sách đài Việt Nam từ thư mục công khai Radio Browser. */
    fun fetch(): List<Station> {
        var err: Exception? = null
        for (h in hosts) {
            try {
                val c = openStream(
                    "https://$h.api.radio-browser.info/json/stations/bycountrycodeexact/VN" +
                        "?hidebroken=true&order=clickcount&reverse=true&limit=1000", 20000
                )
                val txt = c.inputStream.bufferedReader().readText()
                c.disconnect()
                val a = JSONArray(txt)
                val out = ArrayList<Station>()
                for (i in 0 until a.length()) {
                    val o = a.getJSONObject(i)
                    val u = o.optString("url_resolved").ifEmpty { o.optString("url") }
                    if (u.isEmpty()) continue
                    out.add(
                        Station(
                            o.getString("stationuuid"), o.optString("name").trim(), u,
                            o.optString("tags"), o.optString("state"), o.optInt("hls") == 1
                        )
                    )
                }
                if (out.isNotEmpty()) return out
            } catch (e: Exception) {
                err = Exception("${err?.message ?: ""}\n$h: ${e.message}")
            }
        }
        throw java.io.IOException(err?.message ?: "Không có dữ liệu")
    }
}
