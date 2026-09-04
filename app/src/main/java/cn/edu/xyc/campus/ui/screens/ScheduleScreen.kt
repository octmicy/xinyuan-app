package cn.edu.xyc.campus.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.NavigateBefore
import androidx.compose.material.icons.automirrored.rounded.NavigateNext
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.edu.xyc.campus.data.local.ScheduleCache
import cn.edu.xyc.campus.data.local.TodayStore
import cn.edu.xyc.campus.data.model.Course
import cn.edu.xyc.campus.data.remote.JwxtApi
import cn.edu.xyc.campus.data.remote.JwxtResult
import cn.edu.xyc.campus.data.remote.TermUtils
import cn.edu.xyc.campus.widget.TodayWidget
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.launch

private val COURSE_COLORS = listOf(
    Color(0xFFD6E4FF) to Color(0xFF1D3F8C),
    Color(0xFFFFE0DB) to Color(0xFF8C2B1D),
    Color(0xFFE0F2E0) to Color(0xFF1D6B2B),
    Color(0xFFFFF3CD) to Color(0xFF7A5C00),
    Color(0xFFF3E0FF) to Color(0xFF5B1D8C),
    Color(0xFFDFF6F6) to Color(0xFF0F5E5E),
    Color(0xFFFFE9F4) to Color(0xFF8C1D5B),
    Color(0xFFE8EAED) to Color(0xFF37414F),
)

@Composable
fun ScheduleScreen() {
    val curTerm = remember { TermUtils.current() }
    var selXnm by rememberSaveable { mutableStateOf(curTerm.xnm) }
    var selTermNo by rememberSaveable { mutableStateOf(curTerm.termNo) }
    var initialLoading by rememberSaveable { mutableStateOf(true) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var weeks by remember { mutableStateOf<List<cn.edu.xyc.campus.data.model.WeekInfo>>(emptyList()) }
    var reloadKey by rememberSaveable { mutableStateOf(0) }
    var showTermPicker by rememberSaveable { mutableStateOf(false) }
    var selectedCourse by remember { mutableStateOf<Course?>(null) }
    val scope = rememberCoroutineScope()
    val term = TermUtils.of(selXnm, selTermNo)

    // Pager：一周一页，跟手拖拽 + 自动吸附
    val pagerState = rememberPagerState(initialPage = 0) { weeks.size.coerceAtLeast(1) }
    val displayWeek = pagerState.currentPage + 1

    val context = LocalContext.current

    // 当前学期的周数据落盘给小组件并触发其刷新（非当前学期不落盘，避免带偏小组件）
    fun syncTodayStore(zs: Int, courses: List<Course>) {
        if (selXnm != curTerm.xnm || selTermNo != curTerm.termNo) return
        val monday = weeks.firstOrNull { it.zs == zs }?.rq.orEmpty()
        TodayStore.upsertWeek(
            context,
            termKey = "$selXnm-${term.xqm}",
            termLabel = "${TermUtils.xnmToLabel(selXnm)} 第${selTermNo}学期",
            zs = zs,
            monday = monday,
            courses = courses,
        )
        scope.launch {
            runCatching { TodayWidget().updateAll(context) }
        }
    }

    // 确保某周数据就绪（未缓存才请求）
    suspend fun ensureWeek(zs: Int) {
        val key = ScheduleCache.weekKey(selXnm, term.xqm, zs)
        if (ScheduleCache.weekData.containsKey(key)) return
        if (!ScheduleCache.tryMark(key)) return
        try {
            when (val r = JwxtApi.getScheduleByWeek(term, zs)) {
                is JwxtResult.Ok -> {
                    ScheduleCache.weekData[key] = r.data
                    syncTodayStore(zs, r.data.first)
                }
                is JwxtResult.SessionExpired -> error = r.message
                is JwxtResult.Failed -> error = r.message
            }
        } finally {
            ScheduleCache.unmark(key)
        }
    }

    // 学期级初始化：周次列表 + 定位当前周 + 预加载相邻
    LaunchedEffect(selXnm, selTermNo, reloadKey) {
        initialLoading = true
        error = null
        val wkKey = ScheduleCache.weeksKey(selXnm, term.xqm)
        val list = ScheduleCache.weeksList[wkKey] ?: when (val w = JwxtApi.getWeeks(selXnm, term.xqm)) {
            is JwxtResult.Ok -> {
                ScheduleCache.weeksList[wkKey] = w.data
                w.data
            }
            is JwxtResult.SessionExpired -> {
                error = w.message
                null
            }
            is JwxtResult.Failed -> {
                error = w.message
                null
            }
        }
        if (list != null) {
            weeks = list
            val target = if (selXnm == curTerm.xnm && selTermNo == curTerm.termNo) {
                guessCurrentWeekFromList(list)
            } else 1
            ensureWeek(target)
            // 预加载相邻周：翻页大概率命中缓存，无需转圈
            if (target + 1 <= list.size) ensureWeek(target + 1)
            if (target - 1 >= 1) ensureWeek(target - 1)
            // 先解除 loading（让 Pager 组合），再跳到当前周
            // 注意：scrollToPage 会挂起等待 Pager 布局，若 Pager 未组合则永远挂起 → 必须先置 false
            initialLoading = false
            pagerState.scrollToPage((target - 1).coerceIn(0, weeks.size - 1))
        }
        initialLoading = false
    }

    // 翻页稳定后：加载该周 + 预加载相邻周（拖动过程中相邻页大概率已就绪）
    LaunchedEffect(pagerState.settledPage, selXnm, selTermNo) {
        if (initialLoading) return@LaunchedEffect
        val zs = pagerState.settledPage + 1
        ensureWeek(zs)
        if (zs + 1 <= weeks.size) ensureWeek(zs + 1)
        if (zs - 1 >= 1) ensureWeek(zs - 1)
    }

    Column(Modifier.fillMaxSize()) {
        // 学期选择 + 刷新
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            TextButton(onClick = { showTermPicker = true }) {
                Text(
                    "${TermUtils.xnmToLabel(selXnm)} 第${selTermNo}学期 ▾",
                    style = MaterialTheme.typography.titleSmall,
                )
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { reloadKey++ }) { Icon(Icons.Rounded.Refresh, "刷新") }
        }

        // 周次行（实时跟随拖动）
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
        ) {
            IconButton(
                onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) } },
                enabled = pagerState.currentPage > 0,
            ) {
                Icon(Icons.AutoMirrored.Rounded.NavigateBefore, "上一周")
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("第 $displayWeek 周", style = MaterialTheme.typography.titleMedium)
                val rq = weeks.firstOrNull { it.zs == displayWeek }?.rq.orEmpty()
                Text(
                    if (rq.isNotEmpty()) rq else TermUtils.xnmToLabel(selXnm),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(
                onClick = {
                    scope.launch {
                        if (pagerState.currentPage + 1 < weeks.size) {
                            pagerState.animateScrollToPage(pagerState.currentPage + 1)
                        }
                    }
                },
                enabled = weeks.isEmpty() || pagerState.currentPage < weeks.size - 1,
            ) {
                Icon(Icons.AutoMirrored.Rounded.NavigateNext, "下一周")
            }
        }

        // 星期表头
        Row(Modifier.fillMaxWidth()) {
            Box(
                Modifier.width(24.dp).height(28.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "节",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            listOf("一", "二", "三", "四", "五", "六", "日").forEach {
                Box(Modifier.weight(1f).height(28.dp), contentAlignment = Alignment.Center) {
                    Text(it, style = MaterialTheme.typography.labelMedium)
                }
            }
        }

        when {
            initialLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            error != null -> ErrorPane(error!!, onRetry = { reloadKey++ })
            else -> HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                beyondViewportPageCount = 1, // 相邻页保持组合，拖动时立即可见
            ) { page ->
                val zs = page + 1
                val key = ScheduleCache.weekKey(selXnm, term.xqm, zs)
                val data = ScheduleCache.weekData[key]
                android.util.Log.d(
                    "XycApp",
                    "Page $page key=$key has=${ScheduleCache.weekData.containsKey(key)} cacheSize=${ScheduleCache.weekData.size}",
                )
                when {
                    data == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.padding(bottom = 60.dp))
                    }
                    data.first.isEmpty() -> Box(
                        Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "第 $zs 周无课 🎉",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    else -> Grid(courses = data.first, onCourseClick = { selectedCourse = it })
                }
            }
        }
    }

    if (showTermPicker) {
        TermPickerDialog(
            currentXnm = selXnm,
            currentTermNo = selTermNo,
            onDismiss = { showTermPicker = false },
            onConfirm = { xnm, no ->
                selXnm = xnm
                selTermNo = no
                showTermPicker = false
            },
        )
    }

    selectedCourse?.let { c ->
        CourseDetailDialog(course = c, onDismiss = { selectedCourse = null })
    }
}

/** 课程详情弹窗：点击课表卡片放大查看（教师/教室/周次/学分等） */
@Composable
private fun CourseDetailDialog(course: Course, onDismiss: () -> Unit) {
    val idx = (course.name.hashCode().let { if (it < 0) -it else it }) % COURSE_COLORS.size
    val (bg, fg) = COURSE_COLORS[idx]
    val weekday = DAY_NAMES.getOrElse(course.dayOfWeek - 1) { "周${course.dayOfWeek}" }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(10.dp)
                        .background(bg, RoundedCornerShape(3.dp)),
                )
                Spacer(Modifier.width(8.dp))
                Text(course.name, style = MaterialTheme.typography.titleMedium)
            }
        },
        text = {
            Column {
                DetailRow("教师", course.teacher)
                DetailRow("教室", listOf(course.building, course.room).filter { it.isNotEmpty() }.joinToString(" · "))
                DetailRow("时间", "$weekday 第${course.startSection}-${course.endSection}节")
                if (course.weekText.isNotEmpty()) DetailRow("周次", course.weekText)
                if (course.credit.isNotEmpty()) DetailRow("学分", course.credit)
                if (course.nature.isNotEmpty()) DetailRow("性质", course.nature)
                if (course.classGroup.isNotEmpty()) DetailRow("教学班", course.classGroup)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(52.dp),
        )
        Text(
            value.ifEmpty { "—" },
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

private val DAY_NAMES = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

/** 一屏式网格：7 列均分屏宽、12 节均分剩余高度，随分辨率自适应 */
@Composable
private fun Grid(courses: List<Course>, onCourseClick: (Course) -> Unit) {
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
        val cellH = maxHeight / 12
        Row(Modifier.fillMaxSize()) {
            Column(Modifier.width(24.dp)) {
                (1..12).forEach { i ->
                    Box(
                        Modifier.height(cellH).fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "$i",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            (1..7).forEach { day ->
                DayColumn(
                    dayCourses = courses.filter { it.dayOfWeek == day },
                    cellH = cellH,
                    onCourseClick = onCourseClick,
                )
            }
        }
    }
}

@Composable
private fun RowScope.DayColumn(
    dayCourses: List<Course>,
    cellH: androidx.compose.ui.unit.Dp,
    onCourseClick: (Course) -> Unit,
) {
    Box(
        Modifier
            .weight(1f)
            .fillMaxHeight(),
    ) {
        dayCourses
            .groupBy { it.startSection to it.endSection }
            .forEach { (range, list) ->
                val top = cellH * (range.first - 1)
                val h = cellH * (range.second - range.first + 1)
                Row(
                    Modifier
                        .offset(y = top)
                        .height(h)
                        .fillMaxWidth(),
                ) {
                    list.forEach { c ->
                        CourseCell(
                            c = c,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxSize()
                                .padding(1.dp),
                            onClick = { onCourseClick(c) },
                        )
                    }
                }
            }
    }
}

@Composable
private fun CourseCell(c: Course, modifier: Modifier, onClick: () -> Unit) {
    val idx = (c.name.hashCode().let { if (it < 0) -it else it }) % COURSE_COLORS.size
    val (bg, fg) = COURSE_COLORS[idx]
    Box(
        modifier
            .background(bg, RoundedCornerShape(4.dp))
            .clickable { onClick() }
            .padding(horizontal = 2.dp, vertical = 1.dp),
    ) {
        Column {
            Text(
                c.name,
                fontSize = 8.sp,
                lineHeight = 10.sp,
                color = fg,
                fontWeight = FontWeight.Bold,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "@${c.room}",
                fontSize = 7.sp,
                lineHeight = 9.sp,
                color = fg.copy(alpha = 0.85f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** 用周次列表的 rq（每周一日期）精确定位当前周 */
private fun guessCurrentWeekFromList(weeks: List<cn.edu.xyc.campus.data.model.WeekInfo>): Int {
    if (weeks.isEmpty()) return 1
    val today = java.text.SimpleDateFormat(
        "yyyy-MM-dd",
        java.util.Locale.US,
    ).format(java.util.Calendar.getInstance().time)
    val sorted = weeks.sortedBy { it.zs }
    var best = sorted.first().zs
    for (w in sorted) {
        if (w.rq <= today) best = w.zs else break
    }
    return best.coerceIn(1, sorted.last().zs)
}

@Composable
private fun TermPickerDialog(
    currentXnm: String,
    currentTermNo: Int,
    onDismiss: () -> Unit,
    onConfirm: (String, Int) -> Unit,
) {
    val curYear = currentXnm.toIntOrNull() ?: 2026
    val years = remember { (0..3).map { (curYear - it).toString() } }
    var pickXnm by rememberSaveable { mutableStateOf(currentXnm) }
    var pickNo by rememberSaveable { mutableStateOf(currentTermNo) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择学期") },
        text = {
            Column {
                Text("学年", style = MaterialTheme.typography.labelMedium)
                LazyRow(
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp),
                ) {
                    items(years.size) { i ->
                        val y = years[i]
                        FilterChip(
                            selected = pickXnm == y,
                            onClick = { pickXnm = y },
                            label = { Text(TermUtils.xnmToLabel(y)) },
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text("学期", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)) {
                    (1..3).forEach { n ->
                        FilterChip(
                            selected = pickNo == n,
                            onClick = { pickNo = n },
                            label = { Text("第${n}学期") },
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(pickXnm, pickNo) }) { Text("确定") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
internal fun ErrorPane(message: String, onRetry: (() -> Unit)? = null) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            if (onRetry != null) {
                Spacer(Modifier.height(12.dp))
                IconButton(onClick = onRetry) { Icon(Icons.Rounded.Refresh, "重试") }
            }
        }
    }
}
