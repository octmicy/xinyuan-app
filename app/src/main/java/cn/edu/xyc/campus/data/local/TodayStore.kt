package cn.edu.xyc.campus.data.local

import android.content.Context
import cn.edu.xyc.campus.data.model.Course
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 桌面「今日课程」小组件的数据源：
 * 把课表页已加载的周课表落到 filesDir 下的 JSON（原子写），
 * 小组件在 App 进程被杀后仍可读取渲染。
 *
 * 只保留一个学期（当前学期）的数据：termKey 不匹配时整体重置，
 * 避免用户在 App 里翻历史学期把小组件数据带偏。
 * 落盘失败静默忽略——只影响小组件新鲜度，不影响 App 功能。
 */
object TodayStore {

    data class StoredWeek(val zs: Int, val monday: String, val courses: List<Course>)

    data class Snapshot(
        val termKey: String,
        val termLabel: String,
        val updatedMs: Long,
        val weeks: List<StoredWeek>,
    )

    private const val FILE = "widget_schedule.json"

    private fun file(context: Context): File = File(context.filesDir, FILE)

    /** 追加/覆盖一周课表。monday 取 cxZc 周次列表的 rq，可能是 "yyyy-MM-dd" 或 "yyyy-MM-dd/yyyy-MM-dd" 整周区间。 */
    fun upsertWeek(
        context: Context,
        termKey: String,
        termLabel: String,
        zs: Int,
        monday: String,
        courses: List<Course>,
    ) {
        runCatching {
            val snap = read(context)
            val base = if (snap != null && snap.termKey == termKey) snap
            else Snapshot(termKey, termLabel, 0L, emptyList())
            val weeks = base.weeks.filterNot { it.zs == zs } + StoredWeek(zs, monday, courses)

            val carr = JSONArray()
            weeks.sortedBy { it.zs }.forEach { w ->
                val cs = JSONArray()
                w.courses.forEach { c ->
                    cs.put(
                        JSONObject()
                            .put("n", c.name)
                            .put("t", c.teacher)
                            .put("r", c.room)
                            .put("d", c.dayOfWeek)
                            .put("s", c.startSection)
                            .put("e", c.endSection),
                    )
                }
                carr.put(
                    JSONObject().put("zs", w.zs).put("monday", w.monday).put("courses", cs),
                )
            }
            val obj = JSONObject()
                .put("termKey", termKey)
                .put("termLabel", termLabel)
                .put("updatedMs", System.currentTimeMillis())
                .put("weeks", carr)

            val tmp = File(context.filesDir, "$FILE.tmp")
            tmp.writeText(obj.toString())
            if (!tmp.renameTo(file(context))) {
                file(context).writeText(obj.toString())
                tmp.delete()
            }
        }
    }

    fun read(context: Context): Snapshot? = runCatching {
        val f = file(context)
        if (!f.exists()) return@runCatching null
        val obj = JSONObject(f.readText())
        val weeks = mutableListOf<StoredWeek>()
        val arr = obj.optJSONArray("weeks") ?: JSONArray()
        for (i in 0 until arr.length()) {
            val w = arr.optJSONObject(i) ?: continue
            val cs = mutableListOf<Course>()
            val courseArr = w.optJSONArray("courses") ?: JSONArray()
            for (j in 0 until courseArr.length()) {
                val c = courseArr.optJSONObject(j) ?: continue
                cs.add(
                    Course(
                        name = c.optString("n"),
                        teacher = c.optString("t"),
                        room = c.optString("r"),
                        building = "",
                        dayOfWeek = c.optInt("d", 1),
                        startSection = c.optInt("s", 1),
                        endSection = c.optInt("e", 1),
                        weekText = "",
                        credit = "",
                        nature = "",
                        classGroup = "",
                    ),
                )
            }
            weeks.add(StoredWeek(w.optInt("zs", 0), w.optString("monday"), cs))
        }
        Snapshot(
            termKey = obj.optString("termKey"),
            termLabel = obj.optString("termLabel"),
            updatedMs = obj.optLong("updatedMs", 0L),
            weeks = weeks,
        )
    }.getOrNull()

    /** 退出登录时清空小组件数据 */
    fun clear(context: Context) {
        runCatching { file(context).delete() }
    }
}
