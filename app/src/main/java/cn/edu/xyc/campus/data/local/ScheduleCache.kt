package cn.edu.xyc.campus.data.local

import android.content.Context
import androidx.compose.runtime.mutableStateMapOf
import cn.edu.xyc.campus.data.model.Course
import cn.edu.xyc.campus.data.model.GradeItem
import cn.edu.xyc.campus.data.model.StudentInfo
import cn.edu.xyc.campus.data.model.ThirdApp
import cn.edu.xyc.campus.data.model.WeekInfo
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 课表缓存：内存（Compose 状态，驱动 UI）+ 磁盘双层。
 * 磁盘层解决"用户杀后台后缓存全丢、重开要等网络"的问题：
 * init() 启动时从 filesDir 载入，之后界面秒开；由调用方走"后台比对"保证新鲜度。
 *
 * 持久化范围：周次列表 / 每周课表 / 学期总表（成绩、学籍、应用列表为启动即预取的轻数据，不落盘）。
 * 写入用 putXxx 助手（置内存 + 防抖异步落盘），不要直接 set map。
 */
object ScheduleCache {

    /** 周次列表：key = "W:xnm-xqm" */
    val weeksList = mutableStateMapOf<String, List<WeekInfo>>()

    /** 每周课表：key = "D:xnm-xqm-zs" → (课程, 学籍) */
    val weekData = mutableStateMapOf<String, Pair<List<Course>, StudentInfo>>()

    /** 成绩：key = "G:xnm-xqm" */
    val gradeData = mutableStateMapOf<String, List<GradeItem>>()

    /** 学籍卡：key = "PROFILE" */
    val profileData = mutableStateMapOf<String, cn.edu.xyc.campus.data.model.ProfileCard>()

    /** 门户第三方应用列表：key = "APPS" */
    val applications = mutableStateMapOf<String, List<ThirdApp>>()

    /** 学期整表（全部课程）：key = "T:xnm-xqm" */
    val termSchedule = mutableStateMapOf<String, List<Course>>()

    private val inFlight = mutableSetOf<String>()

    private const val FILE = "schedule_cache.json"
    private var appContext: Context? = null
    private val saveExecutor = Executors.newSingleThreadExecutor()
    private val savePending = AtomicBoolean(false)

    fun weeksKey(xnm: String, xqm: String) = "W:$xnm-$xqm"
    fun weekKey(xnm: String, xqm: String, zs: Int) = "D:$xnm-$xqm-$zs"
    fun gradeKey(xnm: String, xqm: String) = "G:$xnm-$xqm"
    fun termKey(xnm: String, xqm: String) = "T:$xnm-$xqm"

    /** MainActivity 启动时调用：载入磁盘缓存 */
    fun init(context: Context) {
        appContext = context.applicationContext
        loadFromDisk()
    }

    @Synchronized
    fun tryMark(key: String): Boolean =
        if (key in inFlight) false else {
            inFlight += key
            true
        }

    @Synchronized
    fun unmark(key: String) {
        inFlight -= key
    }

    fun putWeeks(key: String, list: List<WeekInfo>) {
        weeksList[key] = list
        requestSave()
    }

    fun putWeekData(key: String, data: Pair<List<Course>, StudentInfo>) {
        weekData[key] = data
        requestSave()
    }

    fun putTermSchedule(key: String, list: List<Course>) {
        termSchedule[key] = list
        requestSave()
    }

    fun clear() {
        weeksList.clear()
        weekData.clear()
        gradeData.clear()
        profileData.clear()
        applications.clear()
        termSchedule.clear()
        inFlight.clear()
        appContext?.let { runCatching { File(it.filesDir, FILE).delete() } }
    }

    // ---------- 磁盘层 ----------

    private fun requestSave() {
        val ctx = appContext ?: return
        if (!savePending.compareAndSet(false, true)) return
        saveExecutor.execute {
            try {
                Thread.sleep(300) // 防抖：连续写入只落一次盘
            } catch (_: InterruptedException) {
            }
            savePending.set(false)
            writeSnapshot(ctx)
        }
    }

    private fun writeSnapshot(context: Context) {
        runCatching {
            val weeks = JSONArray()
            weeksList.forEach { (key, list) ->
                val items = JSONArray()
                list.forEach { items.put(JSONObject().put("zs", it.zs).put("rq", it.rq)) }
                weeks.put(JSONObject().put("k", key).put("items", items))
            }
            val days = JSONArray()
            weekData.forEach { (key, pair) ->
                val student = pair.second
                val courses = JSONArray()
                pair.first.forEach { courses.put(courseJson(it)) }
                days.put(
                    JSONObject()
                        .put("k", key)
                        .put("student", JSONObject()
                            .put("n", student.name)
                            .put("i", student.studentId)
                            .put("c", student.className)
                            .put("m", student.major)
                            .put("g", student.gradeYear))
                        .put("courses", courses),
                )
            }
            val term = JSONArray()
            termSchedule.forEach { (key, list) ->
                val courses = JSONArray()
                list.forEach { courses.put(courseJson(it)) }
                term.put(JSONObject().put("k", key).put("courses", courses))
            }
            val obj = JSONObject().put("weeks", weeks).put("days", days).put("term", term)
            val f = File(context.filesDir, FILE)
            val tmp = File(context.filesDir, "$FILE.tmp")
            tmp.writeText(obj.toString())
            if (!tmp.renameTo(f)) {
                f.writeText(obj.toString())
                tmp.delete()
            }
        }
    }

    private fun courseJson(c: Course) = JSONObject()
        .put("n", c.name).put("t", c.teacher).put("r", c.room).put("b", c.building)
        .put("d", c.dayOfWeek).put("s", c.startSection).put("e", c.endSection)
        .put("w", c.weekText).put("xf", c.credit).put("xz", c.nature).put("jx", c.classGroup)

    private fun loadFromDisk() {
        val ctx = appContext ?: return
        runCatching {
            val f = File(ctx.filesDir, FILE)
            if (!f.exists()) return
            val obj = JSONObject(f.readText())

            obj.optJSONArray("weeks")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val key = o.optString("k")
                    val items = o.optJSONArray("items") ?: continue
                    val list = mutableListOf<WeekInfo>()
                    for (j in 0 until items.length()) {
                        val it2 = items.optJSONObject(j) ?: continue
                        list.add(WeekInfo(zs = it2.optInt("zs", 0), rq = it2.optString("rq")))
                    }
                    if (key.isNotEmpty() && list.isNotEmpty()) weeksList[key] = list
                }
            }
            obj.optJSONArray("days")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val key = o.optString("k")
                    val st = o.optJSONObject("student") ?: continue
                    val coursesArr = o.optJSONArray("courses") ?: continue
                    val student = StudentInfo(
                        name = st.optString("n"),
                        studentId = st.optString("i"),
                        className = st.optString("c"),
                        major = st.optString("m"),
                        gradeYear = st.optString("g"),
                    )
                    val courses = mutableListOf<Course>()
                    for (j in 0 until coursesArr.length()) {
                        val c = coursesArr.optJSONObject(j) ?: continue
                        courses.add(
                            Course(
                                name = c.optString("n"), teacher = c.optString("t"),
                                room = c.optString("r"), building = c.optString("b"),
                                dayOfWeek = c.optInt("d", 1),
                                startSection = c.optInt("s", 1), endSection = c.optInt("e", 1),
                                weekText = c.optString("w"), credit = c.optString("xf"),
                                nature = c.optString("xz"), classGroup = c.optString("jx"),
                            ),
                        )
                    }
                    if (key.isNotEmpty() && courses.isNotEmpty()) weekData[key] = courses to student
                }
            }
            obj.optJSONArray("term")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val key = o.optString("k")
                    val coursesArr = o.optJSONArray("courses") ?: continue
                    val courses = mutableListOf<Course>()
                    for (j in 0 until coursesArr.length()) {
                        val c = coursesArr.optJSONObject(j) ?: continue
                        courses.add(
                            Course(
                                name = c.optString("n"), teacher = c.optString("t"),
                                room = c.optString("r"), building = c.optString("b"),
                                dayOfWeek = c.optInt("d", 1),
                                startSection = c.optInt("s", 1), endSection = c.optInt("e", 1),
                                weekText = c.optString("w"), credit = c.optString("xf"),
                                nature = c.optString("xz"), classGroup = c.optString("jx"),
                            ),
                        )
                    }
                    if (key.isNotEmpty() && courses.isNotEmpty()) termSchedule[key] = courses
                }
            }
        }
    }
}
