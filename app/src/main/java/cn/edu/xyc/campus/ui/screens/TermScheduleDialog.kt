package cn.edu.xyc.campus.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import cn.edu.xyc.campus.data.local.CustomCourseStore
import cn.edu.xyc.campus.data.local.ScheduleCache
import cn.edu.xyc.campus.data.model.Course
import cn.edu.xyc.campus.data.remote.JwxtApi
import cn.edu.xyc.campus.data.remote.JwxtResult

private val TERM_DAY_NAMES = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

/** 学期总表：整学期全部课程（教务整表 + 自定义课），按星期/节次排序 */
@Composable
internal fun TermScheduleDialog(
    xnm: String,
    xqm: String,
    termLabel: String,
    onDismiss: () -> Unit,
) {
    var loading by rememberSaveable { mutableStateOf(true) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var courses by remember { mutableStateOf<List<Course>>(emptyList()) }
    var reloadKey by rememberSaveable { mutableStateOf(0) }

    LaunchedEffect(reloadKey) {
        val key = ScheduleCache.termKey(xnm, xqm)
        ScheduleCache.termSchedule[key]?.let {
            courses = it
            loading = false
            return@LaunchedEffect
        }
        if (!ScheduleCache.tryMark(key)) return@LaunchedEffect
        loading = true
        error = null
        when (val r = JwxtApi.getTermSchedule(xnm, xqm)) {
            is JwxtResult.Ok -> {
                ScheduleCache.termSchedule[key] = r.data
                courses = r.data
            }
            is JwxtResult.SessionExpired -> error = r.message
            is JwxtResult.Failed -> error = r.message
        }
        ScheduleCache.unmark(key)
        loading = false
    }

    // 教务整表 + 自定义课（不按周过滤），按 星期 → 开始节次 排序
    val all = remember(courses) {
        (courses + CustomCourseStore.allAsCourses())
            .sortedWith(compareBy({ it.dayOfWeek }, { it.startSection }, { it.name }))
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .background(MaterialTheme.colorScheme.background, RoundedCornerShape(20.dp))
                .padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("学期课表", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "$termLabel · 共 ${all.size} 门",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, "关闭") }
            }
            Spacer(Modifier.height(4.dp))
            when {
                loading -> Column(
                    Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) { CircularProgressIndicator() }
                error != null -> ErrorPane(error!!, onRetry = { reloadKey++ })
                all.isEmpty() -> Column(
                    Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text("该学期暂无课程", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                else -> LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(all) { c ->
                        TermCourseCard(c)
                    }
                }
            }
        }
    }
}

@Composable
private fun TermCourseCard(c: Course) {
    val weekday = TERM_DAY_NAMES.getOrElse(c.dayOfWeek - 1) { "周${c.dayOfWeek}" }
    val idx = (c.name.hashCode().let { if (it < 0) -it else it }) % COURSE_COLORS.size
    val (bg, fg) = if (c.isCustom) Color(0xFFFFF1C9) to Color(0xFF8A6D05)
    else COURSE_COLORS[idx]
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = if (c.isCustom) bg else MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 左侧星期+节次色块
            Column(
                Modifier
                    .background(bg, RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(weekday, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = fg)
                Text(
                    "${c.startSection}-${c.endSection}节",
                    fontSize = 10.sp,
                    color = fg.copy(alpha = 0.85f),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    c.name + if (c.isCustom) "（自定义）" else "",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    listOf(c.teacher, c.room).filter { it.isNotEmpty() }.joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                c.weekText.ifEmpty { "—" },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
