package cn.edu.xyc.campus.data.model

/** 一周课表条目（来自 /kbcx/xskbcxMobile_cxXsKb.html，服务器已按 zs 过滤） */
data class Course(
    val name: String,        // kcmc
    val teacher: String,     // xm
    val room: String,        // cdmc（教室）
    val building: String,    // lh（楼）
    val dayOfWeek: Int,      // xqj：1=周一 … 7=周日
    val startSection: Int,   // jcor "1-2" 前段
    val endSection: Int,     // jcor "1-2" 后段
    val weekText: String,    // zcd 可读串（如 "1-16周"；自定义课为 每周/单周/双周）
    val credit: String,      // xf
    val nature: String,      // kcxz（公共必修等）
    val classGroup: String,  // jxbmc 教学班
    val isCustom: Boolean = false, // 自定义课程（非教务数据）
    val customId: Long = 0L,       // 自定义课程 id（删除用）
    val customTime: String = "",   // 自定义课程的具体时间（如 "18:30-20:00"，按节次模式为空）
)

/**
 * 作息时间表（主教学楼 A/B/C/D 座、主附东、附西教室 与 其他教学楼，
 * 仅第 3、4 节不同）。来自学校作息表。
 */
object SectionTimes {
    data class SectionTime(val start: String, val end: String)

    private val MAIN = listOf(
        SectionTime("8:10", "8:55"), SectionTime("9:00", "9:45"),
        SectionTime("10:15", "11:00"), SectionTime("11:05", "11:50"),
        SectionTime("14:00", "14:45"), SectionTime("14:50", "15:35"),
        SectionTime("15:50", "16:35"), SectionTime("16:40", "17:25"),
        SectionTime("17:30", "18:15"), SectionTime("19:00", "19:45"),
        SectionTime("19:50", "20:35"), SectionTime("20:40", "21:25"),
    )

    private val OTHER = listOf(
        SectionTime("8:10", "8:55"), SectionTime("9:00", "9:45"),
        SectionTime("10:00", "10:45"), SectionTime("10:50", "11:35"),
        SectionTime("14:00", "14:45"), SectionTime("14:50", "15:35"),
        SectionTime("15:50", "16:35"), SectionTime("16:40", "17:25"),
        SectionTime("17:30", "18:15"), SectionTime("19:00", "19:45"),
        SectionTime("19:50", "20:35"), SectionTime("20:40", "21:25"),
    )

    /** 左列展示用：按开关取整表 */
    fun table(main: Boolean): List<SectionTime> = if (main) MAIN else OTHER

    /** 按教室推断作息：名字带 主/附 的归主教学楼，其余归其他教学楼 */
    fun tableFor(room: String): List<SectionTime> =
        if (room.contains("主") || room.contains("附")) MAIN else OTHER

    /** "10:15-11:50" 形式的节次区间，异常返回空串 */
    fun rangeText(sectionStart: Int, sectionEnd: Int, room: String): String {
        val t = tableFor(room)
        val s = t.getOrNull(sectionStart - 1) ?: return ""
        val e = t.getOrNull(sectionEnd - 1) ?: return ""
        return "${s.start}-${e.end}"
    }
}

/** 周次信息（来自 /kbcx/xskbcxMobile_cxZc.html） */
data class WeekInfo(
    val zs: Int,      // 周次号
    val rq: String,   // 该周日期（周一）
)

/** 一条成绩记录（来自 cjcxMobile_cxXsgrcj.html 的数组条目） */
data class GradeItem(
    val termName: String,    // xnmmc "2025-2026"
    val termNo: Int,         // xqmmc 1/2/3
    val courseName: String,  // kcmc
    val courseId: String,    // kch
    val nature: String,      // kcxzmc
    val category: String,    // kclbmc
    val credit: Double,      // xf
    val score: String,       // cj
    val gradePoint: Double,  // jd
    val teacher: String,     // jsxm
    val school: String,      // kkbmmc
    val pass: Boolean,       // cjsfzf == "否"
) {
    val scoreNumeric: Double? get() = score.toDoubleOrNull()
}

/** 学籍信息（课表 xsxx） */
data class StudentInfo(
    val name: String,        // XM
    val studentId: String,   // XH
    val className: String,   // BJMC
    val major: String,       // ZYMC
    val gradeYear: String,   // NJDM_ID
)

/** 学籍卡（我的页展示，数据综合自课表 xsxx + 成绩首条） */
data class ProfileCard(
    val info: StudentInfo,
    val college: String,     // 开课学院（成绩 kkbmmc，仅参考）
)

/** 第三方应用（门户 /app/getApplication） */
data class ThirdApp(
    val name: String,
    val href: String,
    val hrefType: Int,
)

/** 考试安排条目（来自 /pkmdgl/ksmdglMobile_cxKsxxList.html，字段宽松解析） */
data class ExamInfo(
    val courseName: String,   // kcmc
    val dateText: String,     // kssj / ksrq（考试日期时间）
    val location: String,     // cdmc
    val seatNo: String,       // zwh
    val examType: String,     // ksxzmc / kslbmc
    val raw: String,          // 原始 JSON（字段未校准时前端兜底展示）
)

/** 教务首页内联菜单项（clickMenu 参数，wapLogin 签名用） */
data class WapMenu(
    val procode: String,
    val type: String,
    val choice: String,      // Y 功能码
    val uid: String,
    val role: String,
    val key: String,
    val time: String,
    val title: String,
) {
    fun toUrl(jwxtBase: String): String =
        "$jwxtBase/jwglxt/xtgl/login_wapLogin.html?procode=$procode&type=$type" +
            "&choice=$choice&uid=$uid&role=$role&key=$key&time=$time"
}
