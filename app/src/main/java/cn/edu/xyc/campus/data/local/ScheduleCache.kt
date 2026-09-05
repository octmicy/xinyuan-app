package cn.edu.xyc.campus.data.local

import androidx.compose.runtime.mutableStateMapOf
import cn.edu.xyc.campus.data.model.Course
import cn.edu.xyc.campus.data.model.GradeItem
import cn.edu.xyc.campus.data.model.StudentInfo
import cn.edu.xyc.campus.data.model.ThirdApp
import cn.edu.xyc.campus.data.model.WeekInfo

/**
 * 进程内缓存（Compose State 驱动）：
 * - 每周课表 / 周次列表 / 成绩 / 应用：切换时命中缓存零等待，put 自动触发 UI 重组
 * - inFlight 去重防止并发重复请求
 * - 预加载策略：相邻周在当前周加载完成后静默预取
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

    fun weeksKey(xnm: String, xqm: String) = "W:$xnm-$xqm"
    fun weekKey(xnm: String, xqm: String, zs: Int) = "D:$xnm-$xqm-$zs"
    fun gradeKey(xnm: String, xqm: String) = "G:$xnm-$xqm"
    fun termKey(xnm: String, xqm: String) = "T:$xnm-$xqm"

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

    fun clear() {
        weeksList.clear()
        weekData.clear()
        gradeData.clear()
        profileData.clear()
        applications.clear()
        termSchedule.clear()
        inFlight.clear()
    }
}
