package cn.edu.xyc.campus.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import cn.edu.xyc.campus.data.local.CustomCourseStore
import cn.edu.xyc.campus.data.local.ScheduleCache
import cn.edu.xyc.campus.data.model.Course
import cn.edu.xyc.campus.data.remote.JwxtApi
import cn.edu.xyc.campus.data.remote.JwxtResult

/** 学期总表：与周课表同款 7×12 网格展示整学期全部课程（同一时段多门并排，点击看详情） */
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
    var selected by remember { mutableStateOf<Course?>(null) }

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

    // 教务整表 + 自定义课（不按周过滤，同一时段并排）
    val all = remember(courses) { courses + CustomCourseStore.allAsCourses() }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.94f)
                .background(MaterialTheme.colorScheme.background, RoundedCornerShape(20.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("学期课表", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "$termLabel · 共 ${all.size} 门 · 点课程看详情",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, "关闭") }
            }

            when {
                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                error != null -> ErrorPane(error!!, onRetry = { reloadKey++ })
                all.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("该学期暂无课程", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                else -> {
                    // 星期表头（与周课表一致）
                    Row(Modifier.fillMaxWidth()) {
                        Box(
                            Modifier.width(28.dp).height(28.dp),
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
                    // 同款 7×12 网格（整学期：同一时段的多门课并排）
                    Box(Modifier.fillMaxWidth().weight(1f)) {
                        Grid(
                            courses = all,
                            timeMain = true,
                            onCourseClick = { selected = it },
                        )
                    }
                }
            }
        }

        selected?.let { c ->
            CourseDetailDialog(course = c, onDismiss = { selected = null })
        }
    }
}
