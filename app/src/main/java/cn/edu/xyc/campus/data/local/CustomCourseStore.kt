package cn.edu.xyc.campus.data.local

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import cn.edu.xyc.campus.data.model.Course
import cn.edu.xyc.campus.data.model.SectionTimes
import java.io.File
import org.json.JSONArray

/**
 * 自定义课程：课表上没有的课（社团/自习/讲座等），用户手动添加。
 * 两种时间模式：
 * - 按节次：选起止节次，具体时间按作息表推算（startTime/endTime 为空）
 * - 按时间：直接填 "18:30"/"20:00"，网格按重叠节次折算占位，展示用真实时间
 * parity: 0=每周 1=单周 2=双周（按教学周号 zs 奇偶，第 1 周为单周）。
 *
 * - courses 是 Compose 状态列表，增删即时刷新课表网格
 * - 落盘 filesDir/custom_courses.json（App 进程被杀后重新加载）
 * - 小组件渲染时通过 readForWeek 独立读盘合并，与 App 进程无关
 */
object CustomCourseStore {

    data class Def(
        val id: Long,
        val name: String,
        val teacher: String,
        val room: String,
        val dayOfWeek: Int,
        val startSection: Int,   // 网格占位（时间模式为折算结果）
        val endSection: Int,
        val parity: Int,
        val startTime: String = "", // 非空 = 按具体时间模式
        val endTime: String = "",
        val fracStart: Float = 0f,  // 起始节次格内纵向起点比例
        val fracEnd: Float = 1f,    // 结束节次格内纵向终点比例
    ) {
        val isTimeMode: Boolean get() = startTime.isNotEmpty() && endTime.isNotEmpty()
        val timeText: String get() = if (isTimeMode) "$startTime-$endTime" else ""
    }

    private const val FILE = "custom_courses.json"

    val courses = mutableStateListOf<Def>()

    private var nextId = 1L

    /** App 启动时加载一次 */
    fun init(context: Context) {
        synchronized(courses) {
            courses.clear()
            runCatching {
                val f = File(context.filesDir, FILE)
                if (f.exists()) {
                    val arr = JSONArray(f.readText())
                    for (i in 0 until arr.length()) {
                        val o = arr.optJSONObject(i) ?: continue
                        courses += Def(
                            id = o.optLong("id"),
                            name = o.optString("name"),
                            teacher = o.optString("teacher"),
                            room = o.optString("room"),
                            dayOfWeek = o.optInt("day", 1),
                            startSection = o.optInt("start", 1),
                            endSection = o.optInt("end", 1),
                            parity = o.optInt("parity", 0),
                            startTime = o.optString("stime"),
                            endTime = o.optString("etime"),
                            fracStart = o.optDouble("fs", 0.0).toFloat(),
                            fracEnd = o.optDouble("fe", 1.0).toFloat(),
                        )
                    }
                    nextId = (courses.maxOfOrNull { it.id } ?: 0L) + 1
                }
            }
        }
    }

    fun add(
        context: Context,
        name: String,
        teacher: String,
        room: String,
        day: Int,
        parity: Int,
        startSec: Int,
        endSec: Int,
        startTime: String = "",
        endTime: String = "",
    ) {
        val placement = if (startTime.isNotEmpty() && endTime.isNotEmpty()) {
            placeByTime(startTime, endTime, room)
        } else {
            Placement(startSec, endSec, 0f, 1f)
        }
        val def = Def(
            id = nextId++, name = name, teacher = teacher, room = room,
            dayOfWeek = day, startSection = placement.sectionStart, endSection = placement.sectionEnd,
            parity = parity, startTime = startTime, endTime = endTime,
            fracStart = placement.fracStart, fracEnd = placement.fracEnd,
        )
        synchronized(courses) { courses += def }
        persist(context)
    }

    fun remove(context: Context, id: Long) {
        synchronized(courses) { courses.removeAll { it.id == id } }
        persist(context)
    }

    private data class Placement(
        val sectionStart: Int,
        val sectionEnd: Int,
        val fracStart: Float,
        val fracEnd: Float,
    )

    private fun toMin(s: String): Int = s.split(":").mapNotNull { it.toIntOrNull() }.let {
        if (it.size == 2) it[0] * 60 + it[1] else -1
    }

    /**
     * 具体时间 → 网格节次占位：取与 [startTime,endTime) 有交集的节次，
     * 并算出首尾节次内的纵向比例（不满一节只画部分高度）。
     * 完全落在作息之外（如深夜）时钳到第 1 或第 12 节，保证可见。
     */
    private fun placeByTime(startTime: String, endTime: String, room: String): Placement {
        val table = SectionTimes.tableFor(room)
        val st = toMin(startTime)
        val en = toMin(endTime)
        if (st < 0 || en < 0 || en <= st) return Placement(1, 1, 0f, 1f)
        var s = Int.MAX_VALUE
        var e = Int.MIN_VALUE
        table.forEachIndexed { i, sec ->
            val ss = toMin(sec.start)
            val se = toMin(sec.end)
            if (se > st && ss < en) {
                s = minOf(s, i + 1)
                e = maxOf(e, i + 1)
            }
        }
        if (s > e) {
            // 与作息无交集：钳到边界节
            return if (en <= toMin(table.first().start)) Placement(1, 1, 0f, 1f)
            else Placement(12, 12, 0f, 1f)
        }
        fun frac(secIndex0: Int, minutes: Int): Float {
            val sec = table[secIndex0]
            val dur = (toMin(sec.end) - toMin(sec.start)).coerceAtLeast(1)
            return ((minutes - toMin(sec.start)).toFloat() / dur).coerceIn(0f, 1f)
        }
        val fs = frac(s - 1, st)
        val fe = frac(e - 1, en).coerceAtLeast(0.05f)
        return Placement(s, e, fs, fe)
    }

    private fun persist(context: Context) {
        runCatching {
            val arr = JSONArray()
            synchronized(courses) {
                courses.forEach { c ->
                    arr.put(
                        org.json.JSONObject()
                            .put("id", c.id)
                            .put("name", c.name)
                            .put("teacher", c.teacher)
                            .put("room", c.room)
                            .put("day", c.dayOfWeek)
                            .put("start", c.startSection)
                            .put("end", c.endSection)
                            .put("parity", c.parity)
                            .put("stime", c.startTime)
                            .put("etime", c.endTime)
                            .put("fs", c.fracStart.toDouble())
                            .put("fe", c.fracEnd.toDouble()),
                    )
                }
            }
            val f = File(context.filesDir, FILE)
            val tmp = File(context.filesDir, "$FILE.tmp")
            tmp.writeText(arr.toString())
            if (!tmp.renameTo(f)) {
                f.writeText(arr.toString())
                tmp.delete()
            }
        }
    }

    fun parityLabel(parity: Int): String = when (parity) {
        1 -> "单周"
        2 -> "双周"
        else -> "每周"
    }

    /** 课表网格用：某教学周的自定义课（按单双周过滤），转成 Course 直接复用网格渲染 */
    fun forWeek(zs: Int): List<Course> = courses
        .filter { matchParity(it.parity, zs) }
        .map { it.toCourse() }

    private fun matchParity(parity: Int, zs: Int): Boolean =
        parity == 0 || (parity == 1 && zs % 2 == 1) || (parity == 2 && zs % 2 == 0)

    private fun Def.toCourse() = Course(
        name = name,
        teacher = teacher,
        room = room,
        building = "",
        dayOfWeek = dayOfWeek,
        startSection = startSection,
        endSection = endSection,
        weekText = parityLabel(parity),
        credit = "",
        nature = "自定义",
        classGroup = "",
        isCustom = true,
        customId = id,
        customTime = timeText,
        timeFracStart = fracStart,
        timeFracEnd = fracEnd,
    )

    /** 小组件用：无状态读盘并按周过滤（不触碰 Compose 状态） */
    fun readForWeek(context: Context, zs: Int): List<Course> = runCatching {
        val f = File(context.filesDir, FILE)
        if (!f.exists()) return@runCatching emptyList()
        val arr = JSONArray(f.readText())
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val parity = o.optInt("parity", 0)
            if (!matchParity(parity, zs)) return@mapNotNull null
            val stime = o.optString("stime")
            val etime = o.optString("etime")
            Course(
                name = o.optString("name"),
                teacher = o.optString("teacher"),
                room = o.optString("room"),
                building = "",
                dayOfWeek = o.optInt("day", 1),
                startSection = o.optInt("start", 1),
                endSection = o.optInt("end", 1),
                weekText = parityLabel(parity),
                credit = "",
                nature = "自定义",
                classGroup = "",
                isCustom = true,
                customId = o.optLong("id"),
                customTime = if (stime.isNotEmpty() && etime.isNotEmpty()) "$stime-$etime" else "",
                timeFracStart = o.optDouble("fs", 0.0).toFloat(),
                timeFracEnd = o.optDouble("fe", 1.0).toFloat(),
            )
        }
    }.getOrDefault(emptyList())
}
