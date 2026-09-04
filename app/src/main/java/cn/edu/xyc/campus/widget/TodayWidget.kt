package cn.edu.xyc.campus.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.action.actionRunCallback
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
import cn.edu.xyc.campus.data.local.CustomCourseStore
import cn.edu.xyc.campus.data.local.TodayStore
import cn.edu.xyc.campus.data.model.Course
import cn.edu.xyc.campus.data.model.SectionTimes
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/** 小组件查看日期的偏移：按 appWidgetId 存 SharedPreferences（每块独立） */
private const val OFFSET_PREFS = "widget_day_offset"
private val shiftDeltaKey = ActionParameters.Key<Int>("shift_delta")

private suspend fun offsetKey(context: Context, glanceId: GlanceId): String {
    val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(glanceId)
    return "offset_$appWidgetId"
}

private fun readOffset(context: Context, key: String): Int =
    context.getSharedPreferences(OFFSET_PREFS, Context.MODE_PRIVATE).getInt(key, 0)

private fun writeOffset(context: Context, key: String, value: Int?) {
    val prefs = context.getSharedPreferences(OFFSET_PREFS, Context.MODE_PRIVATE)
    if (value == null) prefs.edit().remove(key).apply() else prefs.edit().putInt(key, value).apply()
}

/** 头部 ‹ › 切换查看日期：+1 明天 / -1 昨天 */
class DayShiftAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val delta = parameters[shiftDeltaKey] ?: 0
        val key = offsetKey(context, glanceId)
        writeOffset(context, key, (readOffset(context, key) + delta).coerceIn(-60, 60))
        TodayWidget().update(context, glanceId)
    }
}

/** 回到今天 */
class DayResetAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        writeOffset(context, offsetKey(context, glanceId), null)
        TodayWidget().update(context, glanceId)
    }
}

/**
 * 「今日课程」小组件（SizeMode.Exact）。
 * 预设 2×2 / 2×3 / 3×3 / 3×4 / 4×4 五种规格（见对应 today_widget_info_*.xml），均可拖拽缩放。
 * 头部 ‹ › 前后翻看日期（偏移存在小组件自身状态，各实例独立），点日期文本回到今天。
 * 数据 = TodayStore 落盘教务课表 + CustomCourseStore 自定义课（按单双周过滤合并）。
 */
class TodayWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val offset = readOffset(context, offsetKey(context, id))

        val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, offset) }
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }
        val targetStr = fmt.format(cal.time)
        val target = fmt.parse(targetStr)
        // xqj: 1=周一 … 7=周日；Calendar.DAY_OF_WEEK: 周日=1 … 周六=7
        val xqj = if (cal.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY) 7 else cal.get(Calendar.DAY_OF_WEEK) - 1

        val snap = TodayStore.read(context)
        val week = snap?.weeks?.firstOrNull { w ->
            // rq 兼容 "yyyy-MM-dd"（周一日期）与 "yyyy-MM-dd/yyyy-MM-dd"（整周区间）
            val startStr = w.monday.substringBefore('/').trim()
            val monday = runCatching { fmt.parse(startStr) }.getOrNull()
            monday != null && target != null &&
                !target.before(monday) && target.time - monday.time < 7L * 24 * 3600 * 1000
        }
        // 教务课表 + 自定义课（自定义按目标日期所在周的单双周过滤）
        val customs = if (week != null) CustomCourseStore.readForWeek(context, week.zs) else emptyList()
        val courses = (week?.courses.orEmpty() + customs)
            .filter { it.dayOfWeek == xqj }
            .sortedWith(compareBy({ it.startSection }, { it.name }))

        val dateText = buildString {
            append("${cal.get(Calendar.MONTH) + 1}月${cal.get(Calendar.DAY_OF_MONTH)}日 ")
            append(WEEKDAYS[cal.get(Calendar.DAY_OF_WEEK) - 1])
            when (offset) {
                0 -> append(" · 今天")
                1 -> append(" · 明天")
                -1 -> append(" · 昨天")
                else -> append(" · ${if (offset > 0) "+" else ""}$offset 天")
            }
        }

        provideContent { Content(dateText, snap, week == null, courses, offset) }
    }

    @Composable
    private fun Content(
        dateText: String,
        snap: TodayStore.Snapshot?,
        noCurrentWeek: Boolean,
        courses: List<Course>,
        offset: Int,
    ) {
        val size = LocalSize.current
        // 尺寸分档：紧凑（高<170dp）/ 常规 / 大（宽≥250dp 且高≥240dp 显示教师）
        val compact = size.height < 170.dp
        val large = size.width >= 250.dp && size.height >= 240.dp
        val maxCourses = when {
            size.height < 170.dp -> 3
            size.height < 260.dp -> 5
            size.height < 350.dp -> 7
            else -> 9
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
                    if (!compact) {
                        Text(
                            "今日课程",
                            style = TextStyle(
                                color = ColorProvider(Primary),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                            ),
                            maxLines = 1,
                        )
                        Spacer(GlanceModifier.width(6.dp))
                    }
                    // ‹ › 翻日期；点日期回今天（非今天时后面跟「今」按钮）
                    Text(
                        "‹",
                        style = TextStyle(color = ColorProvider(Primary), fontSize = 13.sp, fontWeight = FontWeight.Bold),
                        modifier = GlanceModifier
                            .clickable(actionRunCallback<DayShiftAction>(actionParametersOf(shiftDeltaKey to -1)))
                            .padding(horizontal = 3.dp, vertical = 1.dp),
                    )
                    Spacer(GlanceModifier.width(2.dp))
                    Text(
                        dateText,
                        style = TextStyle(color = ColorProvider(Secondary), fontSize = 10.sp),
                        maxLines = 1,
                        modifier = GlanceModifier
                            .defaultWeight()
                            .clickable(actionRunCallback<DayResetAction>()),
                    )
                    if (offset != 0) {
                        Text(
                            "今",
                            style = TextStyle(color = ColorProvider(Primary), fontSize = 10.sp, fontWeight = FontWeight.Bold),
                            modifier = GlanceModifier
                                .clickable(actionRunCallback<DayResetAction>())
                                .padding(horizontal = 3.dp, vertical = 1.dp),
                        )
                        Spacer(GlanceModifier.width(2.dp))
                    }
                    Text(
                        "›",
                        style = TextStyle(color = ColorProvider(Primary), fontSize = 13.sp, fontWeight = FontWeight.Bold),
                        modifier = GlanceModifier
                            .clickable(actionRunCallback<DayShiftAction>(actionParametersOf(shiftDeltaKey to 1)))
                            .padding(horizontal = 3.dp, vertical = 1.dp),
                    )
                }
                Spacer(GlanceModifier.height(if (compact) 4.dp else 6.dp))
                when {
                    snap == null || snap.weeks.isEmpty() ->
                        Empty("打开 App 同步课表", compact)
                    noCurrentWeek ->
                        Empty("该日不在已同步的教学周内", compact)
                    courses.isEmpty() ->
                        Empty(if (offset == 0) "今天没有课，好好休息 🎉" else "该日没有课 🎉", compact)
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
            val table = SectionTimes.tableFor(c.room)
            val label = when {
                c.isCustom && c.customTime.isNotEmpty() -> c.customTime
                compact -> "${c.startSection}-${c.endSection}节"
                else -> "${c.startSection}-${c.endSection}节 " +
                    (table.getOrNull(c.startSection - 1)?.start ?: "")
            }
            Text(
                label.trim(),
                style = TextStyle(color = ColorProvider(Primary), fontSize = 10.sp),
                maxLines = 1,
            )
            Spacer(GlanceModifier.width(7.dp))
            Column {
                Text(
                    c.name,
                    style = TextStyle(
                        color = ColorProvider(if (c.isCustom) CustomAmber else TextDark),
                        fontSize = if (compact) 10.sp else 11.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                    maxLines = 1,
                )
                if (large && c.teacher.isNotEmpty()) {
                    Text(
                        "${c.teacher} · ${c.room}",
                        style = TextStyle(color = ColorProvider(Secondary), fontSize = 9.sp),
                        maxLines = 1,
                    )
                } else if ((!compact || c.isCustom) && c.room.isNotEmpty()) {
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
        private val CustomAmber = Color(0xFF8A6D05) // 自定义课程标识色

        // Calendar.DAY_OF_WEEK: 1=周日 … 7=周六
        private val WEEKDAYS = arrayOf("周日", "周一", "周二", "周三", "周四", "周五", "周六")
    }
}

/** 2×2 */
class TodayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayWidget()
}

/** 2×3 */
class TodayWidgetReceiver23 : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayWidget()
}

/** 3×3 */
class TodayWidgetReceiver33 : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayWidget()
}

/** 3×4 */
class TodayWidgetReceiver34 : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayWidget()
}

/** 4×4 */
class TodayWidgetReceiver44 : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayWidget()
}
