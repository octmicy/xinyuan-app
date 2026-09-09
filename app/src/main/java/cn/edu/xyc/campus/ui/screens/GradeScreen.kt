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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cn.edu.xyc.campus.data.grades.AvgScoreCalculator
import cn.edu.xyc.campus.data.local.AvgScorePrefs
import cn.edu.xyc.campus.data.local.ScheduleCache
import cn.edu.xyc.campus.data.model.GradeItem
import cn.edu.xyc.campus.data.remote.JwxtApi
import cn.edu.xyc.campus.data.remote.JwxtResult
import cn.edu.xyc.campus.data.remote.TermUtils
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay

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
    val context = LocalContext.current
    var selXnm by rememberSaveable { mutableStateOf(curTerm.xnm) }
    var termNo by rememberSaveable { mutableStateOf(curTerm.termNo) }
    var loading by rememberSaveable { mutableStateOf(true) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var grades by remember { mutableStateOf<List<GradeItem>>(emptyList()) }
    var reloadKey by rememberSaveable { mutableStateOf(0) }
    // 「平均学分绩」卡片显隐（右上角开关，全局持久化；隐藏后整卡移除不占位）
    var avgCardHidden by remember { mutableStateOf(AvgScorePrefs.isCardHidden(context)) }
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

        // 学年切换 + 右上角「平均学分绩」显隐开关（持久化；隐藏后卡片完全移除，不占位）
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                Modifier
                    .weight(1f)
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
            IconButton(
                onClick = {
                    avgCardHidden = !avgCardHidden
                    AvgScorePrefs.setCardHidden(context, avgCardHidden)
                },
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    if (avgCardHidden) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff,
                    contentDescription = if (avgCardHidden) "显示平均学分绩" else "隐藏平均学分绩",
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
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

        // 平均学分绩卡片（独立数据流，不影响下方成绩列表）；隐藏后整卡移除
        if (!avgCardHidden) AvgScoreCard(selXnm)

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

/** 平均学分绩卡片状态机（与列表数据流互不干扰） */
private sealed interface AvgState {
    data object Loading : AvgState
    data class Ok(val subjects: List<AvgScoreCalculator.Subject>) : AvgState
    data class Error(val message: String) : AvgState
}

/**
 * 拉取单个学期成绩：先读 ScheduleCache 缓存，未命中走网络并回写缓存。
 * 同 key 请求已在途（如成绩列表数据流）时轮询等待其写缓存，超时则直取兜底。
 */
private suspend fun loadTermGrades(term: TermUtils.Term): JwxtResult<List<GradeItem>> {
    val key = ScheduleCache.gradeKey(term.xnm, term.xqm)
    // 命中缓存零等待
    ScheduleCache.gradeData[key]?.let { return JwxtResult.Ok(it) }
    if (ScheduleCache.tryMark(key)) {
        try {
            val r = JwxtApi.getGrades(term)
            if (r is JwxtResult.Ok) ScheduleCache.gradeData[key] = r.data
            return r
        } finally {
            ScheduleCache.unmark(key)
        }
    }
    var waited = 0
    while (waited < 5000) {
        delay(100)
        ScheduleCache.gradeData[key]?.let { return JwxtResult.Ok(it) }
        waited += 100
    }
    return JwxtApi.getGrades(term)
}

/**
 * 「平均学分绩」卡片：并发拉取本学年三个学期成绩，合并去重后加权平均；
 * 勾选科目按学年持久化（AvgScorePrefs），「选择科目」弹窗确认后才落盘。
 */
@Composable
private fun AvgScoreCard(selXnm: String) {
    val context = LocalContext.current
    var state by remember(selXnm) { mutableStateOf<AvgState>(AvgState.Loading) }
    var excluded by remember(selXnm) { mutableStateOf(AvgScorePrefs.getExcluded(context, selXnm)) }
    var avgReloadKey by rememberSaveable(selXnm) { mutableIntStateOf(0) }
    var showPicker by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(selXnm, avgReloadKey) {
        state = AvgState.Loading
        // 并发拉取三个学期，任一失败整卡报错
        val results = coroutineScope {
            (1..3).map { n -> async { loadTermGrades(TermUtils.of(selXnm, n)) } }.awaitAll()
        }
        val firstError = results.firstOrNull { it !is JwxtResult.Ok }
        if (firstError != null) {
            state = AvgState.Error(
                when (firstError) {
                    is JwxtResult.SessionExpired -> firstError.message
                    is JwxtResult.Failed -> firstError.message
                    else -> "加载失败"
                },
            )
        } else {
            val all = results.filterIsInstance<JwxtResult.Ok<List<GradeItem>>>().flatMap { it.data }
            state = AvgState.Ok(AvgScoreCalculator.subjects(AvgScoreCalculator.dedupe(all)))
        }
    }

    Card(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        shape = RoundedCornerShape(16.dp),
    ) {
        when (val s = state) {
            is AvgState.Loading -> Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
                Text(
                    "正在统计本学年成绩…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            is AvgState.Error -> Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    s.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { avgReloadKey++ }) { Text("重试") }
            }
            is AvgState.Ok -> {
                val result = AvgScoreCalculator.compute(s.subjects, excluded)
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 12.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "平均学分绩",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { showPicker = true }) { Text("选择科目") }
                    }
                    if (result.avgScore == null) {
                        Text(
                            "暂无可参与计算的成绩",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Text(
                            "%.2f".format(result.avgScore),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "${result.count} 门科目 · 计入学分 ${trimZero(result.totalCredit)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            result.formulaLine,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (showPicker) {
                    CourseSelectDialog(
                        subjects = s.subjects,
                        initiallyExcluded = excluded,
                        onConfirm = { keys ->
                            showPicker = false
                            AvgScorePrefs.setExcluded(context, selXnm, keys)
                            excluded = keys
                        },
                        onDismiss = { showPicker = false },
                    )
                }
            }
        }
    }
}

/** 去掉末尾多余的 .0（与 AvgScoreCalculator 的展示规则一致） */
private fun trimZero(v: Double): String {
    val s = "%.2f".format(v)
    return if (s.endsWith(".00")) s.dropLast(3) else if (s.endsWith("0")) s.dropLast(1) else s
}
