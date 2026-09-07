package cn.edu.xyc.campus.data.zongce

import android.content.Context
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

/**
 * 综测计算器数据层：
 * - ZongceAwardRecord：单条奖励记录（查表项 value 由 ZongceTables.lookup 得出，自定义项用户直填）
 * - ZongceDraft：一学年的完整草稿
 * - ZongceDraftStore：草稿本地持久化（filesDir/zongce_draft.json，原子写 tmp+rename）
 */

/** 综测模块标识 */
object ZongceModules {
    const val DE = "de"
    const val ZHI = "zhi"
    const val TI = "ti"
    const val MEI = "mei"
}

/** 单条奖励记录。value：查表项为查表得分（-2=一票定优标记），自定义项为用户直填分值 */
data class ZongceAwardRecord(
    val id: Long = 0L,
    val module: String,
    val category: String,
    val level: String = "",
    val grade: String = "",
    val value: Double = 0.0,
    val sameProject: Boolean = false,
)

/** 处罚计次项：key → 每次扣分 */
object ZongcePenalty {
    // 德育
    const val DE_YUAN_CRITICISM = "de_yuan_criticism"         // 院通报批评
    const val DE_SCHOOL_CRITICISM = "de_school_criticism"     // 校通报批评
    const val DE_WARNING = "de_warning"                       // 警告
    const val DE_SERIOUS_WARNING = "de_serious_warning"       // 严重警告
    const val DE_DEMOTION = "de_demotion"                     // 记过
    const val DE_LEAGUE_WARNING = "de_league_warning"         // 团内警告
    const val DE_LEAGUE_SERIOUS = "de_league_serious"         // 团内严重警告
    const val DE_LEAGUE_REMOVE = "de_league_remove"           // 撤销团内职务
    const val DE_ABSENT = "de_absent"                         // 缺勤
    const val DE_FRESH_EXAM = "de_fresh_exam"                 // 新生考试不及格
    // 智育
    const val ZHI_FAIL_COURSE = "zhi_fail_course"             // 课程不及格
    const val ZHI_RETAKE_FAIL = "zhi_retake_fail"             // 补考不及格
    const val ZHI_ABSENT = "zhi_absent"                       // 缺勤实习/学术活动
    const val ZHI_EXPULSION_WARNING = "zhi_expulsion_warning" // 退学警告或未获毕业资格
    // 体育
    const val TI_ABSENT = "ti_absent"                         // 缺勤
    const val TI_QUIT = "ti_quit"                             // 弃权
    // 美育
    const val MEI_DISORDER = "mei_disorder"                   // 扰乱秩序
    const val MEI_LEAVE_EARLY = "mei_leave_early"             // 未满服务期退团
    const val MEI_QUIT = "mei_quit"                           // 弃权

    val DE_KEYS = listOf(
        DE_YUAN_CRITICISM, DE_SCHOOL_CRITICISM, DE_WARNING, DE_SERIOUS_WARNING, DE_DEMOTION,
        DE_LEAGUE_WARNING, DE_LEAGUE_SERIOUS, DE_LEAGUE_REMOVE, DE_ABSENT, DE_FRESH_EXAM,
    )
    val ZHI_KEYS = listOf(ZHI_FAIL_COURSE, ZHI_RETAKE_FAIL, ZHI_ABSENT, ZHI_EXPULSION_WARNING)
    val TI_KEYS = listOf(TI_ABSENT, TI_QUIT)
    val MEI_KEYS = listOf(MEI_DISORDER, MEI_LEAVE_EARLY, MEI_QUIT)
    val ALL_KEYS: List<String> = DE_KEYS + ZHI_KEYS + TI_KEYS + MEI_KEYS

    /** 每次扣分值 */
    val VALUES: Map<String, Double> = mapOf(
        DE_YUAN_CRITICISM to 2.0,
        DE_SCHOOL_CRITICISM to 3.0,
        DE_WARNING to 5.0,
        DE_SERIOUS_WARNING to 8.0,
        DE_DEMOTION to 10.0,
        DE_LEAGUE_WARNING to 5.0,
        DE_LEAGUE_SERIOUS to 8.0,
        DE_LEAGUE_REMOVE to 10.0,
        DE_ABSENT to 1.0,
        DE_FRESH_EXAM to 1.0,
        ZHI_FAIL_COURSE to 5.0,
        ZHI_RETAKE_FAIL to 10.0,
        ZHI_ABSENT to 5.0,
        ZHI_EXPULSION_WARNING to 20.0,
        TI_ABSENT to 2.0,
        TI_QUIT to 1.0,
        MEI_DISORDER to 1.0,
        MEI_LEAVE_EARLY to 2.0,
        MEI_QUIT to 3.0,
    )
}

/** 奖励计次项（体育/美育按次计分的加分项）：key → 每次加分 */
object ZongceBonusCount {
    const val BONUS_PE_DAILY = "pe_daily"                       // 体育日常锻炼
    const val BONUS_PE_COMMEND = "pe_commend"                   // 体育院级表彰（≤1 次）
    const val BONUS_MEI_CAMPUS_AUDIENCE = "mei_campus_audience" // 美育校内实践（观众）
    const val BONUS_MEI_CAMPUS_JOIN = "mei_campus_join"         // 美育校内参与/校级表彰
    const val BONUS_MEI_OUT_AUDIENCE = "mei_out_audience"       // 美育校外实践（观众）
    const val BONUS_MEI_OUT_CITY = "mei_out_city"               // 美育校外市级参与
    const val BONUS_MEI_OUT_PROVINCE = "mei_out_province"       // 美育校外省级
    const val BONUS_MEI_OUT_NATIONAL = "mei_out_national"       // 美育校外国家级

    val ALL_KEYS = listOf(
        BONUS_PE_DAILY, BONUS_PE_COMMEND,
        BONUS_MEI_CAMPUS_AUDIENCE, BONUS_MEI_CAMPUS_JOIN,
        BONUS_MEI_OUT_AUDIENCE, BONUS_MEI_OUT_CITY, BONUS_MEI_OUT_PROVINCE, BONUS_MEI_OUT_NATIONAL,
    )

    /** 每次加分值 */
    val VALUES: Map<String, Double> = mapOf(
        BONUS_PE_DAILY to 0.5,
        BONUS_PE_COMMEND to 1.0,
        BONUS_MEI_CAMPUS_AUDIENCE to 0.5,
        BONUS_MEI_CAMPUS_JOIN to 2.0,
        BONUS_MEI_OUT_AUDIENCE to 0.5,
        BONUS_MEI_OUT_CITY to 2.0,
        BONUS_MEI_OUT_PROVINCE to 6.0,
        BONUS_MEI_OUT_NATIONAL to 10.0,
    )
}

/** 劳动教育布尔勾选项 key */
object ZongceLabFlags {
    const val DESTROY = "lab_destroy"                           // 无故不参加集体劳动且有破坏/不尊重行为（直判 D）
    const val MAJOR_NEGATIVE = "lab_major_negative"             // 其他重大负面影响（直判 D）
    const val SUMMER_HONOR = "lab_summer_honor"                 // 寒暑假实践获校级荣誉（升 B）
    const val CONTEST_BELOW_PROVINCE = "lab_contest_below_province" // 劳动竞赛获省级以下荣誉（升 B）
    const val MEDIA_PROVINCE = "lab_media_province"             // 实践获省级及以上媒体报道（升 A）
    const val CONTEST_PROVINCE = "lab_contest_province"         // 劳动竞赛获省级及以上荣誉（升 A）
}

/** 综测草稿（按学年） */
data class ZongceDraft(
    val yearKey: String = "",
    val awards: MutableList<ZongceAwardRecord> = mutableListOf(),
    // 智育手填
    val avgScore: Double = 0.0,   // 平均学分绩
    val gpaX1: Double = 0.0,      // 第一学期绩点 X1
    val gpaX2: Double = 0.0,      // 第二学期绩点 X2
    // 处罚计次（key 见 ZongcePenalty）
    val penalties: Map<String, Int> = emptyMap(),
    // 体育
    val peGradeLevel: Int = 1,    // 1=大一大二 2=大三大四
    val peTest: Int = 0,          // 0=合格 1=不合格 2=免测
    val peClubDone: Boolean = false, // 是否完成俱乐部（完成 +10）
    val peRunDone: Boolean = false,  // 是否跑完乐跑（跑完 +20）
    val peRecord: Boolean = false, // 破纪录第一名 +5
    // 劳动
    val dormStar: Int = 0,        // 0=无 1=三星 2=四星 3=五星
    val volunteerCount: Int = 0,  // 志愿服务次数
    val labFlags: Set<String> = emptySet(), // 勾选项，key 见 ZongceLabFlags
    val labCriticism: Int = 0,    // 校级通报批评次数
    // 标记
    val oneVoteExcellent: Boolean = false,   // 一票定优
    val deDishonesty: Boolean = false,       // 德育D直判：不良诚信记录
    val deBadBehavior: Boolean = false,      // 德育D直判：有损公德声誉
    val deProbation: Boolean = false,        // 德育D直判：留校察看及以上
    val deLeaguePunishment: Boolean = false, // 德育D直判：留团察看/开除团籍
    val tiVeto: Boolean = false,             // 体育：代跑/代测
    val meiVeto: Boolean = false,            // 美育：抄袭/违规
    val includeLabor: Boolean = true,        // 劳动等次是否参与综测等次评定（不影响 T 分）
    // 奖励计次（key 见 ZongceBonusCount）
    val bonusCounts: Map<String, Int> = emptyMap(),
) {
    /** 德育D直判：任一直判项勾选即成立 */
    val deVetoD: Boolean get() = deDishonesty || deBadBehavior || deProbation || deLeaguePunishment
}

/**
 * 草稿持久化：filesDir/zongce_draft.json。
 * 文件不存在/损坏时 load 返回默认草稿；save 原子写（先 .tmp 再 rename，失败兜底直接写）。
 */
object ZongceDraftStore {

    private const val FILE = "zongce_draft.json"

    /** 默认草稿：美育自评 10，其余 0/空，处罚/奖励计次全部为 0 */
    fun defaultDraft(yearKey: String): ZongceDraft = ZongceDraft(
        yearKey = yearKey,
        penalties = ZongcePenalty.ALL_KEYS.associateWith { 0 },
        bonusCounts = ZongceBonusCount.ALL_KEYS.associateWith { 0 },
    )

    fun load(context: Context): ZongceDraft = runCatching {
        val f = File(context.filesDir, FILE)
        if (!f.exists()) return@runCatching defaultDraft("")
        val o = JSONObject(f.readText())
        val awards = mutableListOf<ZongceAwardRecord>()
        o.optJSONArray("awards")?.let { arr ->
            for (i in 0 until arr.length()) {
                val a = arr.optJSONObject(i) ?: continue
                awards += ZongceAwardRecord(
                    id = a.optLong("id"),
                    module = a.optString("module"),
                    category = a.optString("category"),
                    level = a.optString("level"),
                    grade = a.optString("grade"),
                    value = a.optDouble("value", 0.0),
                    sameProject = a.optBoolean("same", false),
                )
            }
        }
        val penalties = o.optJSONObject("penalties")?.let { p ->
            ZongcePenalty.ALL_KEYS.associateWith { p.optInt(it, 0) }
        } ?: emptyMap()
        val bonusCounts = o.optJSONObject("bonusCounts")?.let { p ->
            ZongceBonusCount.ALL_KEYS.associateWith { p.optInt(it, 0) }
        } ?: emptyMap()
        val labFlags = mutableSetOf<String>()
        o.optJSONArray("labFlags")?.let { arr ->
            for (i in 0 until arr.length()) labFlags += arr.optString(i)
        }
        ZongceDraft(
            yearKey = o.optString("yearKey"),
            awards = awards,
            avgScore = o.optDouble("avgScore", 0.0),
            gpaX1 = o.optDouble("gpaX1", 0.0),
            gpaX2 = o.optDouble("gpaX2", 0.0),
            penalties = penalties,
            peGradeLevel = o.optInt("peGradeLevel", 1),
            peTest = o.optInt("peTest", 0),
            peClubDone = o.optBoolean("peClubDone", false),
            peRunDone = o.optBoolean("peRunDone", false),
            peRecord = o.optBoolean("peRecord", false),
            dormStar = o.optInt("dormStar", 0),
            volunteerCount = o.optInt("volunteerCount", 0),
            labFlags = labFlags,
            labCriticism = o.optInt("labCriticism", 0),
            oneVoteExcellent = o.optBoolean("oneVote", false),
            deDishonesty = o.optBoolean("deDishonesty", false),
            deBadBehavior = o.optBoolean("deBadBehavior", false),
            deProbation = o.optBoolean("deProbation", false),
            deLeaguePunishment = o.optBoolean("deLeaguePunishment", false),
            tiVeto = o.optBoolean("tiVeto", false),
            includeLabor = o.optBoolean("includeLabor", true),
            meiVeto = o.optBoolean("meiVeto", false),
            bonusCounts = bonusCounts,
        )
    }.getOrElse { defaultDraft("") }

    fun save(context: Context, draft: ZongceDraft) {
        runCatching {
            val awards = JSONArray()
            draft.awards.forEach { a ->
                awards.put(
                    JSONObject()
                        .put("id", a.id)
                        .put("module", a.module)
                        .put("category", a.category)
                        .put("level", a.level)
                        .put("grade", a.grade)
                        .put("value", a.value)
                        .put("same", a.sameProject),
                )
            }
            val flags = JSONArray()
            draft.labFlags.forEach { flags.put(it) }
            val o = JSONObject()
                .put("yearKey", draft.yearKey)
                .put("awards", awards)
                .put("avgScore", draft.avgScore)
                .put("gpaX1", draft.gpaX1)
                .put("gpaX2", draft.gpaX2)
                .put("penalties", JSONObject().apply { draft.penalties.forEach { (k, v) -> put(k, v) } })
                .put("peGradeLevel", draft.peGradeLevel)
                .put("peTest", draft.peTest)
                .put("peClubDone", draft.peClubDone)
                .put("peRunDone", draft.peRunDone)
                .put("peRecord", draft.peRecord)
                .put("dormStar", draft.dormStar)
                .put("volunteerCount", draft.volunteerCount)
                .put("labFlags", flags)
                .put("labCriticism", draft.labCriticism)
                .put("oneVote", draft.oneVoteExcellent)
                .put("deDishonesty", draft.deDishonesty)
                .put("deBadBehavior", draft.deBadBehavior)
                .put("deProbation", draft.deProbation)
                .put("deLeaguePunishment", draft.deLeaguePunishment)
                .put("tiVeto", draft.tiVeto)
                .put("meiVeto", draft.meiVeto)
                .put("bonusCounts", JSONObject().apply { draft.bonusCounts.forEach { (k, v) -> put(k, v) } })
            val f = File(context.filesDir, FILE)
            val tmp = File(context.filesDir, "$FILE.tmp")
            tmp.writeText(o.toString())
            if (!tmp.renameTo(f)) {
                f.writeText(o.toString())
                tmp.delete()
            }
        }
    }

    /** 删除草稿文件并返回默认草稿 */
    fun clear(context: Context): ZongceDraft {
        runCatching { File(context.filesDir, FILE).delete() }
        return defaultDraft("")
    }
}
