package cn.edu.xyc.campus.data.grades

import cn.edu.xyc.campus.data.model.GradeItem

/**
 * 平均学分绩计算（纯函数，不依赖 Android）：
 * - 合并去重：跨学期同课程取 termNo 最小的记录（重修高分不采用）；
 *   同学期同课程多条取分数最高（scoreNumeric 比较，null 视为最低，并列取第一条）
 * - 课程判同：courseId.trim() 非空按 courseId，否则按 courseName.trim()
 * - 默认勾选：非「创新创业」（nature/category 含字样）且成绩可解析为数字且学分 > 0
 * - 平均学分绩 = Σ(成绩×学分) ÷ Σ(学分)，结果保留两位小数（r2）
 * - 空数据 / 全部被排除 / 全是等第制 → avgScore = null
 * 展示串统一用 trimZero 去尾零（参照 ZongceCalculator）。
 */
object AvgScoreCalculator {

    /** 勾选粒度的科目（对应 dedupe 后的一条成绩记录） */
    data class Subject(
        val key: String,          // 勾选标识：courseId 非空用 courseId，否则 courseName
        val courseName: String,
        val courseId: String,
        val termNo: Int,          // 该科目取自的学期
        val credit: Double,
        val score: String,
        val scoreNumeric: Double?,
        val nature: String,
        val category: String,
        val defaultSelected: Boolean,
    )

    /** 计算结果 */
    data class Result(
        val avgScore: Double?,     // null = 无可计算科目（空数据/全部被排除/全是等第制）
        val totalCredit: Double,   // 参与计算科目的学分和
        val count: Int,            // 参与计算科目数
        val formulaLine: String,   // "平均学分绩 = Σ(成绩×学分) ÷ Σ(学分) = xx.xx"
        val details: List<String>, // 每科一行："工程材料 85×3"，供 UI 展示带入分数
    )

    // ---------- 工具 ----------

    private fun r2(v: Double): Double = Math.round(v * 100.0) / 100.0

    /** 去掉末尾多余的 .0（参照 ZongceCalculator.trimZero） */
    private fun trimZero(v: Double): String {
        val s = "%.2f".format(v)
        return if (s.endsWith(".00")) s.dropLast(3) else if (s.endsWith("0")) s.dropLast(1) else s
    }

    /** 课程判同 key：courseId.trim() 非空按 courseId，否则按 courseName.trim() */
    private fun courseKey(item: GradeItem): String {
        val id = item.courseId.trim()
        return if (id.isNotEmpty()) id else item.courseName.trim()
    }

    // ---------- 合并去重 ----------

    /**
     * 合并去重：按课程 key 分组后，
     * - 跨学期取 termNo 最小的记录（即使重修高分也不采用）；
     * - 同学期多条取 scoreNumeric 最高（null 视为最低，并列取第一条）。
     */
    fun dedupe(items: List<GradeItem>): List<GradeItem> = items
        .groupBy { courseKey(it) }
        .map { (_, group) ->
            val minTerm = group.minOf { it.termNo }
            group.filter { it.termNo == minTerm }.reduce { best, next ->
                val b = best.scoreNumeric ?: Double.NEGATIVE_INFINITY
                val n = next.scoreNumeric ?: Double.NEGATIVE_INFINITY
                if (n > b) next else best
            }
        }

    /** dedupe 后的记录映射为可勾选科目（defaultSelected 仅为 UI 默认勾选态） */
    fun subjects(deduped: List<GradeItem>): List<Subject> = deduped.map { item ->
        val innovation = item.nature.contains("创新创业") || item.category.contains("创新创业")
        Subject(
            key = courseKey(item),
            courseName = item.courseName,
            courseId = item.courseId,
            termNo = item.termNo,
            credit = item.credit,
            score = item.score,
            scoreNumeric = item.scoreNumeric,
            nature = item.nature,
            category = item.category,
            defaultSelected = !innovation && item.scoreNumeric != null && item.credit > 0,
        )
    }

    /**
     * 按排除集合计算平均学分绩：只统计 key 未被排除、scoreNumeric 非空且学分 > 0 的科目。
     * 无可计算科目时 avgScore = null、totalCredit = 0.0、count = 0、details 为空。
     */
    fun compute(subjects: List<Subject>, excludedKeys: Set<String>): Result {
        val joined = subjects.filter {
            it.key !in excludedKeys && it.scoreNumeric != null && it.credit > 0
        }
        if (joined.isEmpty()) {
            return Result(null, 0.0, 0, "平均学分绩 = Σ(成绩×学分) ÷ Σ(学分) = —", emptyList())
        }
        val totalCredit = r2(joined.sumOf { it.credit })
        val weighted = joined.sumOf { it.scoreNumeric!! * it.credit }
        val avg = r2(weighted / totalCredit)
        return Result(
            avgScore = avg,
            totalCredit = totalCredit,
            count = joined.size,
            formulaLine = "平均学分绩 = Σ(成绩×学分) ÷ Σ(学分) = ${trimZero(avg)}",
            details = joined.map {
                "${it.courseName} ${trimZero(it.scoreNumeric!!)}×${trimZero(it.credit)}"
            },
        )
    }
}
