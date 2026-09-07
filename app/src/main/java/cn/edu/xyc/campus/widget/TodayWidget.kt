package cn.edu.xyc.campus.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
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
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.color.ColorProvider as DayNightProvider
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
import cn.edu.xyc.campus.data.local.ThemeStore
import cn.edu.xyc.campus.data.local.TodayStore
import cn.edu.xyc.campus.data.model.Course
import cn.edu.xyc.campus.data.model.SectionTimes
import cn.edu.xyc.campus.ui.theme.ThemeModeStore
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

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
        ThemeStore.init(context) // 主题包配色
        ThemeModeStore.init(context) // 深浅色偏好（闹钟/系统触发渲染时进程里没跑过 MainActivity，必须在此初始化）
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

        // 今天视图：已下课自动消失（结束后自动补位），并排一个"下一节课下课时刻"的精确闹钟
        // 即时刷新小组件，避免等打开 App 才更新
        val now = System.currentTimeMillis()
        val shown = if (offset == 0 && courses.isNotEmpty()) {
            val remaining = courses.filter { val e = courseEndMillis(it, cal); e < 0 || e > now }
            scheduleNextRefresh(context, remaining)
            remaining
        } else {
            if (offset == 0) scheduleNextRefresh(context, courses)
            courses
        }

        val dateShort = "${cal.get(Calendar.MONTH) + 1}月${cal.get(Calendar.DAY_OF_MONTH)}日 " +
            WEEKDAYS[cal.get(Calendar.DAY_OF_WEEK) - 1]
        val todayAllDone = offset == 0 && courses.isNotEmpty() && shown.isEmpty()
        val relTag = when (offset) {
            0 -> ""
            1 -> "明天"
            -1 -> "昨天"
            else -> (if (offset > 0) "+" else "") + offset + "天"
        }

        provideContent { Content(dateShort, relTag, snap, week == null, shown, offset, todayAllDone) }
    }

    @Composable
    private fun Content(
        dateShort: String,
        relTag: String,
        snap: TodayStore.Snapshot?,
        noCurrentWeek: Boolean,
        courses: List<Course>,
        offset: Int,
        todayAllDone: Boolean = false,
    ) {
        val wc = wColors(LocalContext.current)
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
                .background(wc.bg)
                .cornerRadius(16.dp)
                .clickable(
                    actionStartActivity(
                        Intent(LocalContext.current, MainActivity::class.java),
                    ),
                )
                .padding(if (compact) 6.dp else 10.dp),
        ) {
            Column {
                // 第一行：标题（翻看非今天时显示"课程预览"避免误解）+ 相对日期标签
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (offset == 0) "今日课程" else "课程预览",
                        style = TextStyle(
                            color = wc.primary,
                            fontSize = if (compact) 11.sp else 13.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                        maxLines = 1,
                    )
                    Spacer(GlanceModifier.defaultWeight())
                    if (relTag.isNotEmpty()) {
                        Text(
                            relTag,
                            style = TextStyle(color = wc.secondary, fontSize = 10.sp),
                            maxLines = 1,
                        )
                    }
                }
                Spacer(GlanceModifier.height(2.dp))
                // 第二行：日期控制条（‹ 日期 › 大热区，点日期/今回今天）
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val arrowSize = if (compact) 15.sp else 17.sp
                    val arrowPad = GlanceModifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    Text(
                        "‹",
                        style = TextStyle(
                            color = wc.primary,
                            fontSize = arrowSize,
                            fontWeight = FontWeight.Bold,
                        ),
                        modifier = GlanceModifier
                            .clickable(actionRunCallback<DayShiftAction>(actionParametersOf(shiftDeltaKey to -1)))
                            .then(arrowPad),
                    )
                    Text(
                        dateShort,
                        style = TextStyle(
                            color = wc.primary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = androidx.glance.text.TextAlign.Center,
                        ),
                        maxLines = 1,
                        modifier = GlanceModifier
                            .defaultWeight()
                            .clickable(actionRunCallback<DayResetAction>())
                            .padding(vertical = 4.dp),
                    )
                    if (offset != 0) {
                        Text(
                            "今",
                            style = TextStyle(
                                color = wc.primary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                            ),
                            modifier = GlanceModifier
                                .background(CHIP_BG)
                                .cornerRadius(6.dp)
                                .clickable(actionRunCallback<DayResetAction>())
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                        Spacer(GlanceModifier.width(2.dp))
                    }
                    Text(
                        "›",
                        style = TextStyle(
                            color = wc.primary,
                            fontSize = arrowSize,
                            fontWeight = FontWeight.Bold,
                        ),
                        modifier = GlanceModifier
                            .clickable(actionRunCallback<DayShiftAction>(actionParametersOf(shiftDeltaKey to 1)))
                            .then(arrowPad),
                    )
                }
                Spacer(GlanceModifier.height(if (compact) 3.dp else 5.dp))
                when {
                    snap == null || snap.weeks.isEmpty() ->
                        Empty("打开 App 同步课表", compact, wc)
                    noCurrentWeek ->
                        Empty("该日不在已同步的教学周内", compact, wc)
                    courses.isEmpty() ->
                        Empty(
                            when {
                                todayAllDone -> "今天的课都结束啦 🎉"
                                offset == 0 -> "今天没有课，好好休息 🎉"
                                else -> "该日没有课 🎉"
                            },
                            compact,
                            wc,
                        )
                    else -> {
                        courses.take(maxCourses).forEach { c ->
                            CourseRow(c, compact, large, wc)
                        }
                        if (courses.size > maxCourses) {
                            Spacer(GlanceModifier.height(4.dp))
                            Text(
                                "还有 ${courses.size - maxCourses} 门课…",
                                style = TextStyle(
                                    color = wc.secondary,
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
    private fun Empty(text: String, compact: Boolean, wc: WColors) {
        Text(
            text,
            style = TextStyle(
                color = wc.secondary,
                fontSize = if (compact) 10.sp else 11.sp,
            ),
            maxLines = 2,
        )
    }

    @Composable
    private fun CourseRow(c: Course, compact: Boolean, large: Boolean, wc: WColors) {
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .padding(bottom = if (compact) 3.dp else 4.dp)
                .background(wc.card)
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
                style = TextStyle(color = wc.primary, fontSize = 10.sp),
                maxLines = 1,
            )
            Spacer(GlanceModifier.width(7.dp))
            Column {
                Text(
                    c.name,
                    style = TextStyle(
                        color = if (c.isCustom) wc.custom else wc.textDark,
                        fontSize = if (compact) 10.sp else 11.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                    maxLines = 1,
                )
                if (large && c.teacher.isNotEmpty()) {
                    Text(
                        "${c.teacher} · ${c.room}",
                        style = TextStyle(color = wc.secondary, fontSize = 9.sp),
                        maxLines = 1,
                    )
                } else if ((!compact || c.isCustom) && c.room.isNotEmpty()) {
                    Text(
                        "@${c.room}",
                        style = TextStyle(color = wc.secondary, fontSize = 9.sp),
                        maxLines = 1,
                    )
                }
            }
        }
    }

    companion object {
        // Calendar.DAY_OF_WEEK: 1=周日 … 7=周六
        private val WEEKDAYS = arrayOf("周日", "周一", "周二", "周三", "周四", "周五", "周六")

        /**
         * 小组件配色：主题包定义了 widget* 色则日夜都用主题色；
         * 否则用 Glance 日夜双色（ColorProvider(day, night)），深色系统自动切深色套。
         * keys: widgetBg/widgetCard/widgetText/widgetPrimary/widgetSecondary/widgetCustom
         */
        private fun wColors(context: Context): WColors {
            fun c(key: String, day: String, night: String): ColorProvider {
                val themed = ThemeStore.color(key, "")
                if (themed.isNotEmpty()) {
                    val p = Color(android.graphics.Color.parseColor(themed))
                    return ColorProvider(p)
                }
                val d = Color(android.graphics.Color.parseColor(day))
                val n = Color(android.graphics.Color.parseColor(night))
                return when (ThemeModeStore.mode.value) {
                    ThemeModeStore.Mode.LIGHT -> ColorProvider(d)
                    ThemeModeStore.Mode.DARK -> ColorProvider(n)
                    ThemeModeStore.Mode.FOLLOW -> DayNightProvider(d, n)
                }
            }
            return WColors(
                bg = c("widgetBg", "#E8F1FF", "#171C25"),
                card = c("widgetCard", "#FFFFFF", "#232B38"),
                primary = c("widgetPrimary", "#1D3F8C", "#ADC6FF"),
                secondary = c("widgetSecondary", "#6B7B99", "#9FAEC7"),
                textDark = c("widgetText", "#22304D", "#E1E7F1"),
                custom = c("widgetCustom", "#8A6D05", "#EFD983"),
            )
        }
    }
}

private data class WColors(
    val bg: ColorProvider,
    val card: ColorProvider,
    val primary: ColorProvider,
    val secondary: ColorProvider,
    val textDark: ColorProvider,
    val custom: ColorProvider,
)

/** "回到今天"徽章底色（日夜双色） */
private val CHIP_BG = DayNightProvider(Color(0xFFD6E3FF), Color(0xFF2A4A80))

/** 下一节下课时刻的精确刷新闹钟（已下课自动消失的即时性来源） */
private const val REFRESH_ACTION = "cn.edu.xyc.campus.widget.REFRESH"
private const val REFRESH_RC = 3001

/** 课程今天的结束时刻（epoch ms）；无法确定返回 -1。自定义时间课用其时间，其余按作息表 */
private fun courseEndMillis(c: Course, day: Calendar): Long {
    val endText = when {
        c.isCustom && c.customTime.isNotEmpty() -> c.customTime.substringAfter('-', "")
        else -> SectionTimes.tableFor(c.room).getOrNull(c.endSection - 1)?.end
    } ?: return -1L
    val parts = endText.split(":")
    val h = parts.getOrNull(0)?.toIntOrNull() ?: return -1L
    val m = parts.getOrNull(1)?.toIntOrNull() ?: return -1L
    return (day.clone() as Calendar).apply {
        set(Calendar.HOUR_OF_DAY, h)
        set(Calendar.MINUTE, m)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}

/**
 * 排下一次小组件刷新：
 * - 今天还有课没结束 → 在最近的下课时刻刷新（该课自动消失，剩余课程补位）
 * - 今天没课了 → 次日 00:05 刷新（日期/课程翻新）
 * 用 setAndAllowWhileIdle 省电且免精确闹钟权限；同requestCode覆盖，无需取消旧闹钟
 */
private fun scheduleNextRefresh(context: Context, remaining: List<Course>) {
    val now = System.currentTimeMillis()
    val nextEnd = remaining.mapNotNull { courseEndMillis(it, Calendar.getInstance()).takeIf { e -> e > now } }
        .minOrNull()
    val trigger = (nextEnd ?: run {
        (Calendar.getInstance().clone() as Calendar).apply {
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 5)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }).coerceAtLeast(now + 30_000L)
    val pi = PendingIntent.getBroadcast(
        context,
        REFRESH_RC,
        Intent(context, WidgetRefreshReceiver::class.java).setAction(REFRESH_ACTION),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    runCatching {
        context.getSystemService(AlarmManager::class.java)
            .setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, trigger, pi)
    }
}

/** 下课时刻/跨天的定时刷新接收器 */
class WidgetRefreshReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != REFRESH_ACTION) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            runCatching { TodayWidget().updateAll(context) }
            pending.finish()
        }
    }
}

/** 开机刷新小组件：跨天/重启后重渲染（MIUI 自启动白名单会影响此广播，尽力而为） */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED && action != "android.intent.action.QUICKBOOT_POWERON") return
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            runCatching { TodayWidget().updateAll(context) }
            pending.finish()
        }
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
