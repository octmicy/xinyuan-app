package cn.edu.xyc.campus.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.edu.xyc.campus.data.local.BorrowStore
import cn.edu.xyc.campus.data.local.HomePrefs
import cn.edu.xyc.campus.data.local.Quote
import cn.edu.xyc.campus.data.local.QuoteStore
import cn.edu.xyc.campus.data.local.ScheduleCache
import cn.edu.xyc.campus.data.local.TodayStore
import cn.edu.xyc.campus.data.model.Course
import cn.edu.xyc.campus.data.model.SectionTimes
import cn.edu.xyc.campus.data.model.ThirdApp
import cn.edu.xyc.campus.data.model.WeekInfo
import cn.edu.xyc.campus.data.remote.CredentialLauncher
import cn.edu.xyc.campus.data.remote.FoodRecommender
import cn.edu.xyc.campus.data.remote.HitokotoApi
import cn.edu.xyc.campus.data.remote.PortalApi
import cn.edu.xyc.campus.data.remote.TermUtils
import cn.edu.xyc.campus.data.remote.WeatherApi
import cn.edu.xyc.campus.data.remote.ticketUrl
import cn.edu.xyc.campus.ui.theme.isAppDarkTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

// ==================== 常量与小工具 ====================

/** 头部渐变：浅色 / 深色两套（isAppDarkTheme() 选择） */
private val HEADER_GRADIENT_LIGHT = listOf(Color(0xFF3D6FD6), Color(0xFF6F9BE8))
private val HEADER_GRADIENT_DARK = listOf(Color(0xFF24418C), Color(0xFF3D63B8))

/** 一天的毫秒数（周次/倒计时推算用） */
private const val DAY_MS = 24L * 3600 * 1000

/** "yyyy-MM-dd" 解析（rq 应还日期等，非严格线程场景，仅主线程使用） */
private val DATE_FMT = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }

/** 星期文案（下标 = xqj-1，xqj 1=周一 … 7=周日） */
private val WEEKDAY_LABELS = arrayOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

/**
 * 首页快捷入口候选（6 个）。
 * key 对应 HomePrefs 显隐持久化；打开走门户应用匹配 → ticket 免密 URL → 应用内 WebView。
 * 请假不走门户匹配（直接复用学工登录链），portalName 留空。
 */
private data class HomeEntry(
    val key: String,          // HomePrefs 显隐 key
    val label: String,        // 展示名
    val emoji: String,        // 入口图标（门户应用无图标资源，用 emoji 表意）
    val portalName: String,   // 门户 /app/getApplication 返回的 name 匹配
    val finalHash: String? = null, // 应用内 WebView 的 SPA 落地路由（仅 libsp 域生效）
)

private val HOME_ENTRIES = listOf(
    HomeEntry(HomePrefs.ENTRY_LIBRARY, "电子证", "🎫", CredentialLauncher.LIBRARY_PORTAL_NAME, finalHash = CredentialLauncher.LIBRARY_ROUTE),
    HomeEntry(HomePrefs.ENTRY_LEAVE, "请假申请", "📝", portalName = ""),
    HomeEntry(HomePrefs.ENTRY_JWXT, "教务系统", "🎓", "教务系统"),
    HomeEntry(HomePrefs.ENTRY_XG, "学工系统", "🏫", "学工系统"),
    HomeEntry(HomePrefs.ENTRY_ONLINE, "网络教学", "💻", "网络教学系统"),
)

/** 应用内 WebView 打开目标（首页私有；AppsScreen 有同名 OpenTarget，避免重名） */
private data class HomeOpenTarget(val name: String, val url: String, val finalHash: String?)

/** 借阅卡行：title 书名 / date 应还日期 / daysLeft 距应还天数（负=已超期，null=日期无法解析） */
private data class BorrowRow(val title: String, val date: String?, val daysLeft: Long?) {
    /** 排序组：0 已超期 → 1 三天内到期 → 2 其余（无日期归此组） */
    val sortGroup: Int
        get() = when {
            daysLeft != null && daysLeft < 0 -> 0
            daysLeft != null && daysLeft <= 3 -> 1
            else -> 2
        }
}

/** 解析 "yyyy-MM-dd"（可带 "/yyyy-MM-dd" 整周区间后缀）为当天 0 点 Calendar；失败返回 null */
private fun parseDay(text: String): Calendar? {
    val date = runCatching { DATE_FMT.parse(text.substringBefore('/').trim()) }.getOrNull() ?: return null
    return Calendar.getInstance().apply { time = date }
}

/** 归一化到当日 0 点（克隆，不改原实例） */
private fun Calendar.midnight(): Calendar = (clone() as Calendar).apply {
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}

/** 今天是星期几（1=周一 … 7=周日；Calendar 周日=1 需换算） */
private fun todayXqj(cal: Calendar = Calendar.getInstance()): Int =
    if (cal.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY) 7 else cal.get(Calendar.DAY_OF_WEEK) - 1

/** 时段问候：5-11 上午 / 11-13 中午 / 13-18 下午 / 18-23 晚上 / 其余凌晨 */
private fun greetingText(hour: Int): String = when (hour) {
    in 5..10 -> "上午好"
    in 11..12 -> "中午好"
    in 13..17 -> "下午好"
    in 18..22 -> "晚上好"
    else -> "凌晨好"
}

/** 今天是否落在 mondayStr（"yyyy-MM-dd[/yyyy-MM-dd]"）起始的那一周（周一到周日闭区间） */
private fun isTodayInWeek(mondayStr: String): Boolean {
    val monday = parseDay(mondayStr) ?: return false
    val today = Calendar.getInstance().midnight()
    return !today.before(monday) && today.timeInMillis - monday.timeInMillis < 7 * DAY_MS
}

/** 周次列表中找今天所在的教学周：返回 (周次 zs, 该周周一)；不在任何已同步周内返回 null */
private fun findCurrentWeek(weeks: List<WeekInfo>): Pair<Int, Calendar>? {
    val today = Calendar.getInstance().midnight()
    for (w in weeks) {
        val monday = parseDay(w.rq) ?: continue
        if (!today.before(monday) && today.timeInMillis - monday.timeInMillis < 7 * DAY_MS) {
            return w.zs to monday
        }
    }
    return null
}

/** "8:10" → 今天该时刻的 epoch ms；非法返回 null */
private fun clockToMillis(text: String): Long? {
    val parts = text.split(":")
    val h = parts.getOrNull(0)?.toIntOrNull() ?: return null
    val m = parts.getOrNull(1)?.toIntOrNull() ?: return null
    return Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, h)
        set(Calendar.MINUTE, m)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}

/** 课程今天的起止时刻（epoch ms）；自定义时间课用 customTime，其余按作息表；无法确定返回 null */
private fun courseStartEnd(c: Course): Pair<Long, Long>? {
    val startText: String
    val endText: String
    if (c.isCustom && c.customTime.isNotEmpty()) {
        startText = c.customTime.substringBefore('-', "")
        endText = c.customTime.substringAfter('-', "")
    } else {
        val table = SectionTimes.tableFor(c.room)
        startText = table.getOrNull(c.startSection - 1)?.start ?: return null
        endText = table.getOrNull(c.endSection - 1)?.end ?: return null
    }
    val start = clockToMillis(startText) ?: return null
    val end = clockToMillis(endText) ?: return null
    return start to end
}

/** 门户应用匹配：同名且 href 非空，优先 xyoauthlogin 链接（与 AppsScreen 白名单逻辑一致） */
private fun matchPortalApp(apps: List<ThirdApp>, entry: HomeEntry): ThirdApp? {
    val candidates = apps.filter { it.name == entry.portalName && it.href.isNotBlank() }
    return candidates.firstOrNull { it.href.contains("xyoauthlogin") } ?: candidates.firstOrNull()
}

// ==================== 首页主体 ====================

/**
 * 首页：渐变头部（问候/日期周次/天气/每日摘录）+ 快捷入口 + 今日课程 + 今天吃什么 + 借阅到期卡片流。
 * 所有卡片随数据缺失自动隐藏；天气/摘录异步加载失败静默降级。
 */
@Composable
fun HomeScreen(onOpenSchedule: () -> Unit, onOpenLeave: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dark = isAppDarkTheme()

    // ---- 天气（null = 未加载/失败，整条不渲染；失败静默）----
    var weather by remember { mutableStateOf<WeatherApi.Weather?>(null) }
    LaunchedEffect(Unit) { weather = WeatherApi.fetch() }

    // ---- 每日摘录：初始本地每日一句，换一句优先一言 API、失败本地随机 ----
    var quote by remember { mutableStateOf(QuoteStore.today()) }

    // ---- 快捷入口显隐（null = 从未自定义 → 默认前 3 个）----
    var enabledEntries by remember {
        mutableStateOf(HomePrefs.getEnabled(context) ?: HomePrefs.DEFAULT_ENTRIES)
    }
    var showEdit by remember { mutableStateOf(false) }

    // ---- 应用内 WebView 打开目标 ----
    var openTarget by remember { mutableStateOf<HomeOpenTarget?>(null) }

    /** 点击快捷入口：电子证走专用解析（等 token+免密链接，同应用宫格/小组件）；请假切 tab；其余门户匹配 → WebView */
    fun openEntry(entry: HomeEntry) {
        // 请假直达底部「请假」tab：LeaveScreen 自带完整学工登录链与表单路由
        if (entry.key == HomePrefs.ENTRY_LEAVE) {
            onOpenLeave()
            return
        }
        // 电子证复用 CredentialLauncher 专用解析（resolveTarget 内部等待会话就绪并优先 xyoauthlogin），
        // 与应用宫格「图书馆电子证」、桌面小组件点击完全同源，避免通用匹配在会话未就绪时打不开
        if (entry.key == HomePrefs.ENTRY_LIBRARY) {
            scope.launch {
                val target = CredentialLauncher.resolveTarget(context)
                if (target != null) {
                    openTarget = HomeOpenTarget(target.name, target.url, target.finalHash)
                } else {
                    Toast.makeText(context, "暂无法打开，请稍后再试", Toast.LENGTH_SHORT).show()
                }
            }
            return
        }
        val matched = ScheduleCache.applications["APPS"]?.let { matchPortalApp(it, entry) }
        if (matched != null) {
            openTarget = HomeOpenTarget(entry.label, matched.ticketUrl(), entry.finalHash)
            return
        }
        // 门户缓存未命中：拉一次应用列表写回缓存后重试一次；仍失败提示
        scope.launch {
            val fetched = PortalApi.getApplications().getOrNull()
            if (fetched == null) {
                Toast.makeText(context, "暂无法打开，请稍后再试", Toast.LENGTH_SHORT).show()
                return@launch
            }
            ScheduleCache.applications["APPS"] = fetched
            val retry = matchPortalApp(fetched, entry)
            if (retry != null) {
                openTarget = HomeOpenTarget(entry.label, retry.ticketUrl(), entry.finalHash)
            } else {
                Toast.makeText(context, "暂无法打开，请稍后再试", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ---- 今日课程数据：优先小组件落盘快照（文件 IO，后台线程加载），
    //      无快照或找不到周 → 回退课表内存缓存（状态读取，后台加载完成自动补上）----
    val term = remember { TermUtils.current() }
    val todayXq = todayXqj()
    var snapshot by remember { mutableStateOf<TodayStore.Snapshot?>(null) }
    LaunchedEffect(Unit) {
        snapshot = withContext(Dispatchers.IO) { TodayStore.read(context) }
    }
    val storeWeek = remember(snapshot) { snapshot?.weeks?.firstOrNull { isTodayInWeek(it.monday) } }
    val todayCourses: List<Course> = if (storeWeek != null) {
        storeWeek.courses.filter { it.dayOfWeek == todayXq }.sortedBy { it.startSection }
    } else {
        val currentWeek = ScheduleCache.weeksList[ScheduleCache.weeksKey(term.xnm, term.xqm)]
            ?.let { findCurrentWeek(it) }
        currentWeek
            ?.let { ScheduleCache.weekData[ScheduleCache.weekKey(term.xnm, term.xqm, it.first)]?.first }
            ?.filter { it.dayOfWeek == todayXq }
            ?.sortedBy { it.startSection }
            .orEmpty()
    }

    // ---- 借阅到期：电子证落地后落盘的原始 JSON 宽容解析（文件 IO + 解析排序全部后台线程）----
    var borrowRows by remember { mutableStateOf<List<BorrowRow>>(emptyList()) }
    LaunchedEffect(Unit) {
        borrowRows = withContext(Dispatchers.IO) {
            val entries = BorrowStore.parseEntries(BorrowStore.loadRaw(context))
            val todayMillis = Calendar.getInstance().midnight().timeInMillis
            (0 until entries.length()).mapNotNull { i ->
                val item = entries.optJSONObject(i) ?: return@mapNotNull null
                val title = BorrowStore.extractTitle(item)
                if (title.isBlank()) return@mapNotNull null
                val date = BorrowStore.extractDate(item)
                val daysLeft = date?.let { d ->
                    parseDay(d)?.let { due -> (due.midnight().timeInMillis - todayMillis) / DAY_MS }
                }
                BorrowRow(title, date, daysLeft)
            }.sortedWith(compareBy({ it.sortGroup }, { it.date ?: "9999-99-99" }))
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // A. 渐变头部（问候/日期周次/天气/每日摘录）
        item {
            GradientHeader(
                dark = dark,
                weather = weather,
                quote = quote,
                onChangeQuote = {
                    scope.launch {
                        val remote = HitokotoApi.fetch()
                        quote = remote ?: QuoteStore.random(quote)
                    }
                },
            )
        }
        // B. 快捷入口卡
        item {
            QuickEntriesCard(
                enabledKeys = enabledEntries,
                onEdit = { showEdit = true },
                onOpen = { openEntry(it) },
            )
        }
        // C. 今日课程卡（无课整卡不渲染）
        if (todayCourses.isNotEmpty()) {
            item { TodayCoursesCard(todayCourses) { onOpenSchedule() } }
        }
        // D. 今天吃什么卡
        item { FoodCard() }
        // E. 借阅到期卡（无条目整卡不渲染）
        if (borrowRows.isNotEmpty()) {
            item { BorrowCard(borrowRows) }
        }
    }

    openTarget?.let { target ->
        AppWebViewDialog(
            name = target.name,
            url = target.url,
            finalHash = target.finalHash,
            onDismiss = { openTarget = null },
        )
    }

    if (showEdit) {
        EditEntriesDialog(
            initiallyEnabled = enabledEntries,
            onConfirm = { picked ->
                enabledEntries = picked
                HomePrefs.setEnabled(context, picked)
                showEdit = false
            },
            onDismiss = { showEdit = false },
        )
    }
}

// ==================== A. 渐变头部 ====================

/** 渐变头部：时段问候 + 姓名 / 日期·周次·倒计时 / 天气条 / 每日摘录（内含换一句） */
@Composable
private fun GradientHeader(
    dark: Boolean,
    weather: WeatherApi.Weather?,
    quote: Quote,
    onChangeQuote: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Brush.verticalGradient(if (dark) HEADER_GRADIENT_DARK else HEADER_GRADIENT_LIGHT))
            .padding(16.dp),
    ) {
        // 第一行：时段问候 + 姓名（学籍卡 → 课表学籍 → 「同学」）
        val now = Calendar.getInstance()
        val name = ScheduleCache.profileData["PROFILE"]?.info?.name?.takeIf { it.isNotBlank() }
            ?: ScheduleCache.weekData.values.firstOrNull()?.second?.name?.takeIf { it.isNotBlank() }
            ?: "同学"
        Text(
            "${greetingText(now.get(Calendar.HOUR_OF_DAY))}，$name",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = Color.White,
        )
        Spacer(Modifier.height(4.dp))

        // 第二行：日期 星期 · 第N周 · 距学期结束 N 天（任何一步推算失败只显示日期星期）
        var subline = "${now.get(Calendar.MONTH) + 1}月${now.get(Calendar.DAY_OF_MONTH)}日 " +
            WEEKDAY_LABELS[todayXqj(now) - 1]
        val weeks = ScheduleCache.weeksList[ScheduleCache.weeksKey(TermUtils.current(now).xnm, TermUtils.current(now).xqm)]
        if (weeks != null) {
            val currentWeek = findCurrentWeek(weeks)
            if (currentWeek != null) {
                subline += " · 第${currentWeek.first}周"
                // 学期结束 = 最后一周周一 + 6 天；剩余 = (期末 - 今天0点) / 86400000
                val endMillis = weeks.maxByOrNull { it.zs }
                    ?.let { parseDay(it.rq) }
                    ?.let { it.midnight().timeInMillis + 6 * DAY_MS }
                if (endMillis != null) {
                    val daysLeft = (endMillis - now.midnight().timeInMillis) / DAY_MS
                    if (daysLeft >= 0) subline += " · 距学期结束 $daysLeft 天"
                }
            }
        }
        Text(subline, fontSize = 13.sp, color = Color.White)
        Spacer(Modifier.height(12.dp))

        // 天气条：null 整条不渲染；半透明白底圆角
        weather?.let { w ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.White.copy(alpha = 0.16f))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(WeatherApi.wmoEmoji(w.code), fontSize = 26.sp)
                Spacer(Modifier.width(8.dp))
                Text(
                    "%.0f°C".format(w.tempC),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
                Text(
                    "${w.desc} · 最高${w.maxC}° 最低${w.minC}° · 新余",
                    fontSize = 12.sp,
                    color = Color.White,
                    textAlign = TextAlign.End,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 8.dp),
                )
            }
            Spacer(Modifier.height(12.dp))
        }

        // 每日摘录：分隔线 + 斜体句子 + 出处与「换一句」
        HorizontalDivider(color = Color.White.copy(alpha = 0.22f))
        Spacer(Modifier.height(10.dp))
        Text(
            quote.content,
            fontSize = 14.sp,
            lineHeight = 22.sp,
            fontStyle = FontStyle.Italic,
            color = Color.White,
        )
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "——${quote.source} · 每日摘录",
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.weight(1f),
            )
            Text(
                "换一句 ›",
                fontSize = 12.sp,
                color = Color.White,
                modifier = Modifier
                    .clickable(onClick = onChangeQuote)
                    .padding(start = 12.dp, top = 4.dp, bottom = 4.dp),
            )
        }
    }
}

// ==================== B. 快捷入口卡 ====================

/** 快捷入口卡：3 列 emoji 宫格 + 右上角「编辑」显隐设置 */
@Composable
private fun QuickEntriesCard(
    enabledKeys: Set<String>,
    onEdit: () -> Unit,
    onOpen: (HomeEntry) -> Unit,
) {
    Column(cardModifier()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "快捷入口",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onEdit) { Text("编辑") }
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            HOME_ENTRIES.filter { it.key in enabledKeys }.chunked(3).forEach { rowEntries ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    rowEntries.forEach { entry ->
                        Column(
                            Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { onOpen(entry) }
                                .padding(vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(entry.emoji, fontSize = 26.sp)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                entry.label,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    // 末行不足 3 个时补空位保持等宽
                    repeat(3 - rowEntries.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

/** 编辑快捷入口弹窗：全候选复选，勾选 = 显示；确认写 HomePrefs */
@Composable
private fun EditEntriesDialog(
    initiallyEnabled: Set<String>,
    onConfirm: (Set<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    var tempEnabled by remember { mutableStateOf(initiallyEnabled) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑快捷入口") },
        text = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    "取消勾选的入口将从首页隐藏，随时可以再打开。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                HOME_ENTRIES.forEach { entry ->
                    val shown = entry.key in tempEnabled
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                tempEnabled = if (shown) tempEnabled - entry.key else tempEnabled + entry.key
                            }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = shown,
                            onCheckedChange = { v ->
                                tempEnabled = if (v) tempEnabled + entry.key else tempEnabled - entry.key
                            },
                        )
                        Text("${entry.emoji} ${entry.label}", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(tempEnabled) }) { Text("完成") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

// ==================== C. 今日课程卡 ====================

/** 今日课程卡：标题行（节数 + 进课表）+ 课程行列表（节次徽章/上课中/教室时间） */
@Composable
private fun TodayCoursesCard(courses: List<Course>, onOpenSchedule: () -> Unit) {
    Column(cardModifier()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "今日课程 · ${courses.size} 节",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.weight(1f))
            Text(
                "进课表 ›",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clickable(onClick = onOpenSchedule)
                    .padding(start = 12.dp, top = 4.dp, bottom = 4.dp),
            )
        }
        Spacer(Modifier.height(10.dp))
        courses.forEachIndexed { index, course ->
            TodayCourseRow(course)
            if (index < courses.lastIndex) Spacer(Modifier.height(8.dp))
        }
    }
}

/** 单条课程行：节次徽章 + 课程名（上课中加徽章）+ 教室与起止时间 */
@Composable
private fun TodayCourseRow(c: Course) {
    val bounds = courseStartEnd(c)
    val now = System.currentTimeMillis()
    val inProgress = bounds != null && now >= bounds.first && now <= bounds.second
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            "${c.startSection}-${c.endSection}节",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.primaryContainer)
                .padding(horizontal = 8.dp, vertical = 4.dp),
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    c.name,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (inProgress) {
                    Text(
                        "上课中",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.primary)
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
            // 教室行：@教室 · 起止时间（自定义时间课直接用 customTime，其余按教室作息表）
            val timeText = when {
                c.isCustom && c.customTime.isNotEmpty() -> c.customTime
                else -> SectionTimes.rangeText(c.startSection, c.endSection, c.room)
            }
            val roomLine = buildString {
                if (c.room.isNotBlank()) append("@").append(c.room)
                if (timeText.isNotBlank()) {
                    if (isNotEmpty()) append(" · ")
                    append(timeText)
                }
            }
            if (roomLine.isNotEmpty()) {
                Text(
                    roomLine,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

// ==================== D. 今天吃什么卡 ====================

/** 今天吃什么卡：🍜 + 食堂·菜品 + 「换一个」红棕胶囊按钮（纯本地随机） */
@Composable
private fun FoodCard() {
    var meal by remember { mutableStateOf(FoodRecommender.random(null)) }
    Row(cardModifier(), verticalAlignment = Alignment.CenterVertically) {
        Text("🍜", fontSize = 30.sp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "今天吃什么",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "${meal.canteen} · ${meal.dish}",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            "换一个",
            fontSize = 13.sp,
            color = Color.White,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(Color(0xFFC2503F))
                .clickable { meal = FoodRecommender.random(meal) }
                .padding(horizontal = 14.dp, vertical = 8.dp),
        )
    }
}

// ==================== E. 借阅到期卡 ====================

/** 借阅到期卡：书名 + 应还日期 + 徽章（剩N天/已超期N天，warn 色随深浅色切换） */
@Composable
private fun BorrowCard(rows: List<BorrowRow>) {
    val dark = isAppDarkTheme()
    Column(cardModifier()) {
        Text(
            "借阅到期",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(10.dp))
        rows.forEachIndexed { index, row ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        row.title,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (row.date != null) {
                        Text(
                            "应还 ${row.date}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                val badgeText = when {
                    row.daysLeft != null && row.daysLeft < 0 -> "已超期${-row.daysLeft}天"
                    row.daysLeft != null && row.daysLeft <= 3 -> "剩${row.daysLeft}天"
                    else -> null
                }
                if (badgeText != null) {
                    // warn 色：浅色 #B9680A 字 + #FDF1E2 底；深色 #E8A44A 字 + #3A2D14 底
                    val (fg, bg) = if (dark) {
                        Color(0xFFE8A44A) to Color(0xFF3A2D14)
                    } else {
                        Color(0xFFB9680A) to Color(0xFFFDF1E2)
                    }
                    Text(
                        badgeText,
                        fontSize = 11.sp,
                        color = fg,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(bg)
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }
            if (index < rows.lastIndex) Spacer(Modifier.height(8.dp))
        }
    }
}

// ==================== 通用 ====================

/** 白色卡片底：surface 底色 + 14dp 圆角 + 内衬 */
@Composable
private fun cardModifier(): Modifier = Modifier
    .fillMaxWidth()
    .clip(RoundedCornerShape(14.dp))
    .background(MaterialTheme.colorScheme.surface)
    .padding(14.dp)
