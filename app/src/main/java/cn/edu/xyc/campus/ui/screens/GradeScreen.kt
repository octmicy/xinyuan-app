package cn.edu.xyc.campus.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cn.edu.xyc.campus.data.local.ScheduleCache
import cn.edu.xyc.campus.data.model.GradeItem
import cn.edu.xyc.campus.data.remote.JwxtApi
import cn.edu.xyc.campus.data.remote.JwxtResult
import cn.edu.xyc.campus.data.remote.TermUtils

private data class GpaSummary(val gpa: Double, val credits: Double, val count: Int)

private fun summarize(items: List<GradeItem>): GpaSummary {
    val valid = items.filter { it.credit > 0 && it.gradePoint > 0 }
    val credits = valid.sumOf { it.credit }
    val gpa = if (credits > 0) valid.sumOf { it.gradePoint * it.credit } / credits else 0.0
    return GpaSummary(gpa, items.sumOf { it.credit }, items.size)
}

@Composable
fun GradeScreen() {
    val curTerm = remember { TermUtils.current() }
    var selXnm by rememberSaveable { mutableStateOf(curTerm.xnm) }
    var termNo by rememberSaveable { mutableStateOf(curTerm.termNo) }
    var loading by rememberSaveable { mutableStateOf(true) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var grades by remember { mutableStateOf<List<GradeItem>>(emptyList()) }
    var reloadKey by rememberSaveable { mutableStateOf(0) }
    val yearOptions = remember {
        val y = curTerm.xnm.toIntOrNull() ?: 2026
        (0..3).map { (y - it).toString() }
    }

    LaunchedEffect(selXnm, termNo, reloadKey) {
        val xqm = TermUtils.of(selXnm, termNo).xqm
        val key = ScheduleCache.gradeKey(selXnm, xqm)
        // 命中缓存零等待
        ScheduleCache.gradeData[key]?.let {
            grades = it
            loading = false
            return@LaunchedEffect
        }
        if (!ScheduleCache.tryMark(key)) return@LaunchedEffect
        loading = true
        error = null
        when (val r = JwxtApi.getGrades(TermUtils.of(selXnm, termNo))) {
            is JwxtResult.Ok -> {
                ScheduleCache.gradeData[key] = r.data
                grades = r.data
            }
            is JwxtResult.SessionExpired -> error = r.message
            is JwxtResult.Failed -> error = r.message
        }
        ScheduleCache.unmark(key)
        loading = false
    }

    Column(Modifier.fillMaxSize()) {
        Text(
            "成绩查询",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        )

        // 学年切换
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            yearOptions.forEach { y ->
                FilterChip(
                    selected = selXnm == y,
                    onClick = { selXnm = y },
                    label = { Text(TermUtils.xnmToLabel(y)) },
                )
            }
        }

        // 学期切换
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            (1..3).forEach { n ->
                FilterChip(
                    selected = termNo == n,
                    onClick = { termNo = n },
                    label = { Text("第${n}学期") },
                )
            }
        }

        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                androidx.compose.material3.CircularProgressIndicator()
            }
            error != null -> ErrorPane(error!!, onRetry = { reloadKey++ })
            grades.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "本学期暂无成绩",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            else -> {
                val sum = summarize(grades)
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                ) {
                    // 校色蓝渐变汇总卡
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.horizontalGradient(listOf(Color(0xFF1E5AA8), Color(0xFF3E7CD1))),
                                RoundedCornerShape(18.dp),
                            )
                            .padding(vertical = 14.dp),
                    ) {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            MetricCell("%.2f".format(sum.gpa), "平均绩点", Modifier.weight(1f))
                            MetricCell("${sum.credits}", "总学分", Modifier.weight(1f))
                            MetricCell("${sum.count}", "课程数", Modifier.weight(1f))
                        }
                    }
                }
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = 16.dp, vertical = 4.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(grades, key = { it.courseId + it.courseName }) { g ->
                        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        g.courseName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        "${g.nature} · ${g.credit}学分 · ${g.teacher}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    val (fg, bg) = scoreColors(g)
                                    Box(
                                        Modifier
                                            .background(bg, RoundedCornerShape(10.dp))
                                            .padding(horizontal = 10.dp, vertical = 4.dp),
                                    ) {
                                        Text(
                                            g.score,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = fg,
                                        )
                                    }
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        "绩点 ${g.gradePoint}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 分数徽标配色：<60 红 / ≥85 绿 / ≥70 校色蓝 / 其余琥珀（等第制跟随及格态） */
private fun scoreColors(g: GradeItem): Pair<Color, Color> {
    val s = g.scoreNumeric
    return when {
        s != null && s < 60 -> Color(0xFFC62828) to Color(0xFFFDEBEA)
        s != null && s >= 85 -> Color(0xFF2E7D32) to Color(0xFFE7F4E8)
        s != null && s >= 70 -> Color(0xFF1E5AA8) to Color(0xFFE3EFFF)
        s != null -> Color(0xFF9A7B0A) to Color(0xFFFFF6DC)
        g.pass -> Color(0xFF1E5AA8) to Color(0xFFE3EFFF)
        else -> Color(0xFFC62828) to Color(0xFFFDEBEA)
    }
}

@Composable
private fun MetricCell(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = Color.White,
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.85f),
        )
    }
}
