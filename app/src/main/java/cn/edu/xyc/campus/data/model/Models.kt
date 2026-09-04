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
    val weekText: String,    // zcd 可读串（如 "1-16周"）
    val credit: String,      // xf
    val nature: String,      // kcxz（公共必修等）
    val classGroup: String,  // jxbmc 教学班
)

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
