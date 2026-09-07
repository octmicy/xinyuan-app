package cn.edu.xyc.campus

import cn.edu.xyc.campus.data.zongce.ZongceAwardRecord
import cn.edu.xyc.campus.data.zongce.ZongceCalculator
import cn.edu.xyc.campus.data.zongce.ZongceDraft
import cn.edu.xyc.campus.data.zongce.ZongceModules
import cn.edu.xyc.campus.data.zongce.ZongcePenalty
import cn.edu.xyc.campus.data.zongce.ZongceTables
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 综测计算器单元测试（纯函数，不依赖 Android）。
 */
class ZongceCalculatorTest {

    private var nextId = 1L

    private fun award(
        module: String,
        category: String,
        level: String = ZongceTables.LEVEL_NONE,
        grade: String = ZongceTables.GRADE_RECOGNIZED,
        value: Double,
        sameProject: Boolean = false,
    ): ZongceAwardRecord = ZongceAwardRecord(nextId++, module, category, level, grade, value, sameProject)

    /**
     * 基准草稿（大三大四档）：Z1=80、Z2=avgScore×0.8、Z3=70（体测合格）、Z4=80（基础70+自评10）、
     * Z5=0（X1=X2=0）、劳动 C（默认）。
     * T = 16 + 0.6×Z2 + 7 + 8 = 31 + 0.48×avgScore
     */
    private fun baseDraft(avgScore: Double): ZongceDraft = ZongceDraft(
        yearKey = "2026-2027",
        avgScore = avgScore,
        peGradeLevel = 2,
        peTest = 0,
    )

    // ---------- 标准算例 ----------

    @Test
    fun standardExample_T_followsFormula() {
        val draft = ZongceDraft(
            yearKey = "2026-2027",
            avgScore = 100.0,                              // Z2 = 100×0.8 = 80
            peGradeLevel = 1, peTest = 0, peRunDone = true, // Z3 = 40 + 0 + 20 = 60
            gpaX1 = 3.0, gpaX2 = 3.5,                      // Z5 = 0.5
        )
        val r = ZongceCalculator.finalize(draft)
        assertEquals(80.0, r.z1, 1e-9)
        assertEquals(80.0, r.z2, 1e-9)
        assertEquals(60.0, r.z3, 1e-9)
        assertEquals(80.0, r.z4, 1e-9) // 70 + 自评 10
        assertEquals(0.5, r.z5, 1e-9)
        // 80×0.2 + 80.5×0.6 + 60×0.1 + 80×0.1 = 16 + 48.3 + 6 + 8 = 78.3
        assertEquals(78.3, r.t, 0.01)
        // 无排名输入：T>75 但劳动 C（A 需劳动≥B）→ B
        assertEquals("B", r.grade)
    }

    // ---------- 德育 ----------

    @Test
    fun de_bonusCappedAt20() {
        val draft = ZongceDraft(
            awards = mutableListOf(
                award(ZongceModules.DE, ZongceTables.DE_DEED, ZongceTables.LEVEL_NATIONAL, value = 10.0),
                award(ZongceModules.DE, ZongceTables.DE_DEED, ZongceTables.LEVEL_PROVINCE, value = 8.0),
                award(ZongceModules.DE, ZongceTables.DE_DEED, ZongceTables.LEVEL_COLLEGE, value = 7.0),
            ),
        )
        val m = ZongceCalculator.m1De(draft)
        assertEquals(20.0, m.bonus, 1e-9) // 25 → 封顶 20
        assertEquals(25.0, m.rawBonus, 1e-9) // 封顶前原始合计
        assertTrue(m.bonusCapped)
        assertEquals(100.0, m.z, 1e-9)    // 80 + 20
    }

    @Test
    fun de_honorPersonAndGroup_takeMaxEach() {
        val draft = ZongceDraft(
            awards = mutableListOf(
                award(ZongceModules.DE, ZongceTables.DE_HONOR_PERSON, ZongceTables.LEVEL_SCHOOL, value = 5.0),
                award(ZongceModules.DE, ZongceTables.DE_HONOR_PERSON, ZongceTables.LEVEL_COLLEGE, value = 2.0),
                award(ZongceModules.DE, ZongceTables.DE_HONOR_GROUP, ZongceTables.LEVEL_SCHOOL, value = 5.0),
            ),
        )
        val m = ZongceCalculator.m1De(draft)
        assertEquals(10.0, m.bonus, 1e-9) // max(5,2) + 5 = 10
        assertFalse(m.bonusCapped)
        assertEquals(90.0, m.z, 1e-9)
    }

    @Test
    fun de_honorCombinedCappedAt10() {
        val draft = ZongceDraft(
            awards = mutableListOf(
                award(ZongceModules.DE, ZongceTables.DE_HONOR_PERSON, ZongceTables.LEVEL_NATIONAL, value = 10.0),
                award(ZongceModules.DE, ZongceTables.DE_HONOR_GROUP, ZongceTables.LEVEL_NATIONAL, value = 10.0),
            ),
        )
        val m = ZongceCalculator.m1De(draft)
        assertEquals(10.0, m.bonus, 1e-9) // (10+10) 荣誉合计封顶 10
        assertFalse(m.bonusCapped)
    }

    // ---------- 智育 ----------

    @Test
    fun zhi_specialPrize130_andCappedAt100() {
        // 特等奖 = 同级别一等 × 1.3 = 100 × 1.3 = 130
        assertEquals(
            130.0,
            ZongceTables.lookup(ZongceTables.ZHI_CONTEST_RANKED, ZongceTables.LEVEL_NATIONAL, ZongceTables.GRADE_SPECIAL),
            1e-9,
        )
        val draft = ZongceDraft(
            awards = mutableListOf(
                award(
                    ZongceModules.ZHI, ZongceTables.ZHI_CONTEST_RANKED,
                    ZongceTables.LEVEL_NATIONAL, ZongceTables.GRADE_SPECIAL, value = 130.0,
                ),
            ),
        )
        val m = ZongceCalculator.m2Zhi(draft)
        assertEquals(100.0, m.bonus, 1e-9) // 130 → 封顶 100
        assertTrue(m.bonusCapped)
        assertEquals(20.0, m.z, 1e-9)      // avgScore=0 → 100×0.2
    }

    // ---------- Z5 ----------

    @Test
    fun z5_negativeClampedToZero() {
        val draft = baseDraft(75.0).copy(gpaX1 = 3.5, gpaX2 = 3.0)
        val r = ZongceCalculator.finalize(draft)
        assertEquals(0.0, r.z5, 1e-9)
    }

    // ---------- 等次评定（无排名输入，劳动开关） ----------

    @Test
    fun grade_T61_laborC_isC() {
        // T = 31 + 0.48×62.5 = 61（基准大三大四档，无处罚）
        val draft = baseDraft(62.5)
        val r = ZongceCalculator.finalize(draft)
        assertEquals(61.0, r.t, 1e-9)
        assertEquals("C", r.grade)
    }

    @Test
    fun grade_T75_laborB_isNotA_boundaryExcluded() {
        // 大一大二：体测40 + 俱乐部10 + 乐跑20 − 缺勤10 = 60；T = 16 + 0.6×75 + 6 + 8 = 75（A 不含 75）
        val draft = baseDraft(93.75).copy(
            peGradeLevel = 1, peTest = 0, peClubDone = true, peRunDone = true,
            penalties = mapOf(ZongcePenalty.TI_ABSENT to 5),
            dormStar = 2, // 劳动 B
        )
        val r = ZongceCalculator.finalize(draft)
        assertEquals(75.0, r.t, 1e-9)
        assertEquals("B", r.laborGrade)
        assertEquals("B", r.grade)
    }

    @Test
    fun grade_T75_1_laborB_isA() {
        val draft = baseDraft(93.75).copy(
            peGradeLevel = 1, peTest = 0, peClubDone = true, peRunDone = true,
            penalties = mapOf(ZongcePenalty.TI_ABSENT to 5),
            dormStar = 2,
            awards = mutableListOf(award(ZongceModules.DE, "custom", value = 0.5)), // Z1=80.5 → T=75.1
        )
        val r = ZongceCalculator.finalize(draft)
        assertEquals(75.1, r.t, 0.01)
        assertEquals("A", r.grade)
    }

    @Test
    fun grade_T67_laborC_isB() {
        // T = 31 + 0.48×75 = 67 ≥65 → B
        val draft = baseDraft(75.0)
        val r = ZongceCalculator.finalize(draft)
        assertEquals(67.0, r.t, 1e-9)
        assertEquals("B", r.grade)
    }

    @Test
    fun grade_T59_isD() {
        val draft = baseDraft(58.0) // T = 31 + 0.48×58 = 58.84
        val r = ZongceCalculator.finalize(draft)
        assertEquals(58.84, r.t, 0.01)
        assertEquals("D", r.grade)
    }

    @Test
    fun grade_oneVoteExcellent_isA_evenWithLowT() {
        val draft = baseDraft(62.5).copy(oneVoteExcellent = true) // T=61
        val r = ZongceCalculator.finalize(draft)
        assertTrue(r.oneVote)
        assertEquals("A", r.grade)
    }

    @Test
    fun grade_oneVoteFromMinus2Record() {
        val draft = baseDraft(62.5).copy(
            awards = mutableListOf(
                award(
                    ZongceModules.ZHI, ZongceTables.ZHI_INNOVATION,
                    ZongceTables.LEVEL_PROVINCE, ZongceTables.GRADE_GOLD, value = -2.0,
                ),
            ),
        )
        val r = ZongceCalculator.finalize(draft)
        assertTrue(r.oneVote)
        assertEquals("A", r.grade)
        // -2 是一票定优标记，不参与奖励分合计
        assertEquals(0.0, ZongceCalculator.m2Zhi(draft).bonus, 1e-9)
    }

    @Test
    fun grade_deDirectD_overridesOneVote() {
        val draft = baseDraft(62.5).copy(oneVoteExcellent = true, deDishonesty = true)
        val r = ZongceCalculator.finalize(draft)
        assertTrue(r.deDirectD)
        assertEquals("D", r.grade)
    }

    // ---------- 劳动等次开关 ----------

    @Test
    fun laborToggle_on_aborD_makesGradeD() {
        val draft = baseDraft(62.5).copy( // T=61
            includeLabor = true, dormStar = 0, labCriticism = 3, // 劳动 D
        )
        assertEquals("D", ZongceCalculator.finalize(draft).grade)
    }

    @Test
    fun laborToggle_off_ignoresLaborGrade() {
        val draft = baseDraft(62.5).copy( // T=61，劳动 D 但开关关闭
            includeLabor = false, dormStar = 0, labCriticism = 3,
        )
        val r = ZongceCalculator.finalize(draft)
        assertEquals("D", r.laborGrade) // 劳动等次仍展示
        assertEquals("C", r.grade)      // 但不参与等次评定：61≥60 → C
    }

    @Test
    fun laborToggle_off_highT_isA_withoutLaborB() {
        val draft = baseDraft(93.75).copy( // T=76 >75
            includeLabor = false, dormStar = 0, // 劳动 C（不满足 A 的劳动≥B）
        )
        assertEquals("A", ZongceCalculator.finalize(draft).grade)
    }

    @Test
    fun laborToggle_on_highT_laborC_notA() {
        val draft = baseDraft(93.75).copy( // T=76 >75，劳动 C
            includeLabor = true, dormStar = 0,
        )
        assertEquals("B", ZongceCalculator.finalize(draft).grade) // 76>75 但劳动 C → B
    }

    // ---------- 劳动规则链 ----------

    @Test
    fun labor_fiveStarDorm_isA() {
        assertEquals("A", ZongceCalculator.m5Labor(ZongceDraft(dormStar = 3)))
    }

    @Test
    fun labor_volunteer7_isB() {
        assertEquals("B", ZongceCalculator.m5Labor(ZongceDraft(volunteerCount = 7)))
    }

    @Test
    fun labor_volunteer11_isA() {
        assertEquals("A", ZongceCalculator.m5Labor(ZongceDraft(volunteerCount = 11)))
    }

    @Test
    fun labor_belowThreeStarWith3Criticism_isD() {
        assertEquals("D", ZongceCalculator.m5Labor(ZongceDraft(dormStar = 0, labCriticism = 3)))
    }

    @Test
    fun labor_default_isC() {
        assertEquals("C", ZongceCalculator.m5Labor(ZongceDraft()))
    }

    // ---------- 查表抽查 ----------

    @Test
    fun lookup_deIdeoActivity_provinceSecond_is8() {
        assertEquals(
            8.0,
            ZongceTables.lookup(ZongceTables.DE_IDEO_ACTIVITY, ZongceTables.LEVEL_PROVINCE, ZongceTables.GRADE_SECOND),
            1e-9,
        )
    }

    @Test
    fun lookup_zhiContestOther_provinceFirst_is20() {
        assertEquals(
            20.0,
            ZongceTables.lookup(ZongceTables.ZHI_CONTEST_OTHER, ZongceTables.LEVEL_PROVINCE, ZongceTables.GRADE_FIRST),
            1e-9,
        )
    }

    @Test
    fun lookup_meiAward_nationalThird_is10() {
        assertEquals(
            10.0,
            ZongceTables.lookup(ZongceTables.MEI_AWARD, ZongceTables.LEVEL_NATIONAL, ZongceTables.GRADE_THIRD),
            1e-9,
        )
    }

    @Test
    fun lookup_tiContest_collegeSecond_is4() {
        assertEquals(
            4.0,
            ZongceTables.lookup(ZongceTables.TI_CONTEST, ZongceTables.LEVEL_COLLEGE, ZongceTables.GRADE_SECOND),
            1e-9,
        )
    }

    @Test
    fun lookup_zhiInnovation_provinceGold_isOneVoteMarker() {
        assertEquals(
            -2.0,
            ZongceTables.lookup(ZongceTables.ZHI_INNOVATION, ZongceTables.LEVEL_PROVINCE, ZongceTables.GRADE_GOLD),
            1e-9,
        )
    }

    // ---------- 一票否决项 ----------

    @Test
    fun ti_veto_makesZ3Zero() {
        val draft = baseDraft(75.0).copy(tiVeto = true)
        val m = ZongceCalculator.m3Ti(draft)
        assertEquals(0.0, m.z, 1e-9)
    }

    @Test
    fun mei_veto_makesZ4Zero() {
        val draft = baseDraft(75.0).copy(meiVeto = true)
        val m = ZongceCalculator.m4Mei(draft)
        assertEquals(0.0, m.z, 1e-9)
    }

    // ---------- 体育年级分支（俱乐部/乐跑为完成勾选） ----------

    @Test
    fun ti_upperGrade_excludesClubAndRun() {
        val d = baseDraft(0.0).copy(
            peGradeLevel = 2, peTest = 0, peClubDone = true, peRunDone = true,
            penalties = ZongcePenalty.ALL_KEYS.associateWith { 0 },
        )
        val r = ZongceCalculator.m3Ti(d)
        // 大三大四：体测合格 70，即使勾了俱乐部/乐跑也不加
        assertEquals(70.0, r.z, 0.01)
        // 大一大二同输入：体测40 + 俱乐部10 + 乐跑20 = 70
        val r12 = ZongceCalculator.m3Ti(d.copy(peGradeLevel = 1))
        assertEquals(70.0, r12.z, 0.01)
        // 大一大二未完成：只有体测 40
        val r12none = ZongceCalculator.m3Ti(d.copy(peGradeLevel = 1, peClubDone = false, peRunDone = false))
        assertEquals(40.0, r12none.z, 0.01)
    }

    // ---------- 美育正常公式（自评 10 固定计入） ----------

    @Test
    fun mei_normalFormula_bonus() {
        val d = baseDraft(0.0).copy(
            awards = mutableListOf(
                award(
                    ZongceModules.MEI, ZongceTables.MEI_AWARD,
                    ZongceTables.LEVEL_NATIONAL, ZongceTables.GRADE_FIRST, 20.0,
                ),
            ),
            penalties = ZongcePenalty.ALL_KEYS.associateWith { 0 },
        )
        val r = ZongceCalculator.m4Mei(d)
        assertEquals(100.0, r.z, 0.01) // 80 + min(20,20)
    }

    // ---------- 公式代值行 ----------

    @Test
    fun explain_containsFormulaLines() {
        val draft = baseDraft(75.0)
        val lines = ZongceCalculator.explain(draft)
        assertTrue(lines.any { it.startsWith("T = Z1×20%") })
        assertTrue(lines.any { it.contains("Z1 = 80 + min(20, 奖励 0) − 处罚 0 = 80") })
        assertTrue(lines.any { it.contains("Z5 = max(0, 0 − 0) = 0") })
        assertTrue(lines.last().contains("综测等次"))
    }
}
