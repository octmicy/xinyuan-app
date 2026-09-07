package cn.edu.xyc.campus.data.zongce

import kotlin.math.max
import kotlin.math.min

/**
 * 综测计算（纯函数，不依赖 Android）：
 * - M1 德育：Z1 = 80 + min(20, 奖励) − 处罚，clamp [0,110]（基础 70 + 民主评议 10 默认计入）
 * - M2 智育：Z2 = 平均学分绩×0.8 + (min(100, 奖励) − 处罚)×0.2，clamp [0,100]
 * - M3 体育：按年级档合成，俱乐部/乐跑完成即加满分，代跑/代测 → 0
 * - M4 美育：Z4 = 80 + min(20, 奖励) − 处罚（基础 70 + 自评激励 10 固定计入），抄袭/违规 → 0
 * - M5 劳动等次：规则链 D→C→B→A
 * - M7 合成：T = Z1×0.2 + (Z2+Z5)×0.6 + Z3×0.1 + Z4×0.1，Z5 = max(0, X2−X1)
 * - 等次：A>75 且劳动≥B（劳动开关开启时）/ 一票定优；B≥65 且劳动≥C；C≥60 且劳动≥C
 * 所有浮点结果保留两位小数。
 */
object ZongceCalculator {

    data class ModuleResult(
        val z: Double,            // 模块最终分
        val bonus: Double,        // 计入的奖励分（封顶后）
        val rawBonus: Double,     // 奖励分原始合计（封顶前，供公式展示）
        val penalty: Double,      // 处罚扣分合计
        val bonusCapped: Boolean, // 奖励分是否触发封顶
        val notes: List<String>,  // 结果页提示
    )

    data class FinalResult(
        val z1: Double,
        val z2: Double,
        val z3: Double,
        val z4: Double,
        val z5: Double,
        val t: Double,
        val grade: String,       // "A"/"B"/"C"/"D"
        val laborGrade: String,  // "A"/"B"/"C"/"D"
        val oneVote: Boolean,    // 一票定优（勾选或命中 -2 档记录）
        val deDirectD: Boolean,  // 德育D直判
    )

    // ---------- 工具 ----------

    private fun r2(v: Double): Double = Math.round(v * 100.0) / 100.0

    /** -2 是"一票定优"标记，不作为奖励分参与合计 */
    private fun bonusValue(v: Double): Double = if (v == ZongceTables.VOTE_EXCELLENT) 0.0 else v

    private fun cnt(draft: ZongceDraft, key: String): Int = draft.penalties[key] ?: 0

    private fun bcnt(draft: ZongceDraft, key: String): Int = draft.bonusCounts[key] ?: 0

    private fun penaltySum(draft: ZongceDraft, keys: List<String>): Double =
        keys.sumOf { (ZongcePenalty.VALUES[it] ?: 0.0) * cnt(draft, it) }

    private fun awardsOf(draft: ZongceDraft, module: String) =
        draft.awards.filter { it.module == module }

    // ---------- M1 德育 ----------

    fun m1De(draft: ZongceDraft): ModuleResult {
        val list = awardsOf(draft, ZongceModules.DE)
        // 荣誉称号：个人/集体各取同组最高，两者之和封顶 10，再与其他类目相加
        val personMax = list.filter { it.category == ZongceTables.DE_HONOR_PERSON }
            .maxOfOrNull { bonusValue(it.value) } ?: 0.0
        val groupMax = list.filter { it.category == ZongceTables.DE_HONOR_GROUP }
            .maxOfOrNull { bonusValue(it.value) } ?: 0.0
        val honor = min(10.0, personMax + groupMax)
        val others = list
            .filter { it.category != ZongceTables.DE_HONOR_PERSON && it.category != ZongceTables.DE_HONOR_GROUP }
            .sumOf { bonusValue(it.value) }
        val raw = r2(honor + others)
        val capped = min(20.0, raw)
        val penalty = penaltySum(draft, ZongcePenalty.DE_KEYS)
        val z = (80.0 + capped - penalty).coerceIn(0.0, 110.0)
        val notes = mutableListOf<String>()
        if (personMax + groupMax > 10.0) {
            notes += "个人/集体荣誉称号合计 ${r2(personMax + groupMax)} 分，按上限 10 分计"
        }
        if (raw > 20.0) notes += "德育奖励合计 $raw 分，超出上限，按 20 分计"
        return ModuleResult(r2(z), capped, raw, penalty, raw > 20.0, notes)
    }

    // ---------- M2 智育 ----------

    fun m2Zhi(draft: ZongceDraft): ModuleResult {
        val list = awardsOf(draft, ZongceModules.ZHI)
        // "同一项目"记录：同 category 分组取最高；其余逐条累加
        val sameGroup = list.filter { it.sameProject }
            .groupBy { it.category }
            .values.sumOf { group -> group.maxOf { bonusValue(it.value) } }
        val direct = list.filter { !it.sameProject }.sumOf { bonusValue(it.value) }
        val raw = r2(sameGroup + direct)
        val capped = min(100.0, raw)
        val penalty = penaltySum(draft, ZongcePenalty.ZHI_KEYS)
        val z = (draft.avgScore * 0.8 + (capped - penalty) * 0.2).coerceIn(0.0, 100.0)
        val oneVoteRecord = list.any { it.value == ZongceTables.VOTE_EXCELLENT }
        val notes = mutableListOf<String>()
        if (raw > 100.0) notes += "智育奖励合计 $raw 分，超出上限，按 100 分计"
        if (oneVoteRecord) notes += "命中一票定优档奖励，综测等次直接评定为 A"
        return ModuleResult(r2(z), capped, raw, penalty, raw > 100.0, notes)
    }

    // ---------- M3 体育 ----------

    fun m3Ti(draft: ZongceDraft): ModuleResult {
        val list = awardsOf(draft, ZongceModules.TI)
        var raw = list.sumOf { bonusValue(it.value) }
        if (draft.peRecord) raw += 5.0 // 破纪录第一名
        raw += bcnt(draft, ZongceBonusCount.BONUS_PE_DAILY) * 0.5
        raw += bcnt(draft, ZongceBonusCount.BONUS_PE_COMMEND) * 1.0
        raw = r2(raw)
        val capped = min(30.0, raw)
        val penalty = penaltySum(draft, ZongcePenalty.TI_KEYS)
        val testBase = when (draft.peTest) {
            1 -> if (draft.peGradeLevel == 1) 28.0 else 50.0    // 不合格
            2 -> 60.0                                           // 免测
            else -> if (draft.peGradeLevel == 1) 40.0 else 70.0 // 合格
        }
        val z = when {
            draft.tiVeto -> 0.0
            draft.peGradeLevel == 1 -> {
                // 俱乐部/乐跑为"是否完成"勾选：完成即加满分
                val club = if (draft.peClubDone) 10.0 else 0.0
                val run = if (draft.peRunDone) 20.0 else 0.0
                testBase + club + run + capped - penalty
            }
            else -> testBase + capped - penalty
        }
        val notes = mutableListOf<String>()
        if (raw > 30.0) notes += "体育奖励合计 $raw 分，超出上限，按 30 分计"
        if (draft.tiVeto) notes += "代跑/代测，体育成绩记 0 分"
        return ModuleResult(r2(z), capped, raw, penalty, raw > 30.0, notes)
    }

    // ---------- M4 美育 ----------

    fun m4Mei(draft: ZongceDraft): ModuleResult {
        val list = awardsOf(draft, ZongceModules.MEI)
        // 美育课程合格 +5 为勾选项，以记录形式存在于 awards
        var raw = list.sumOf { bonusValue(it.value) }
        raw += bcnt(draft, ZongceBonusCount.BONUS_MEI_CAMPUS_AUDIENCE) * 0.5
        raw += bcnt(draft, ZongceBonusCount.BONUS_MEI_CAMPUS_JOIN) * 2.0
        raw += bcnt(draft, ZongceBonusCount.BONUS_MEI_OUT_AUDIENCE) * 0.5
        raw += bcnt(draft, ZongceBonusCount.BONUS_MEI_OUT_CITY) * 2.0
        raw += bcnt(draft, ZongceBonusCount.BONUS_MEI_OUT_PROVINCE) * 6.0
        raw += bcnt(draft, ZongceBonusCount.BONUS_MEI_OUT_NATIONAL) * 10.0
        raw = r2(raw)
        val capped = min(20.0, raw)
        val penalty = penaltySum(draft, ZongcePenalty.MEI_KEYS)
        val z = if (draft.meiVeto) 0.0 else 80.0 + capped - penalty // 基础 70 + 自评激励 10 = 80
        val notes = mutableListOf<String>()
        if (raw > 20.0) notes += "美育奖励合计 $raw 分，超出上限，按 20 分计"
        if (draft.meiVeto) notes += "抄袭/违规，美育成绩记 0 分"
        return ModuleResult(r2(z), capped, raw, penalty, raw > 20.0, notes)
    }

    // ---------- M5 劳动等次 ----------

    fun m5Labor(draft: ZongceDraft): String {
        val flags = draft.labFlags
        // D：寝室低于三星且校级通报批评≥3 次；或破坏/不尊重行为；或其他重大负面影响
        if ((draft.dormStar < 1 && draft.labCriticism >= 3) ||
            ZongceLabFlags.DESTROY in flags ||
            ZongceLabFlags.MAJOR_NEGATIVE in flags
        ) return "D"
        // 达 C 后升 B：四星及以上寝室 / 志愿>6 次 / 寒暑假实践校级荣誉 / 劳动竞赛省级以下荣誉
        val reachB = draft.dormStar >= 2 || draft.volunteerCount > 6 ||
            ZongceLabFlags.SUMMER_HONOR in flags ||
            ZongceLabFlags.CONTEST_BELOW_PROVINCE in flags
        // 达 B 后升 A：五星寝室 / 志愿>10 次 / 省级及以上媒体报道 / 劳动竞赛省级及以上荣誉
        val reachA = draft.dormStar >= 3 || draft.volunteerCount > 10 ||
            ZongceLabFlags.MEDIA_PROVINCE in flags ||
            ZongceLabFlags.CONTEST_PROVINCE in flags
        return when {
            reachB && reachA -> "A"
            reachB -> "B"
            else -> "C"
        }
    }

    // ---------- M7 合成与等次 ----------

    fun finalize(draft: ZongceDraft): FinalResult {
        val m1 = m1De(draft)
        val m2 = m2Zhi(draft)
        val m3 = m3Ti(draft)
        val m4 = m4Mei(draft)
        val z5 = r2(max(0.0, draft.gpaX2 - draft.gpaX1))
        val t = r2(m1.z * 0.2 + (m2.z + z5) * 0.6 + m3.z * 0.1 + m4.z * 0.1)
        val laborGrade = m5Labor(draft)
        val oneVote = draft.oneVoteExcellent ||
            draft.awards.any { it.value == ZongceTables.VOTE_EXCELLENT }
        val deDirectD = draft.deVetoD
        // 等次（无排名输入，班级排名由学院评定时用户以一票定优/最终等次为准）：
        // 劳动开关开启时按手册要求叠加劳动等次前置条件；关闭则仅看 T 与一票定优/直判
        val labor = if (draft.includeLabor) laborGrade else null
        val laborOn = draft.includeLabor
        val grade = when {
            deDirectD -> "D"
            oneVote -> "A"
            t > 75.0 && (!laborOn || laborAtLeast(labor!!, "B")) -> "A"
            t >= 65.0 && (!laborOn || laborAtLeast(labor!!, "C")) -> "B"
            t >= 60.0 && (!laborOn || laborAtLeast(labor!!, "C")) -> "C"
            else -> "D"
        }
        return FinalResult(m1.z, m2.z, m3.z, m4.z, z5, t, grade, laborGrade, oneVote, deDirectD)
    }

    private fun laborAtLeast(labor: String, minGrade: String): Boolean =
        laborRank(labor) >= laborRank(minGrade)

    private fun laborRank(g: String): Int = when (g) {
        "A" -> 3
        "B" -> 2
        "C" -> 1
        else -> 0
    }

    // ---------- 公式代值展示 ----------

    /**
     * 计算过程逐行展示（供结果页"计算公式"区，让他人核对计算正确性）。
     * 每行为"公式 = 带入值 = 结果"的形式。
     */
    fun explain(draft: ZongceDraft): List<String> {
        val m1 = m1De(draft)
        val m2 = m2Zhi(draft)
        val m3 = m3Ti(draft)
        val m4 = m4Mei(draft)
        val labor = m5Labor(draft)
        val z5 = r2(max(0.0, draft.gpaX2 - draft.gpaX1))
        val t = r2(m1.z * 0.2 + (m2.z + z5) * 0.6 + m3.z * 0.1 + m4.z * 0.1)
        val f = { v: Double -> trimZero(v) }
        val lines = mutableListOf<String>()
        lines += "Z1 = 80 + min(20, 奖励 ${f(m1.rawBonus)}) − 处罚 ${f(m1.penalty)} = ${f(m1.z)}"
        lines += "Z2 = 学分绩 ${f(draft.avgScore)}×0.8 + (min(100, 奖励 ${f(m2.rawBonus)}) − 处罚 ${f(m2.penalty)})×0.2 = ${f(m2.z)}"
        val club = if (draft.peClubDone && draft.peGradeLevel == 1) 10.0 else 0.0
        val run = if (draft.peRunDone && draft.peGradeLevel == 1) 20.0 else 0.0
        val clubRun = if (draft.peGradeLevel == 1) " + 俱乐部 $club + 乐跑 $run" else ""
        lines += "Z3 = 体测 ${f(testBaseOf(draft))}$clubRun + min(30, 奖励 ${f(m3.rawBonus)}) − 处罚 ${f(m3.penalty)} = ${f(m3.z)}"
        lines += "Z4 = 80 + min(20, 奖励 ${f(m4.rawBonus)}) − 处罚 ${f(m4.penalty)} = ${f(m4.z)}"
        lines += "Z5 = max(0, ${f(draft.gpaX2)} − ${f(draft.gpaX1)}) = ${f(z5)}"
        lines += "T = Z1×20% + (Z2+Z5)×60% + Z3×10% + Z4×10%"
        lines += "   = ${f(m1.z)}×0.2 + (${f(m2.z)}+${f(z5)})×0.6 + ${f(m3.z)}×0.1 + ${f(m4.z)}×0.1 = ${f(t)}"
        val laborPart = if (draft.includeLabor) "劳动 $labor" else "劳动等次已关闭"
        lines += "等次评定：一票定优 ${if (oneVote(draft)) "是" else "否"}、$laborPart → 综测等次 ${finalize(draft).grade}"
        return lines
    }

    private fun testBaseOf(draft: ZongceDraft): Double = when (draft.peTest) {
        1 -> if (draft.peGradeLevel == 1) 28.0 else 50.0
        2 -> 60.0
        else -> if (draft.peGradeLevel == 1) 40.0 else 70.0
    }

    private fun oneVote(draft: ZongceDraft): Boolean =
        draft.oneVoteExcellent || draft.awards.any { it.value == ZongceTables.VOTE_EXCELLENT }

    /** 去掉末尾多余的 .0 */
    private fun trimZero(v: Double): String {
        val s = "%.2f".format(v)
        return if (s.endsWith(".00")) s.dropLast(3) else if (s.endsWith("0")) s.dropLast(1) else s
    }
}
