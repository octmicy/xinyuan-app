package cn.edu.xyc.campus

import cn.edu.xyc.campus.data.grades.AvgScoreCalculator
import cn.edu.xyc.campus.data.model.GradeItem
import cn.edu.xyc.campus.data.remote.TermUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 平均学分绩计算器单元测试（纯函数，不依赖 Android）。
 */
class AvgScoreCalculatorTest {

    private fun grade(
        courseName: String,
        courseId: String = courseName,
        termNo: Int = 1,
        credit: Double,
        score: String,
        nature: String = "公共必修",
        category: String = "",
    ): GradeItem = GradeItem(
        termName = "2025-2026",
        termNo = termNo,
        courseName = courseName,
        courseId = courseId,
        nature = nature,
        category = category,
        credit = credit,
        score = score,
        gradePoint = 0.0,
        teacher = "张老师",
        school = "材料学院",
        pass = true,
    )

    // ---------- 学年合并 ----------

    @Test
    fun merge_bothTermsEnterSubjects() {
        val items = listOf(
            grade("高等数学", termNo = 1, credit = 4.0, score = "85"),
            grade("大学英语", termNo = 2, credit = 3.0, score = "90"),
        )
        val subs = AvgScoreCalculator.subjects(AvgScoreCalculator.dedupe(items))
        assertEquals(2, subs.size)
        assertEquals(listOf(1, 2), subs.map { it.termNo })
        assertEquals(listOf("高等数学", "大学英语"), subs.map { it.courseName })
        assertTrue(subs.all { it.defaultSelected })
    }

    // ---------- 跨学期去重 ----------

    @Test
    fun dedupe_crossTerm_keepsSmallestTerm() {
        // 工程材料跨学期：termNo=1 与 termNo=2 各一条，只剩 termNo=1（即使输入顺序 2 在前）
        val items = listOf(
            grade("工程材料", termNo = 2, credit = 3.0, score = "92"),
            grade("工程材料", termNo = 1, credit = 3.0, score = "78"),
        )
        val deduped = AvgScoreCalculator.dedupe(items)
        assertEquals(1, deduped.size)
        assertEquals(1, deduped[0].termNo)
        assertEquals("78", deduped[0].score)
    }

    @Test
    fun dedupe_retakeHighScore_notAdopted() {
        // 重修高分不采用：第一学期 78、第二学期 92，计算仍用 78
        val items = listOf(
            grade("工程材料", termNo = 1, credit = 3.0, score = "78"),
            grade("工程材料", termNo = 2, credit = 3.0, score = "92"),
        )
        val subs = AvgScoreCalculator.subjects(AvgScoreCalculator.dedupe(items))
        val r = AvgScoreCalculator.compute(subs, emptySet())
        assertEquals(1, r.count)
        assertEquals(78.0, r.avgScore!!, 1e-9)
        assertEquals(listOf("工程材料 78×3"), r.details)
    }

    // ---------- 同学期重复 ----------

    @Test
    fun dedupe_sameTerm_takesHighestScore() {
        val items = listOf(
            grade("大学英语", termNo = 1, credit = 2.0, score = "80"),
            grade("大学英语", termNo = 1, credit = 2.0, score = "90"),
            grade("大学英语", termNo = 1, credit = 2.0, score = "85"),
        )
        val deduped = AvgScoreCalculator.dedupe(items)
        assertEquals(1, deduped.size)
        assertEquals("90", deduped[0].score)
    }

    @Test
    fun dedupe_sameTerm_nullVsNumber_takesNumber() {
        // null 分数视为最低：有数字分时优先保留数字分
        val items = listOf(
            grade("大学英语", termNo = 1, credit = 2.0, score = "免修"),
            grade("大学英语", termNo = 1, credit = 2.0, score = "88"),
        )
        assertEquals("88", AvgScoreCalculator.dedupe(items).single().score)
    }

    @Test
    fun dedupe_sameTerm_allNull_takesFirst() {
        val items = listOf(
            grade("劳动实践", termNo = 1, credit = 1.0, score = "合格"),
            grade("劳动实践", termNo = 1, credit = 1.0, score = "优秀"),
        )
        assertEquals("合格", AvgScoreCalculator.dedupe(items).single().score)
    }

    @Test
    fun dedupe_blankCourseId_fallsBackToCourseName() {
        // courseId 为空白时按 courseName 判同
        val items = listOf(
            grade("大学英语", courseId = " ", termNo = 1, credit = 2.0, score = "80"),
            grade("大学英语", courseId = "", termNo = 2, credit = 2.0, score = "88"),
        )
        val deduped = AvgScoreCalculator.dedupe(items)
        assertEquals(1, deduped.size)
        assertEquals(1, deduped[0].termNo)
        assertEquals("大学英语", AvgScoreCalculator.subjects(deduped).single().key)
    }

    // ---------- 创新创业默认排除 ----------

    @Test
    fun innovation_natureDefaultNotSelected_manualExcludeWorks() {
        val items = listOf(
            grade("创新实践", credit = 2.0, score = "90", nature = "创新创业"),
            grade("高等数学", credit = 4.0, score = "85"),
        )
        val subs = AvgScoreCalculator.subjects(AvgScoreCalculator.dedupe(items))
        val innovation = subs.first { it.courseName == "创新实践" }
        assertFalse(innovation.defaultSelected)
        assertTrue(subs.first { it.courseName == "高等数学" }.defaultSelected)
        // compute 只看 excludedKeys：默认未勾选的科目不传入 excludedKeys 时仍会计入
        assertEquals(2, AvgScoreCalculator.compute(subs, emptySet()).count)
        // excludedKeys 手动传入也能排除
        val r = AvgScoreCalculator.compute(subs, setOf(innovation.key))
        assertEquals(1, r.count)
        assertEquals(4.0, r.totalCredit, 1e-9)
        assertEquals(85.0, r.avgScore!!, 1e-9)
    }

    @Test
    fun innovation_categoryAlsoNotSelected() {
        val subs = AvgScoreCalculator.subjects(
            listOf(grade("创新创业基础", credit = 1.0, score = "80", category = "创新创业类")),
        )
        assertFalse(subs.single().defaultSelected)
    }

    // ---------- 取消勾选重算 ----------

    @Test
    fun deselect_recompute_changesResult() {
        val subs = AvgScoreCalculator.subjects(
            listOf(
                grade("工程材料", credit = 3.0, score = "85"),
                grade("高等数学", credit = 4.0, score = "90"),
            ),
        )
        val full = AvgScoreCalculator.compute(subs, emptySet())
        assertEquals(87.86, full.avgScore!!, 1e-9) // 615 ÷ 7
        assertEquals(7.0, full.totalCredit, 1e-9)
        assertEquals(2, full.count)
        // 取消工程材料：只剩 90×4
        val key = subs.first { it.courseName == "工程材料" }.key
        val r = AvgScoreCalculator.compute(subs, setOf(key))
        assertEquals(90.0, r.avgScore!!, 1e-9) // 360 ÷ 4
        assertEquals(4.0, r.totalCredit, 1e-9)
        assertEquals(1, r.count)
        assertEquals(listOf("高等数学 90×4"), r.details)
    }

    // ---------- 等第制 ----------

    @Test
    fun gradeLevelScore_skipped() {
        val items = listOf(
            grade("军事理论", credit = 2.0, score = "优"),
            grade("劳动教育", credit = 1.0, score = "良"),
            grade("高等数学", credit = 4.0, score = "88"),
        )
        val subs = AvgScoreCalculator.subjects(AvgScoreCalculator.dedupe(items))
        subs.filter { it.scoreNumeric == null }.forEach { assertFalse(it.defaultSelected) }
        val r = AvgScoreCalculator.compute(subs, emptySet())
        assertEquals(1, r.count)
        assertEquals(4.0, r.totalCredit, 1e-9)
        assertEquals(88.0, r.avgScore!!, 1e-9)
    }

    // ---------- 学分为 0 ----------

    @Test
    fun zeroCredit_notCounted() {
        val subs = AvgScoreCalculator.subjects(
            listOf(
                grade("讲座", credit = 0.0, score = "95"),
                grade("高等数学", credit = 4.0, score = "85"),
            ),
        )
        assertFalse(subs.first { it.courseName == "讲座" }.defaultSelected)
        val r = AvgScoreCalculator.compute(subs, emptySet())
        assertEquals(1, r.count)
        assertEquals(85.0, r.avgScore!!, 1e-9)
    }

    // ---------- 空数据 / 全部排除 ----------

    @Test
    fun emptyItems_nullResult() {
        val r = AvgScoreCalculator.compute(emptyList(), emptySet())
        assertNull(r.avgScore)
        assertEquals(0.0, r.totalCredit, 1e-9)
        assertEquals(0, r.count)
        assertTrue(r.details.isEmpty())
    }

    @Test
    fun allExcluded_nullResult() {
        val subs = AvgScoreCalculator.subjects(
            listOf(grade("高等数学", credit = 4.0, score = "85")),
        )
        val r = AvgScoreCalculator.compute(subs, subs.map { it.key }.toSet())
        assertNull(r.avgScore)
        assertEquals(0.0, r.totalCredit, 1e-9)
        assertEquals(0, r.count)
        assertTrue(r.details.isEmpty())
    }

    @Test
    fun allGradeLevel_nullResult() {
        val subs = AvgScoreCalculator.subjects(
            listOf(grade("军事理论", credit = 2.0, score = "优")),
        )
        val r = AvgScoreCalculator.compute(subs, emptySet())
        assertNull(r.avgScore)
        assertEquals(0, r.count)
        assertTrue(r.details.isEmpty())
    }

    // ---------- 真实接口 termNo 编码（xqm: 3/12/16）----------

    @Test
    fun dedupe_realXqmEncoding_keepsFirstTerm() {
        // 接口返回 termNo 为 xqm 编码（3=第1学期 12=第2学期 16=第3学期），相对大小仍正确去重
        val items = listOf(
            grade("工程材料", termNo = 12, credit = 3.0, score = "92"),
            grade("工程材料", termNo = 3, credit = 3.0, score = "78"),
            grade("大学英语", termNo = 16, credit = 2.0, score = "88"),
        )
        val deduped = AvgScoreCalculator.dedupe(items)
        assertEquals(2, deduped.size)
        assertEquals(3, deduped.first { it.courseName == "工程材料" }.termNo)
        assertEquals("78", deduped.first { it.courseName == "工程材料" }.score)
    }

    @Test
    fun xqmToTermNo_mapsRequestEncoding() {
        assertEquals(1, TermUtils.xqmToTermNo(3))
        assertEquals(2, TermUtils.xqmToTermNo(12))
        assertEquals(3, TermUtils.xqmToTermNo(16))
        // 未知值原样返回（防御）
        assertEquals(0, TermUtils.xqmToTermNo(0))
        assertEquals(99, TermUtils.xqmToTermNo(99))
    }

    // ---------- 公式与展示 ----------

    @Test
    fun formula_twoSubjects() {
        val subs = AvgScoreCalculator.subjects(
            listOf(
                grade("工程材料", credit = 3.0, score = "85"),
                grade("高等数学", credit = 4.0, score = "90"),
            ),
        )
        val r = AvgScoreCalculator.compute(subs, emptySet())
        // (85×3 + 90×4) ÷ 7 = 615 ÷ 7 = 87.857… → 87.86
        assertEquals(87.86, r.avgScore!!, 1e-9)
        assertEquals(7.0, r.totalCredit, 1e-9)
        assertEquals(2, r.count)
        assertEquals("平均学分绩 = Σ(成绩×学分) ÷ Σ(学分) = 87.86", r.formulaLine)
        assertEquals(listOf("工程材料 85×3", "高等数学 90×4"), r.details)
    }
}
