package cn.edu.xyc.campus.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import cn.edu.xyc.campus.MainActivity
import cn.edu.xyc.campus.data.local.TodayStore
import cn.edu.xyc.campus.data.model.Course
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * 桌面「今日课程」小组件（SizeMode.Exact：可自由拖拽缩放，按实际尺寸自适应排版）。
 * 数据来自 TodayStore（课表页加载时落盘），App 进程不在也能渲染；
 * 预设 小(2x2)/标准(4x2)/大(4x3) 三种初始规格，均可在桌面拖拽调整。
 */
class TodayWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snap = TodayStore.read(context)
        val cal = Calendar.getInstance()
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }
        val todayStr = fmt.format(cal.time)
        val today = fmt.parse(todayStr)
        // xqj: 1=周一 … 7=周日；Calendar.DAY_OF_WEEK: 周日=1 … 周六=7
        val xqj = if (cal.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY) 7 else cal.get(Calendar.DAY_OF_WEEK) - 1

        val week = snap?.weeks?.firstOrNull { w ->
            // rq 兼容两种格式："yyyy-MM-dd"（周一日期）与 "yyyy-MM-dd/yyyy-MM-dd"（整周区间）
            val startStr = w.monday.substringBefore('/').trim()
            val monday = runCatching { fmt.parse(startStr) }.getOrNull()
            monday != null && today != null &&
                !today.before(monday) && today.time - monday.time < 7L * 24 * 3600 * 1000
        }
        val courses = week?.courses
            ?.filter { it.dayOfWeek == xqj }
            ?.sortedWith(compareBy({ it.startSection }, { it.name }))
            .orEmpty()
        val dateText = "${cal.get(Calendar.MONTH) + 1}月${cal.get(Calendar.DAY_OF_MONTH)}日 " +
            WEEKDAYS[cal.get(Calendar.DAY_OF_WEEK) - 1]

        provideContent {
            // Exact 模式下从 composition local 取当前实际尺寸
            Content(dateText, snap, week == null, courses, LocalSize.current)
        }
    }

    @Composable
    private fun Content(
        dateText: String,
        snap: TodayStore.Snapshot?,
        noCurrentWeek: Boolean,
        courses: List<Course>,
        size: DpSize,
    ) {
        // 尺寸分档：小（高度<160dp）/ 标准 / 大（宽≥250dp 且高≥230dp）
        val compact = size.height < 160.dp
        val large = size.width >= 250.dp && size.height >= 230.dp
        val maxCourses = when {
            compact -> 3
            large -> 8
            else -> 6
        }
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(ColorProvider(Bg))
                .cornerRadius(16.dp)
                .clickable(
                    actionStartActivity(
                        Intent(LocalContext.current, MainActivity::class.java),
                    ),
                )
                .padding(if (compact) 6.dp else 10.dp),
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "今日课程",
                        style = TextStyle(
                            color = ColorProvider(Primary),
                            fontSize = if (compact) 11.sp else 13.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                    Spacer(GlanceModifier.defaultWeight())
                    Text(
                        dateText,
                        style = TextStyle(color = ColorProvider(Secondary), fontSize = 10.sp),
                    )
                }
                Spacer(GlanceModifier.height(if (compact) 4.dp else 6.dp))
                when {
                    snap == null || snap.weeks.isEmpty() ->
                        Empty("打开 App 同步课表", compact)
                    noCurrentWeek ->
                        Empty("暂无本周数据，打开 App 刷新", compact)
                    courses.isEmpty() ->
                        Empty("今天没有课，好好休息 🎉", compact)
                    else -> {
                        courses.take(maxCourses).forEach { c ->
                            CourseRow(c, compact, large)
                        }
                        if (courses.size > maxCourses) {
                            Spacer(GlanceModifier.height(4.dp))
                            Text(
                                "还有 ${courses.size - maxCourses} 门课…",
                                style = TextStyle(
                                    color = ColorProvider(Secondary),
                                    fontSize = 9.sp,
                                ),
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun Empty(text: String, compact: Boolean) {
        Text(
            text,
            style = TextStyle(
                color = ColorProvider(Secondary),
                fontSize = if (compact) 10.sp else 11.sp,
            ),
            maxLines = 2,
        )
    }

    @Composable
    private fun CourseRow(c: Course, compact: Boolean, large: Boolean) {
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .padding(bottom = if (compact) 3.dp else 4.dp)
                .background(ColorProvider(Card))
                .cornerRadius(8.dp)
                .padding(
                    horizontal = if (compact) 6.dp else 8.dp,
                    vertical = if (compact) 4.dp else 5.dp,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "${c.startSection}-${c.endSection}节",
                style = TextStyle(color = ColorProvider(Primary), fontSize = 10.sp),
                maxLines = 1,
            )
            Spacer(GlanceModifier.width(7.dp))
            Column {
                Text(
                    c.name,
                    style = TextStyle(
                        color = ColorProvider(TextDark),
                        fontSize = if (compact) 10.sp else 11.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                    maxLines = 1,
                )
                // 大尺寸显示教师；紧凑模式只留教室；标准模式教室
                if (large && c.teacher.isNotEmpty()) {
                    Text(
                        "${c.teacher} · ${c.room}",
                        style = TextStyle(color = ColorProvider(Secondary), fontSize = 9.sp),
                        maxLines = 1,
                    )
                } else if (!compact && c.room.isNotEmpty()) {
                    Text(
                        "@${c.room}",
                        style = TextStyle(color = ColorProvider(Secondary), fontSize = 9.sp),
                        maxLines = 1,
                    )
                }
            }
        }
    }

    companion object {
        // 新院助手校色：浅蓝底 + 深蓝字
        private val Bg = Color(0xFFE8F1FF)
        private val Card = Color(0xFFFFFFFF)
        private val Primary = Color(0xFF1D3F8C)
        private val Secondary = Color(0xFF6B7B99)
        private val TextDark = Color(0xFF22304D)

        // Calendar.DAY_OF_WEEK: 1=周日 … 7=周六
        private val WEEKDAYS = arrayOf("周日", "周一", "周二", "周三", "周四", "周五", "周六")
    }
}

/** 标准 4x2 */
class TodayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayWidget()
}

/** 小 2x2 */
class TodayWidgetReceiverSmall : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayWidget()
}

/** 大 4x3 */
class TodayWidgetReceiverLarge : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayWidget()
}
