package cn.edu.xyc.campus.data.zongce

/**
 * 综测内置分值表（《2026 版学生手册》第 146-162 页）。
 * lookup 返回：-1=查无此项；-2=一票定优档（特殊标记，不计入奖励分合计）。
 * 学生骨干考核优秀(+4)、美育课程合格(+5) 为勾选项，不算查表，直接以记录形式进入 awards。
 */
object ZongceTables {

    /** 查无此项 */
    const val NOT_FOUND = -1.0

    /** 一票定优档标记 */
    const val VOTE_EXCELLENT = -2.0

    // 级别
    const val LEVEL_NATIONAL = "国家"
    const val LEVEL_PROVINCE = "省"
    const val LEVEL_CITY = "市"
    const val LEVEL_SCHOOL = "校"
    const val LEVEL_COLLEGE = "院"
    const val LEVEL_NONE = "无" // 与级别无关的类目（论文/著作/证书等）

    // 常用等级
    const val GRADE_FIRST = "一等"
    const val GRADE_SECOND = "二等"
    const val GRADE_THIRD = "三等"
    const val GRADE_OTHER = "其他"
    const val GRADE_SPECIAL = "特等"   // 无固定档时 = 同级别一等 × 1.3
    const val GRADE_EXCELLENT = "优秀" // 表内固定档；无固定档时 = 同级别三等 × 0.6
    const val GRADE_GOOD = "优"
    const val GRADE_GOLD = "金"
    const val GRADE_SILVER = "银"
    const val GRADE_BRONZE = "铜"
    const val GRADE_JOIN = "参与"
    const val GRADE_RECOGNIZED = "认定"
    const val GRADE_PASS = "合格"
    const val GRADE_EACH = "每项"

    // 类目（稳定字符串常量）
    const val DE_IDEO_ACTIVITY = "de_ideo_activity"             // 思想教育类活动
    const val DE_DEED = "de_deed"                               // 优秀事迹
    const val DE_HONOR_PERSON = "de_honor_person"               // 个人荣誉称号
    const val DE_HONOR_GROUP = "de_honor_group"                 // 集体荣誉称号
    const val DE_CADRE = "de_cadre"                             // 学生骨干考核优秀（+4 勾选）
    const val ZHI_INNOVATION = "zhi_innovation"                 // 互联网+
    const val ZHI_CHALLENGE_ACADEMIC = "zhi_challenge_academic" // 挑战杯学术
    const val ZHI_CHALLENGE_STARTUP = "zhi_challenge_startup"   // 挑战杯创业
    const val ZHI_CHUANGYI = "zhi_chuangyi"                     // 中国创翼
    const val ZHI_CONTEST_RANKED = "zhi_contest_ranked"         // 学科竞赛（排行榜内）
    const val ZHI_CONTEST_OTHER = "zhi_contest_other"           // 学科竞赛（排行榜外）
    const val ZHI_PAPER = "zhi_paper"                           // 学术论文
    const val ZHI_BOOK = "zhi_book"                             // 著作
    const val ZHI_PATENT = "zhi_patent"                         // 专利
    const val ZHI_BIGCUTI = "zhi_bigcuti"                       // 大创
    const val ZHI_STARTUP_YEARS = "zhi_startup_years"           // 创业年限
    const val ZHI_CERT_COMPUTER = "zhi_cert_computer"           // 计算机等级
    const val ZHI_CERT_FOREIGN = "zhi_cert_foreign"             // 外语
    const val ZHI_CERT_VOCATIONAL = "zhi_cert_vocational"       // 职业证书
    const val TI_CONTEST = "ti_contest"                         // 体育竞赛
    const val MEI_AWARD = "mei_award"                           // 美育获奖
    const val MEI_ART_CONTEST = "mei_art_contest"               // 艺术竞赛
    const val MEI_COURSE = "mei_course"                         // 美育课程合格（+5 勾选）

    data class TableRow(val level: String, val grade: String, val value: Double)

    private fun rows(vararg groups: List<TableRow>): List<TableRow> = groups.flatMap { it }

    /** 一/二/三等/其他 四档 */
    private fun ranked(level: String, first: Double, second: Double, third: Double, other: Double) = listOf(
        TableRow(level, GRADE_FIRST, first),
        TableRow(level, GRADE_SECOND, second),
        TableRow(level, GRADE_THIRD, third),
        TableRow(level, GRADE_OTHER, other),
    )

    /** 一/二/三等/优秀 四档 */
    private fun rankedExcellent(level: String, first: Double, second: Double, third: Double, excellent: Double) = listOf(
        TableRow(level, GRADE_FIRST, first),
        TableRow(level, GRADE_SECOND, second),
        TableRow(level, GRADE_THIRD, third),
        TableRow(level, GRADE_EXCELLENT, excellent),
    )

    /** 认定档（无等级区分） */
    private fun rec(level: String, value: Double) = listOf(TableRow(level, GRADE_RECOGNIZED, value))

    private val HONOR_ROWS: List<TableRow> = rows(
        rec(LEVEL_NATIONAL, 10.0),
        rec(LEVEL_PROVINCE, 8.0),
        rec(LEVEL_CITY, 5.0),
        rec(LEVEL_SCHOOL, 5.0),
        rec(LEVEL_COLLEGE, 2.0),
    )

    private val TABLES: Map<String, List<TableRow>> = mapOf(
        // ---- 德育 ----
        DE_IDEO_ACTIVITY to rows(
            ranked(LEVEL_NATIONAL, 20.0, 15.0, 10.0, 6.0),
            ranked(LEVEL_PROVINCE, 10.0, 8.0, 6.0, 4.0),
            ranked(LEVEL_SCHOOL, 6.0, 5.0, 4.0, 2.0),
            ranked(LEVEL_COLLEGE, 4.0, 3.0, 2.0, 1.0),
        ),
        DE_DEED to rows(
            rec(LEVEL_NATIONAL, 10.0),
            rec(LEVEL_PROVINCE, 8.0),
            rec(LEVEL_CITY, 5.0),
            rec(LEVEL_SCHOOL, 5.0),
            rec(LEVEL_COLLEGE, 2.0),
        ),
        DE_HONOR_PERSON to HONOR_ROWS,
        DE_HONOR_GROUP to HONOR_ROWS,
        // ---- 智育 ----
        ZHI_INNOVATION to listOf(
            TableRow(LEVEL_PROVINCE, GRADE_GOLD, VOTE_EXCELLENT),
            TableRow(LEVEL_PROVINCE, GRADE_SILVER, 100.0),
            TableRow(LEVEL_PROVINCE, GRADE_BRONZE, 50.0),
            TableRow(LEVEL_SCHOOL, GRADE_GOLD, 20.0),
            TableRow(LEVEL_SCHOOL, GRADE_SILVER, 15.0),
            TableRow(LEVEL_SCHOOL, GRADE_BRONZE, 10.0),
            TableRow(LEVEL_SCHOOL, GRADE_JOIN, 5.0),
        ),
        ZHI_CHALLENGE_ACADEMIC to listOf(
            TableRow(LEVEL_PROVINCE, GRADE_SPECIAL, VOTE_EXCELLENT),
            TableRow(LEVEL_PROVINCE, GRADE_FIRST, 100.0),
            TableRow(LEVEL_PROVINCE, GRADE_SECOND, 50.0),
            TableRow(LEVEL_PROVINCE, GRADE_THIRD, 30.0),
            TableRow(LEVEL_SCHOOL, GRADE_FIRST, 20.0),
            TableRow(LEVEL_SCHOOL, GRADE_SECOND, 15.0),
            TableRow(LEVEL_SCHOOL, GRADE_THIRD, 10.0),
            TableRow(LEVEL_SCHOOL, GRADE_JOIN, 5.0),
        ),
        ZHI_CHALLENGE_STARTUP to listOf(
            TableRow(LEVEL_PROVINCE, GRADE_GOLD, VOTE_EXCELLENT),
            TableRow(LEVEL_PROVINCE, GRADE_SILVER, 100.0),
            TableRow(LEVEL_PROVINCE, GRADE_BRONZE, 50.0),
            TableRow(LEVEL_SCHOOL, GRADE_GOLD, 20.0),
            TableRow(LEVEL_SCHOOL, GRADE_SILVER, 15.0),
            TableRow(LEVEL_SCHOOL, GRADE_BRONZE, 10.0),
            TableRow(LEVEL_SCHOOL, GRADE_JOIN, 5.0),
        ),
        ZHI_CHUANGYI to listOf(
            TableRow(LEVEL_PROVINCE, GRADE_FIRST, VOTE_EXCELLENT),
            TableRow(LEVEL_PROVINCE, GRADE_SECOND, 100.0),
            TableRow(LEVEL_PROVINCE, GRADE_THIRD, 50.0),
            TableRow(LEVEL_PROVINCE, GRADE_GOOD, 30.0),
            TableRow(LEVEL_CITY, GRADE_FIRST, 20.0),
            TableRow(LEVEL_CITY, GRADE_SECOND, 15.0),
            TableRow(LEVEL_CITY, GRADE_THIRD, 10.0),
            TableRow(LEVEL_CITY, GRADE_GOOD, 5.0),
            TableRow(LEVEL_CITY, GRADE_JOIN, 3.0),
        ),
        ZHI_CONTEST_RANKED to rows(
            ranked(LEVEL_NATIONAL, 100.0, 50.0, 30.0, 6.0),
            ranked(LEVEL_PROVINCE, 30.0, 25.0, 20.0, 4.0),
            ranked(LEVEL_SCHOOL, 10.0, 6.0, 3.0, 2.0),
        ),
        ZHI_CONTEST_OTHER to rows(
            ranked(LEVEL_NATIONAL, 25.0, 20.0, 15.0, 5.0),
            ranked(LEVEL_PROVINCE, 20.0, 15.0, 10.0, 3.0),
            ranked(LEVEL_SCHOOL, 6.0, 3.0, 2.0, 1.0),
        ),
        ZHI_PAPER to listOf(
            TableRow(LEVEL_NONE, "北大核心以上", VOTE_EXCELLENT),
            TableRow(LEVEL_NONE, "北大核心", 100.0),
            TableRow(LEVEL_NONE, "江西日报理论版", 100.0),
            TableRow(LEVEL_NONE, "本科高校学报", 50.0),
            TableRow(LEVEL_NONE, "一般期刊", 20.0),
        ),
        ZHI_BOOK to listOf(
            TableRow(LEVEL_NONE, "专著", 100.0),
            TableRow(LEVEL_NONE, "译著", 80.0),
            TableRow(LEVEL_NONE, "编著校注", 50.0),
            TableRow(LEVEL_NONE, "文学作品", 10.0), // 每万字，界面按字数乘算
        ),
        ZHI_PATENT to listOf(
            TableRow(LEVEL_NONE, "发明", 50.0),
            TableRow(LEVEL_NONE, "其他", 10.0),
        ),
        ZHI_BIGCUTI to listOf(
            TableRow(LEVEL_NATIONAL, GRADE_EXCELLENT, 30.0),
            TableRow(LEVEL_NATIONAL, GRADE_PASS, 20.0),
            TableRow(LEVEL_PROVINCE, GRADE_EXCELLENT, 20.0),
            TableRow(LEVEL_PROVINCE, GRADE_PASS, 15.0),
            TableRow(LEVEL_SCHOOL, GRADE_EXCELLENT, 10.0),
            TableRow(LEVEL_SCHOOL, GRADE_PASS, 5.0),
        ),
        ZHI_STARTUP_YEARS to listOf(
            TableRow(LEVEL_NONE, "3年以上", 20.0),
            TableRow(LEVEL_NONE, "1-3年", 15.0),
            TableRow(LEVEL_NONE, "1年以内", 10.0),
            TableRow(LEVEL_NONE, "孵化阶段", 5.0),
        ),
        ZHI_CERT_COMPUTER to listOf(
            TableRow(LEVEL_NONE, "二级", 3.0),
            TableRow(LEVEL_NONE, "三级", 5.0),
            TableRow(LEVEL_NONE, "四级", 7.0),
        ),
        ZHI_CERT_FOREIGN to listOf(
            TableRow(LEVEL_NONE, "60分档", 15.0),
            TableRow(LEVEL_NONE, "70分档", 20.0),
            TableRow(LEVEL_NONE, "80分档", 25.0),
        ),
        ZHI_CERT_VOCATIONAL to listOf(
            TableRow(LEVEL_NONE, GRADE_EACH, 15.0),
        ),
        // ---- 体育 ----
        TI_CONTEST to rows(
            ranked(LEVEL_NATIONAL, 20.0, 15.0, 10.0, 6.0),
            ranked(LEVEL_PROVINCE, 10.0, 8.0, 6.0, 4.0),
            ranked(LEVEL_SCHOOL, 6.0, 5.0, 4.0, 2.0),
            ranked(LEVEL_COLLEGE, 5.0, 4.0, 3.0, 1.0),
        ),
        // ---- 美育 ----
        MEI_AWARD to rows(
            rankedExcellent(LEVEL_NATIONAL, 20.0, 15.0, 10.0, 8.0),
            rankedExcellent(LEVEL_PROVINCE, 10.0, 8.0, 6.0, 4.0),
            rankedExcellent(LEVEL_SCHOOL, 6.0, 4.0, 2.0, 1.0),
        ),
        MEI_ART_CONTEST to rows(
            rankedExcellent(LEVEL_NATIONAL, 20.0, 15.0, 12.0, 10.0),
            rankedExcellent(LEVEL_PROVINCE, 12.0, 10.0, 8.0, 6.0),
            rankedExcellent(LEVEL_CITY, 8.0, 6.0, 4.0, 2.0),
            rankedExcellent(LEVEL_SCHOOL, 8.0, 6.0, 4.0, 2.0),
            rankedExcellent(LEVEL_COLLEGE, 4.0, 2.0, 1.0, 0.5),
        ),
    )

    /**
     * 查表：category + level + grade → 分值。
     * - 查无此项返回 [NOT_FOUND]（-1）
     * - 一票定优档返回 [VOTE_EXCELLENT]（-2）
     * - 特等奖（无固定档）= 同级别一等 × 1.3；优秀奖（无固定档）= 同级别三等 × 0.6
     */
    fun lookup(category: String, level: String, grade: String): Double {
        val table = TABLES[category] ?: return NOT_FOUND
        table.firstOrNull { it.level == level && it.grade == grade }?.let { return it.value }
        if (grade == GRADE_SPECIAL) {
            table.firstOrNull { it.level == level && it.grade == GRADE_FIRST }
                ?.let { return round2(it.value * 1.3) }
        }
        if (grade == GRADE_EXCELLENT) {
            table.firstOrNull { it.level == level && it.grade == GRADE_THIRD }
                ?.let { return round2(it.value * 0.6) }
        }
        return NOT_FOUND
    }

    /**
     * 界面辅助：返回类目可选的级别与等级列表（查表外类目返回空表）。
     * 学科竞赛两类附加 特等/优秀 档（查表按 一等×1.3 / 三等×0.6 推导）。
     */
    fun optionsOf(category: String): Pair<List<String>, List<String>> {
        val table = TABLES[category] ?: return emptyList<String>() to emptyList()
        val levels = table.map { it.level }.distinct()
        val grades = table.map { it.grade }.distinct().toMutableList()
        if (category == ZHI_CONTEST_RANKED || category == ZHI_CONTEST_OTHER) {
            grades += listOf(GRADE_SPECIAL, GRADE_EXCELLENT)
        }
        return levels to grades
    }

    private fun round2(v: Double): Double = Math.round(v * 100.0) / 100.0
}
