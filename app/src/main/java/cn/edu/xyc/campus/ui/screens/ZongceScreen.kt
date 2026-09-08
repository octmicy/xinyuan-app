package cn.edu.xyc.campus.ui.screens

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import cn.edu.xyc.campus.data.grades.AvgScoreCalculator
import cn.edu.xyc.campus.data.local.AvgScorePrefs
import cn.edu.xyc.campus.data.local.ScheduleCache
import cn.edu.xyc.campus.data.model.GradeItem
import cn.edu.xyc.campus.data.remote.JwxtApi
import cn.edu.xyc.campus.data.remote.JwxtResult
import cn.edu.xyc.campus.data.remote.TermUtils
import cn.edu.xyc.campus.data.zongce.ZongceAwardRecord
import cn.edu.xyc.campus.data.zongce.ZongceBonusCount
import cn.edu.xyc.campus.data.zongce.ZongceCalculator
import cn.edu.xyc.campus.data.zongce.ZongceDraft
import cn.edu.xyc.campus.data.zongce.ZongceDraftStore
import cn.edu.xyc.campus.data.zongce.ZongceLabFlags
import cn.edu.xyc.campus.data.zongce.ZongceModules
import cn.edu.xyc.campus.data.zongce.ZongcePenalty
import cn.edu.xyc.campus.data.zongce.ZongceTables
import java.util.Calendar
import kotlinx.coroutines.launch

/**
 * 综测计算器：全屏 Dialog，草稿实时保存（filesDir/zongce_draft.json），结果实时刷新。
 * 查表/合成逻辑见 data/zongce 包，本文件只做录入与展示。
 */

// 查表外、直接填分值的类目
private const val CUSTOM_CATEGORY = "__custom__"
private const val ZHI_RESEARCH = "zhi_research"
private const val TI_MENTAL = "ti_mental"

/** 各模块「添加奖励」对话框的类目选项（key → 标签） */
private fun moduleCategories(module: String): List<Pair<String, String>> = when (module) {
    ZongceModules.DE -> listOf(
        ZongceTables.DE_IDEO_ACTIVITY to "思想教育类活动",
        ZongceTables.DE_DEED to "优秀事迹",
        ZongceTables.DE_HONOR_PERSON to "个人荣誉称号",
        ZongceTables.DE_HONOR_GROUP to "集体荣誉称号",
        ZongceTables.DE_CADRE to "学生骨干考核优秀（+4）",
    )
    ZongceModules.ZHI -> listOf(
        ZongceTables.ZHI_INNOVATION to "互联网+大赛",
        ZongceTables.ZHI_CHALLENGE_ACADEMIC to "挑战杯（学术）",
        ZongceTables.ZHI_CHALLENGE_STARTUP to "挑战杯（创业）",
        ZongceTables.ZHI_CHUANGYI to "中国创翼",
        ZongceTables.ZHI_CONTEST_RANKED to "学科竞赛（排行榜内）",
        ZongceTables.ZHI_CONTEST_OTHER to "学科竞赛（排行榜外）",
        ZongceTables.ZHI_PAPER to "学术论文",
        ZongceTables.ZHI_BOOK to "著作",
        ZongceTables.ZHI_PATENT to "专利",
        ZHI_RESEARCH to "科研项目（直填分值）",
        ZongceTables.ZHI_BIGCUTI to "大创项目",
        ZongceTables.ZHI_STARTUP_YEARS to "创业年限",
        ZongceTables.ZHI_CERT_COMPUTER to "计算机等级证书",
        ZongceTables.ZHI_CERT_FOREIGN to "外语等级证书",
        ZongceTables.ZHI_CERT_VOCATIONAL to "职业技能证书",
    )
    ZongceModules.TI -> listOf(
        ZongceTables.TI_CONTEST to "体育竞赛",
        TI_MENTAL to "心理健康活动（直填分值）",
    )
    ZongceModules.MEI -> listOf(
        ZongceTables.MEI_AWARD to "美育获奖",
        ZongceTables.MEI_ART_CONTEST to "艺术竞赛",
        ZongceTables.MEI_COURSE to "美育课程合格（+5）",
    )
    else -> emptyList()
} + (CUSTOM_CATEGORY to "自定义（直接填分值）")

private val CATEGORY_LABELS: Map<String, String> =
    listOf(ZongceModules.DE, ZongceModules.ZHI, ZongceModules.TI, ZongceModules.MEI)
        .flatMap { moduleCategories(it) }
        .toMap()

private val LEVEL_LABELS = mapOf(
    ZongceTables.LEVEL_NATIONAL to "国家级",
    ZongceTables.LEVEL_PROVINCE to "省级",
    ZongceTables.LEVEL_CITY to "市级",
    ZongceTables.LEVEL_SCHOOL to "校级",
    ZongceTables.LEVEL_COLLEGE to "院级",
    ZongceTables.LEVEL_NONE to "不限级别",
)

private val GRADE_LABELS = mapOf(
    ZongceTables.GRADE_FIRST to "一等奖",
    ZongceTables.GRADE_SECOND to "二等奖",
    ZongceTables.GRADE_THIRD to "三等奖",
    ZongceTables.GRADE_SPECIAL to "特等奖",
    ZongceTables.GRADE_EXCELLENT to "优秀奖",
    ZongceTables.GRADE_OTHER to "其他",
    ZongceTables.GRADE_GOOD to "优",
    ZongceTables.GRADE_GOLD to "金奖",
    ZongceTables.GRADE_SILVER to "银奖",
    ZongceTables.GRADE_BRONZE to "铜奖",
    ZongceTables.GRADE_JOIN to "参与未获奖",
    ZongceTables.GRADE_RECOGNIZED to "认定",
    ZongceTables.GRADE_PASS to "合格",
    ZongceTables.GRADE_EACH to "每项",
)

private val PENALTY_LABELS = mapOf(
    ZongcePenalty.DE_YUAN_CRITICISM to "院通报批评",
    ZongcePenalty.DE_SCHOOL_CRITICISM to "校通报批评",
    ZongcePenalty.DE_WARNING to "警告",
    ZongcePenalty.DE_SERIOUS_WARNING to "严重警告",
    ZongcePenalty.DE_DEMOTION to "记过",
    ZongcePenalty.DE_LEAGUE_WARNING to "团内警告",
    ZongcePenalty.DE_LEAGUE_SERIOUS to "团内严重警告",
    ZongcePenalty.DE_LEAGUE_REMOVE to "撤销团内职务",
    ZongcePenalty.DE_ABSENT to "缺勤（活动/会议）",
    ZongcePenalty.DE_FRESH_EXAM to "新生入学考试不及格",
    ZongcePenalty.ZHI_FAIL_COURSE to "课程不及格",
    ZongcePenalty.ZHI_RETAKE_FAIL to "补考不及格",
    ZongcePenalty.ZHI_ABSENT to "缺勤实习/学术活动",
    ZongcePenalty.ZHI_EXPULSION_WARNING to "退学警告/未获毕业资格",
    ZongcePenalty.TI_ABSENT to "缺勤（体育活动）",
    ZongcePenalty.TI_QUIT to "弃权",
    ZongcePenalty.MEI_DISORDER to "扰乱秩序",
    ZongcePenalty.MEI_LEAVE_EARLY to "未满服务期退团",
    ZongcePenalty.MEI_QUIT to "弃权",
)

private val BONUS_LABELS = mapOf(
    ZongceBonusCount.BONUS_PE_DAILY to "日常锻炼",
    ZongceBonusCount.BONUS_PE_COMMEND to "院级表彰",
    ZongceBonusCount.BONUS_MEI_CAMPUS_AUDIENCE to "校内实践·观众",
    ZongceBonusCount.BONUS_MEI_CAMPUS_JOIN to "校内参与/校级表彰",
    ZongceBonusCount.BONUS_MEI_OUT_AUDIENCE to "校外实践·观众",
    ZongceBonusCount.BONUS_MEI_OUT_CITY to "校外市级参与",
    ZongceBonusCount.BONUS_MEI_OUT_PROVINCE to "校外省级参与",
    ZongceBonusCount.BONUS_MEI_OUT_NATIONAL to "校外国家级参与",
)

/** 学年 key：9-12 月属 "y-(y+1)"，1-8 月属 "(y-1)-y"（与 TermUtils 规则一致） */
private fun currentYearKey(): String {
    val now = Calendar.getInstance()
    val y = now.get(Calendar.YEAR)
    val m = now.get(Calendar.MONTH) + 1
    return if (m in 9..12) "$y-${y + 1}" else "${y - 1}-$y"
}

private fun formatNum(v: Double): String =
    if (v == Math.floor(v) && !v.isInfinite()) v.toLong().toString() else v.toString()

private fun gradeColor(g: String): Color = when (g) {
    "A" -> Color(0xFF1E5AA8)
    "B" -> Color(0xFF2E7D32)
    "C" -> Color(0xFFB45309)
    else -> Color(0xFFC62828)
}

private fun toggleFlag(flags: Set<String>, key: String, on: Boolean): Set<String> =
    if (on) flags + key else flags - key

@Composable
fun ZongceScreen(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val yearKey = remember { currentYearKey() }
    var draft by remember {
        mutableStateOf(
            ZongceDraftStore.load(context).let {
                if (it.yearKey == yearKey) it else ZongceDraftStore.defaultDraft(yearKey)
            }
        )
    }
    var showClear by remember { mutableStateOf(false) }

    // 拆帧：先弹对话框窗口，首帧只渲染标题栏 + 列表骨架，下一帧再填充全部区块，避免打开瞬间一大帧卡顿
    var formReady by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        formReady = true
    }

    val update: (ZongceDraft) -> Unit = { nd ->
        val fixed = nd.copy(yearKey = yearKey)
        draft = fixed
        ZongceDraftStore.save(context, fixed)
    }

    val result = remember(draft) { ZongceCalculator.finalize(draft) }
    val notes = remember(draft) {
        ZongceCalculator.m1De(draft).notes +
            ZongceCalculator.m2Zhi(draft).notes +
            ZongceCalculator.m3Ti(draft).notes +
            ZongceCalculator.m4Mei(draft).notes
    } + if (result.deDirectD) listOf("德育为D等，综测等次按D，以学院认定为准") else emptyList()
    val explainLines = remember(draft) { ZongceCalculator.explain(draft) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.96f)
                .background(MaterialTheme.colorScheme.background, RoundedCornerShape(20.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            // 顶部栏
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("综测计算", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "学年 $yearKey",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "按《学生综合素质测评办法》计算，结果仅供自评参考",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "计算结果见页面底部 ↓",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, "关闭") }
            }

            LazyColumn(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // 拆帧首屏：先只渲染骨架占位，下一帧再填充全部区块
                if (!formReady) {
                    item {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(160.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(Modifier.size(26.dp))
                        }
                    }
                } else {
                    item(key = "de") { DeSection(draft, update) }
                    item(key = "zhi") { ZhiSection(draft, update) }
                    item(key = "ti") { TiSection(draft, update) }
                    item(key = "mei") { MeiSection(draft, update) }
                    item(key = "labor") { LaborSection(draft, update) }
                    item(key = "extra") {
                        OutlinedButton(
                            onClick = { showClear = true },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("清空本学年草稿") }
                    }
                    // 计算结果卡固定在页面最底部
                    item(key = "result") {
                        ResultCard(
                            result = result,
                            notes = notes,
                            explainLines = explainLines,
                            includeLabor = draft.includeLabor,
                            oneVoteChecked = draft.oneVoteExcellent,
                            onOneVoteChange = { v -> update(draft.copy(oneVoteExcellent = v)) },
                        )
                        Spacer(Modifier.height(6.dp))
                    }
                }
            }
        }

        if (showClear) {
            AlertDialog(
                onDismissRequest = { showClear = false },
                title = { Text("清空本学年草稿") },
                text = { Text("将删除 $yearKey 学年的全部填写数据，确定清空？") },
                confirmButton = {
                    TextButton(onClick = {
                        val nd = ZongceDraftStore.clear(context).copy(yearKey = yearKey)
                        draft = nd
                        ZongceDraftStore.save(context, nd)
                        showClear = false
                    }) { Text("清空", color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = {
                    TextButton(onClick = { showClear = false }) { Text("取消") }
                },
            )
        }
    }
}

private fun penaltyLabel(key: String): String {
    val v = ZongcePenalty.VALUES[key] ?: 0.0
    return "${PENALTY_LABELS[key] ?: key}（−${formatNum(v)}/次）"
}

private fun bonusLabel(key: String): String {
    val v = ZongceBonusCount.VALUES[key] ?: 0.0
    return "${BONUS_LABELS[key] ?: key}（+${formatNum(v)}/次）"
}

// ---------- 结果卡 ----------

@Composable
private fun ResultCard(
    result: ZongceCalculator.FinalResult,
    notes: List<String>,
    explainLines: List<String>,
    includeLabor: Boolean,
    oneVoteChecked: Boolean,
    onOneVoteChange: (Boolean) -> Unit,
) {
    var formulaOpen by remember { mutableStateOf(true) }
    SectionCard(title = "测算结果（实时）") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        "T ",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        formatNum(result.t),
                        fontSize = 34.sp,
                        lineHeight = 38.sp,
                        fontWeight = FontWeight.Bold,
                        color = gradeColor(result.grade),
                    )
                }
                Text(
                    "综合总分",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(gradeColor(result.grade))
                    .padding(horizontal = 14.dp, vertical = 6.dp),
            ) {
                Text(
                    "等次 ${result.grade}",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
        HorizontalDivider()
        ResultRow("德育 Z1", formatNum(result.z1))
        ResultRow("智育 Z2", formatNum(result.z2))
        ResultRow("增值 Z5", formatNum(result.z5))
        ResultRow("体育 Z3", formatNum(result.z3))
        ResultRow("美育 Z4", formatNum(result.z4))
        HorizontalDivider()
        Row(Modifier.fillMaxWidth()) {
            Text("劳动教育等次", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(
                result.laborGrade,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = gradeColor(result.laborGrade),
            )
        }
        if (!includeLabor) {
            NoteLine("劳动等次已关闭，不参与综测等次评定")
        }
        CheckRow("一票定优（综测等次直接 A）", oneVoteChecked, onOneVoteChange)
        Text(
            "常见来源：互联网+省金、挑战杯省特/省金、中国创翼省一、北大核心以上论文、科研项目重大贡献经认定",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (result.oneVote) {
            NoteLine("已触发一票定优（勾选或命中一票定优档奖励），等次直接 A")
        }
        notes.forEach { NoteLine(it) }
        HorizontalDivider()
        // 计算公式：逐行代值展示，供他人核对计算过程
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { formulaOpen = !formulaOpen },
        ) {
            Text("计算公式", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Icon(
                Icons.Rounded.ArrowDropDown,
                contentDescription = if (formulaOpen) "收起" else "展开",
                modifier = Modifier.rotate(if (formulaOpen) 180f else 0f),
            )
        }
        if (formulaOpen) {
            explainLines.forEach { line ->
                Text(
                    line,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ResultRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun NoteLine(text: String) {
    val important = text.contains("记 0 分") || text.contains("按D") || text.contains("一票定优")
    Row {
        Text(
            "· ",
            style = MaterialTheme.typography.bodySmall,
            color = if (important) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = if (important) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ---------- 劳动区 ----------

@Composable
private fun LaborSection(draft: ZongceDraft, update: (ZongceDraft) -> Unit) {
    val labor = ZongceCalculator.m5Labor(draft)
    SectionCard(
        title = "劳动教育等次",
        subtitle = "按 D→C→B→A 规则链判定，不计入总分 T",
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("劳动等次参与综测等次评定", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Switch(checked = draft.includeLabor, onCheckedChange = { v -> update(draft.copy(includeLabor = v)) })
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("当前劳动等次", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(
                labor,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = gradeColor(labor),
            )
        }
        if (!draft.includeLabor) {
            Text(
                "劳动等次已关闭",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(
            Modifier.alpha(if (draft.includeLabor) 1f else 0.4f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ChipRow(
                label = "宿舍星级",
                options = listOf(0 to "无", 1 to "三星", 2 to "四星", 3 to "五星"),
                selected = draft.dormStar,
                onSelect = { v -> update(draft.copy(dormStar = v)) },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumField(
                    label = "志愿服务次数",
                    value = draft.volunteerCount.toDouble(),
                    onValue = { v -> update(draft.copy(volunteerCount = v.toInt().coerceAtLeast(0))) },
                    modifier = Modifier.weight(1f),
                )
                NumField(
                    label = "校级通报批评次数",
                    value = draft.labCriticism.toDouble(),
                    onValue = { v -> update(draft.copy(labCriticism = v.toInt().coerceAtLeast(0))) },
                    modifier = Modifier.weight(1f),
                )
            }
            CheckRow("寒暑假实践获校级荣誉（满足可升 B）", ZongceLabFlags.SUMMER_HONOR in draft.labFlags) { v ->
                update(draft.copy(labFlags = toggleFlag(draft.labFlags, ZongceLabFlags.SUMMER_HONOR, v)))
            }
            CheckRow("劳动竞赛获省级以下荣誉（满足可升 B）", ZongceLabFlags.CONTEST_BELOW_PROVINCE in draft.labFlags) { v ->
                update(draft.copy(labFlags = toggleFlag(draft.labFlags, ZongceLabFlags.CONTEST_BELOW_PROVINCE, v)))
            }
            CheckRow("实践获省级及以上媒体报道（满足可升 A）", ZongceLabFlags.MEDIA_PROVINCE in draft.labFlags) { v ->
                update(draft.copy(labFlags = toggleFlag(draft.labFlags, ZongceLabFlags.MEDIA_PROVINCE, v)))
            }
            CheckRow("劳动竞赛获省级及以上荣誉（满足可升 A）", ZongceLabFlags.CONTEST_PROVINCE in draft.labFlags) { v ->
                update(draft.copy(labFlags = toggleFlag(draft.labFlags, ZongceLabFlags.CONTEST_PROVINCE, v)))
            }
            val dChecked = ZongceLabFlags.DESTROY in draft.labFlags || ZongceLabFlags.MAJOR_NEGATIVE in draft.labFlags
            CheckRow("无故不参加集体劳动且有破坏行为/其他重大负面影响（直判 D）", dChecked) { v ->
                update(
                    draft.copy(
                        labFlags = if (v) {
                            draft.labFlags + ZongceLabFlags.DESTROY + ZongceLabFlags.MAJOR_NEGATIVE
                        } else {
                            draft.labFlags - ZongceLabFlags.DESTROY - ZongceLabFlags.MAJOR_NEGATIVE
                        }
                    )
                )
            }
        }
    }
}

// ---------- 各模块录入区（LazyColumn 区块） ----------

@Composable
private fun DeSection(draft: ZongceDraft, update: (ZongceDraft) -> Unit) {
    SectionCard(
        title = "德育（Z1）",
        subtitle = "基础分 70 + 民主评议 10 = 80，默认已计入；奖励封顶 20 分。学生骨干考核优秀 +4 在「添加奖励」中录入",
    ) {
        AwardSection(ZongceModules.DE, draft, update)
        HorizontalDivider()
        Text("处罚计次", style = MaterialTheme.typography.titleSmall)
        ZongcePenalty.DE_KEYS.forEach { key ->
            StepperRow(
                label = penaltyLabel(key),
                count = draft.penalties[key] ?: 0,
                onChange = { n -> update(draft.copy(penalties = draft.penalties + (key to n))) },
            )
        }
        HorizontalDivider()
        Text("D 等直判（任一勾选，综测等次按 D，以学院认定为准）", style = MaterialTheme.typography.titleSmall)
        CheckRow("有不良诚信记录", draft.deDishonesty) { v -> update(draft.copy(deDishonesty = v)) }
        CheckRow("有损社会公德/声誉的行为", draft.deBadBehavior) { v -> update(draft.copy(deBadBehavior = v)) }
        CheckRow("受到留校察看及以上处分", draft.deProbation) { v -> update(draft.copy(deProbation = v)) }
        CheckRow("留团察看/开除团籍", draft.deLeaguePunishment) { v -> update(draft.copy(deLeaguePunishment = v)) }
    }
}

@Composable
private fun ZhiSection(draft: ZongceDraft, update: (ZongceDraft) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var importing by remember { mutableStateOf(false) }
    var importResult by remember { mutableStateOf<ZhiImportResult?>(null) }
    // 导入结果统一在组合侧消费：用最新 draft 做 copy，避免协程闭包持有旧草稿覆盖用户输入
    LaunchedEffect(importResult) {
        when (val r = importResult ?: return@LaunchedEffect) {
            is ZhiImportResult.Ok -> {
                update(draft.copy(avgScore = r.avgScore, gpaX1 = r.gpa1, gpaX2 = r.gpa2))
                Toast.makeText(
                    context,
                    "已导入 ${r.yearLabel} 学年平均学分绩 %.2f 与两学期绩点".format(r.avgScore),
                    Toast.LENGTH_SHORT,
                ).show()
            }
            is ZhiImportResult.SessionExpired ->
                Toast.makeText(context, "登录已过期，请重新登录后导入", Toast.LENGTH_SHORT).show()
            is ZhiImportResult.Failed ->
                Toast.makeText(context, "导入失败：${r.message}", Toast.LENGTH_SHORT).show()
            is ZhiImportResult.NoScores ->
                Toast.makeText(context, "${r.yearLabel} 学年暂无可参与计算的成绩", Toast.LENGTH_SHORT).show()
        }
        importing = false
        importResult = null
    }
    SectionCard(
        title = "智育（Z2）",
        subtitle = "「从成绩导入」自动取上一学年成绩（综测按上一学年评定）；也可手动填写，以学院核算表为准",
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumField(
                label = "平均学分绩",
                value = draft.avgScore,
                onValue = { v -> update(draft.copy(avgScore = v.coerceIn(0.0, 100.0))) },
                modifier = Modifier.weight(1f),
                supportingText = "参考学校发的整学年测评表格中的『学分加权平均分』",
            )
            NumField(
                label = "绩点 X1",
                value = draft.gpaX1,
                onValue = { v -> update(draft.copy(gpaX1 = v.coerceIn(0.0, 10.0))) },
                modifier = Modifier.weight(1f),
                supportingText = "参考第一学期表格中的『平均学分绩点』",
            )
            NumField(
                label = "绩点 X2",
                value = draft.gpaX2,
                onValue = { v -> update(draft.copy(gpaX2 = v.coerceIn(0.0, 10.0))) },
                modifier = Modifier.weight(1f),
                supportingText = "参考第二学期表格中的『平均学分绩点』",
            )
        }
        OutlinedButton(
            onClick = {
                if (importing) return@OutlinedButton
                val year = draft.yearKey
                if (year.isEmpty()) {
                    Toast.makeText(context, "请先选择学年", Toast.LENGTH_SHORT).show()
                    return@OutlinedButton
                }
                importing = true
                scope.launch { importResult = importZhiScores(context, year) }
            },
            enabled = !importing,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (importing) {
                CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(6.dp))
            } else {
                Icon(Icons.Rounded.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
            }
            Text(if (importing) "导入中…" else "从成绩导入")
        }
        AwardSection(ZongceModules.ZHI, draft, update)
        HorizontalDivider()
        Text("处罚计次", style = MaterialTheme.typography.titleSmall)
        ZongcePenalty.ZHI_KEYS.forEach { key ->
            StepperRow(
                label = penaltyLabel(key),
                count = draft.penalties[key] ?: 0,
                onChange = { n -> update(draft.copy(penalties = draft.penalties + (key to n))) },
            )
        }
    }
}

// ---------- 智育：从成绩导入 ----------

/** 「从成绩导入」结果：Ok 携带三项指标与实际导入学年，其余为失败态（UI 统一提示，不落草稿） */
private sealed interface ZhiImportResult {
    data class Ok(val avgScore: Double, val gpa1: Double, val gpa2: Double, val yearLabel: String) : ZhiImportResult
    data object SessionExpired : ZhiImportResult
    data class Failed(val message: String) : ZhiImportResult
    data class NoScores(val yearLabel: String) : ZhiImportResult
}

/** 单学期加权平均绩点：与成绩页 summarize 同口径（学分>0 且绩点>0 加权），保留两位；无有效记录为 0.0 */
private fun termGpa(items: List<GradeItem>): Double {
    val valid = items.filter { it.credit > 0 && it.gradePoint > 0 }
    val credits = valid.sumOf { it.credit }
    if (credits <= 0) return 0.0
    return Math.round(valid.sumOf { it.gradePoint * it.credit } / credits * 100.0) / 100.0
}

/** 拉取一学期成绩：ScheduleCache 命中即用，未命中请求教务后写回缓存（缓存键与成绩页一致） */
private suspend fun loadGradesCached(term: TermUtils.Term): JwxtResult<List<GradeItem>> {
    val key = ScheduleCache.gradeKey(term.xnm, term.xqm)
    ScheduleCache.gradeData[key]?.let { return JwxtResult.Ok(it) }
    val r = JwxtApi.getGrades(term)
    if (r is JwxtResult.Ok) ScheduleCache.gradeData[key] = r.data
    return r
}

/**
 * 智育「从成绩导入」：
 * - 综测按上一学年表现评定：草稿学年 yearKey（如 "2026-2027"）的智育数据取其**上一学年**（2025-2026）成绩；
 * - 平均学分绩：三学期成绩合并去重，按成绩页的勾选排除集（AvgScorePrefs，键与成绩页一致用学年起始年）计算；
 * - 绩点 X1/X2：第 1/2 学期原始列表按 summarize 口径计算，该学期无有效记录为 0.0。
 * 任一学期会话失效/请求失败即整体失败，不产出部分结果。
 */
private suspend fun importZhiScores(context: Context, yearKey: String): ZhiImportResult {
    // 综测草稿学年 "2026-2027" → 上一学年起始年 "2025"；教务按学年起始年查询（TermUtils.of 的 xnm 参数），
    // 缓存键/勾选键随之与成绩页该学年一致
    val start = yearKey.substringBefore('-').toIntOrNull() ?: return ZhiImportResult.Failed("学年格式不正确")
    val xnm = (start - 1).toString()
    val yearLabel = "$xnm-${start}"
    val lists = mutableListOf<List<GradeItem>>()
    for (n in 1..3) {
        when (val r = loadGradesCached(TermUtils.of(xnm, n))) {
            is JwxtResult.Ok -> lists += r.data
            is JwxtResult.SessionExpired -> return ZhiImportResult.SessionExpired
            is JwxtResult.Failed -> return ZhiImportResult.Failed(r.message)
        }
    }
    val avg = AvgScoreCalculator.compute(
        AvgScoreCalculator.subjects(AvgScoreCalculator.dedupe(lists.flatten())),
        AvgScorePrefs.getExcluded(context, xnm),
    ).avgScore ?: return ZhiImportResult.NoScores(yearLabel)
    // lists 按 1..3 顺序拉取，[0]/[1] 即第 1/2 学期的原始列表
    return ZhiImportResult.Ok(avg, termGpa(lists[0]), termGpa(lists[1]), yearLabel)
}

@Composable
private fun TiSection(draft: ZongceDraft, update: (ZongceDraft) -> Unit) {
    SectionCard(title = "体育（Z3）") {
        ChipRow(
            label = "年级档",
            options = listOf(1 to "大一大二", 2 to "大三大四"),
            selected = draft.peGradeLevel,
            onSelect = { v -> update(draft.copy(peGradeLevel = v)) },
        )
        ChipRow(
            label = "体质测试",
            options = listOf(0 to "合格", 1 to "不合格", 2 to "免测"),
            selected = draft.peTest,
            onSelect = { v -> update(draft.copy(peTest = v)) },
        )
        SwitchRow("是否完成俱乐部（完成 +10）", draft.peClubDone) { v ->
            update(draft.copy(peClubDone = v))
        }
        SwitchRow("是否跑完乐跑（跑完 +20）", draft.peRunDone) { v ->
            update(draft.copy(peRunDone = v))
        }
        CheckRow("破纪录第一名（+5）", draft.peRecord) { v -> update(draft.copy(peRecord = v)) }
        AwardSection(ZongceModules.TI, draft, update)
        HorizontalDivider()
        Text("奖励计次", style = MaterialTheme.typography.titleSmall)
        StepperRow(
            label = bonusLabel(ZongceBonusCount.BONUS_PE_DAILY),
            count = draft.bonusCounts[ZongceBonusCount.BONUS_PE_DAILY] ?: 0,
            onChange = { n ->
                update(draft.copy(bonusCounts = draft.bonusCounts + (ZongceBonusCount.BONUS_PE_DAILY to n)))
            },
        )
        StepperRow(
            label = bonusLabel(ZongceBonusCount.BONUS_PE_COMMEND),
            count = draft.bonusCounts[ZongceBonusCount.BONUS_PE_COMMEND] ?: 0,
            max = 1,
            onChange = { n ->
                update(draft.copy(bonusCounts = draft.bonusCounts + (ZongceBonusCount.BONUS_PE_COMMEND to n)))
            },
        )
        HorizontalDivider()
        Text("处罚计次", style = MaterialTheme.typography.titleSmall)
        ZongcePenalty.TI_KEYS.forEach { key ->
            StepperRow(
                label = penaltyLabel(key),
                count = draft.penalties[key] ?: 0,
                onChange = { n -> update(draft.copy(penalties = draft.penalties + (key to n))) },
            )
        }
        CheckRow("代跑/代测（体育成绩记 0 分）", draft.tiVeto) { v -> update(draft.copy(tiVeto = v)) }
    }
}

@Composable
private fun MeiSection(draft: ZongceDraft, update: (ZongceDraft) -> Unit) {
    SectionCard(
        title = "美育（Z4）",
        subtitle = "基础分 70 + 自评激励 10 = 80 已默认计入，只需填写奖励分与处罚分；美育课程合格 +5 在「添加奖励」中录入",
    ) {
        AwardSection(ZongceModules.MEI, draft, update)
        HorizontalDivider()
        Text("实践计次", style = MaterialTheme.typography.titleSmall)
        listOf(
            ZongceBonusCount.BONUS_MEI_CAMPUS_AUDIENCE,
            ZongceBonusCount.BONUS_MEI_CAMPUS_JOIN,
            ZongceBonusCount.BONUS_MEI_OUT_AUDIENCE,
            ZongceBonusCount.BONUS_MEI_OUT_CITY,
            ZongceBonusCount.BONUS_MEI_OUT_PROVINCE,
            ZongceBonusCount.BONUS_MEI_OUT_NATIONAL,
        ).forEach { key ->
            StepperRow(
                label = bonusLabel(key),
                count = draft.bonusCounts[key] ?: 0,
                onChange = { n -> update(draft.copy(bonusCounts = draft.bonusCounts + (key to n))) },
            )
        }
        HorizontalDivider()
        Text("处罚计次", style = MaterialTheme.typography.titleSmall)
        ZongcePenalty.MEI_KEYS.forEach { key ->
            StepperRow(
                label = penaltyLabel(key),
                count = draft.penalties[key] ?: 0,
                onChange = { n -> update(draft.copy(penalties = draft.penalties + (key to n))) },
            )
        }
        CheckRow("抄袭/违规（美育成绩记 0 分）", draft.meiVeto) { v -> update(draft.copy(meiVeto = v)) }
    }
}

// ---------- 奖励记录区（可复用） ----------

@Composable
private fun AwardSection(module: String, draft: ZongceDraft, update: (ZongceDraft) -> Unit) {
    var showAdd by remember { mutableStateOf(false) }
    val awards = draft.awards.filter { it.module == module }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(
            "奖励记录（${awards.size}）",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = { showAdd = true }) {
            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text("添加奖励")
        }
    }
    if (awards.isEmpty()) {
        Text(
            "暂无奖励记录",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        awards.forEach { r ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text(awardTitle(r), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        awardValue(r),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (r.value == ZongceTables.VOTE_EXCELLENT) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (r.value == ZongceTables.VOTE_EXCELLENT) FontWeight.SemiBold else null,
                    )
                }
                IconButton(onClick = {
                    update(draft.copy(awards = draft.awards.filterNot { it.id == r.id }.toMutableList()))
                }) {
                    Icon(
                        Icons.Rounded.DeleteOutline,
                        contentDescription = "删除",
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
    if (showAdd) {
        AddAwardDialog(
            module = module,
            onDismiss = { showAdd = false },
            onAdd = { r ->
                update(draft.copy(awards = (draft.awards + r).toMutableList()))
                showAdd = false
            },
        )
    }
}

private fun awardTitle(r: ZongceAwardRecord): String = buildString {
    append(CATEGORY_LABELS[r.category] ?: r.category)
    val parts = listOfNotNull(
        LEVEL_LABELS[r.level]?.takeIf { r.level.isNotEmpty() && r.level != ZongceTables.LEVEL_NONE },
        (GRADE_LABELS[r.grade] ?: r.grade).takeIf { r.grade.isNotEmpty() },
    )
    if (parts.isNotEmpty()) {
        append(" · ")
        append(parts.joinToString(" "))
    }
    if (r.sameProject) append("（同一项目）")
}

private fun awardValue(r: ZongceAwardRecord): String =
    if (r.value == ZongceTables.VOTE_EXCELLENT) "一票定优" else "+${formatNum(r.value)} 分"

// ---------- 添加奖励对话框 ----------

@Composable
private fun AddAwardDialog(module: String, onDismiss: () -> Unit, onAdd: (ZongceAwardRecord) -> Unit) {
    val cats = remember(module) { moduleCategories(module) }
    var catIdx by remember { mutableStateOf(0) }
    var levelIdx by remember { mutableStateOf(0) }
    var gradeIdx by remember { mutableStateOf(0) }
    var customValue by remember { mutableStateOf("") }
    var sameProject by remember { mutableStateOf(false) }

    val catKey = cats[catIdx.coerceIn(0, cats.lastIndex)].first
    val options = remember(catKey) { ZongceTables.optionsOf(catKey) }
    val hasTable = catKey != CUSTOM_CATEGORY && options.first.isNotEmpty() && options.second.isNotEmpty()
    val levels = if (hasTable) options.first else emptyList()
    val grades = if (hasTable) options.second else emptyList()

    // 切换类目时重置级别/等级；固定分值类目预填
    LaunchedEffect(catIdx) {
        levelIdx = 0
        gradeIdx = 0
        customValue = when (catKey) {
            ZongceTables.DE_CADRE -> "4"
            ZongceTables.MEI_COURSE -> "5"
            else -> ""
        }
    }

    val lookupValue = if (hasTable) {
        val li = levelIdx.coerceIn(0, levels.lastIndex)
        val gi = gradeIdx.coerceIn(0, grades.lastIndex)
        ZongceTables.lookup(catKey, levels[li], grades[gi])
    } else {
        ZongceTables.NOT_FOUND
    }
    val manual = !hasTable || lookupValue == ZongceTables.NOT_FOUND
    val parsed = customValue.toDoubleOrNull() ?: 0.0
    val canConfirm = if (manual) parsed > 0.0 else true

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加奖励记录") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DropdownField(
                    label = "类目",
                    text = cats[catIdx.coerceIn(0, cats.lastIndex)].second,
                    options = cats.map { it.second },
                    onSelect = { catIdx = it },
                )
                if (hasTable) {
                    DropdownField(
                        label = "级别",
                        text = LEVEL_LABELS[levels[levelIdx.coerceIn(0, levels.lastIndex)]].orEmpty(),
                        options = levels.map { LEVEL_LABELS[it] ?: it },
                        onSelect = { levelIdx = it },
                    )
                    DropdownField(
                        label = "等级",
                        text = (GRADE_LABELS[grades[gradeIdx.coerceIn(0, grades.lastIndex)]] ?: grades[gradeIdx.coerceIn(0, grades.lastIndex)]),
                        options = grades.map { GRADE_LABELS[it] ?: it },
                        onSelect = { gradeIdx = it },
                    )
                    when {
                        lookupValue == ZongceTables.VOTE_EXCELLENT -> Text(
                            "查表分值：一票定优（综测等次直接 A）",
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                        )
                        lookupValue == ZongceTables.NOT_FOUND -> Text(
                            "该级别/等级组合查无分值，请直接填写分值",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                        else -> Text(
                            "查表分值：+${formatNum(lookupValue)} 分",
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
                if (manual) {
                    FilteredValueField(
                        label = "奖励分值",
                        value = customValue,
                        onChange = { customValue = it },
                    )
                }
                CheckRow("同一项目（同项目多条就高取分）", sameProject) { sameProject = it }
            }
        },
        confirmButton = {
            TextButton(
                enabled = canConfirm,
                onClick = {
                    onAdd(
                        ZongceAwardRecord(
                            id = System.currentTimeMillis(),
                            module = module,
                            category = catKey,
                            level = if (hasTable) levels[levelIdx.coerceIn(0, levels.lastIndex)] else "",
                            grade = if (hasTable) grades[gradeIdx.coerceIn(0, grades.lastIndex)] else "",
                            value = if (manual) parsed else lookupValue,
                            sameProject = sameProject,
                        )
                    )
                },
            ) { Text("确定") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

// ---------- 通用小组件 ----------

@Composable
private fun SectionCard(
    title: String,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            content()
        }
    }
}

/** 数字输入：过滤非数字与小数点，解析失败按 0；外部值变化时回显 */
@Composable
private fun NumField(
    label: String,
    value: Double,
    onValue: (Double) -> Unit,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
) {
    var text by remember { mutableStateOf(formatNum(value)) }
    var lastEmitted by remember { mutableStateOf(value) }
    LaunchedEffect(value) {
        if (value != lastEmitted) {
            lastEmitted = value
            text = formatNum(value)
        }
    }
    OutlinedTextField(
        value = text,
        onValueChange = { raw ->
            var s = raw.filter { it.isDigit() || it == '.' }
            val dot = s.indexOf('.')
            if (dot >= 0) s = s.take(dot + 1) + s.drop(dot + 1).replace(".", "")
            text = s
            val v = s.toDoubleOrNull() ?: 0.0
            lastEmitted = v
            onValue(v)
        },
        label = { Text(label) },
        supportingText = if (supportingText != null) {
            { Text(supportingText) }
        } else null,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        modifier = modifier.fillMaxWidth(),
    )
}

/** 字符串版数字输入（对话框内自持状态用） */
@Composable
private fun FilteredValueField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { raw ->
            var s = raw.filter { it.isDigit() || it == '.' }
            val dot = s.indexOf('.')
            if (dot >= 0) s = s.take(dot + 1) + s.drop(dot + 1).replace(".", "")
            onChange(s)
        },
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** 只读下拉选择：OutlinedTextField 外观 + DropdownMenu 选项 */
@Composable
private fun DropdownField(
    label: String,
    text: String,
    options: List<String>,
    enabled: Boolean = true,
    onSelect: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = text,
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text(label) },
            trailingIcon = { Icon(Icons.Rounded.ArrowDropDown, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Box(
            Modifier
                .matchParentSize()
                .clickable(enabled = enabled) { expanded = true },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEachIndexed { i, opt ->
                DropdownMenuItem(
                    text = { Text(opt) },
                    onClick = {
                        expanded = false
                        onSelect(i)
                    },
                )
            }
        }
    }
}

@Composable
private fun StepperRow(label: String, count: Int, max: Int = Int.MAX_VALUE, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        IconButton(onClick = { onChange(count - 1) }, enabled = count > 0) {
            Icon(Icons.Rounded.Remove, contentDescription = "减少")
        }
        Text(
            "$count",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(min = 30.dp),
        )
        IconButton(onClick = { onChange(count + 1) }, enabled = count < max) {
            Icon(Icons.Rounded.Add, contentDescription = "增加")
        }
    }
}

@Composable
private fun CheckRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(vertical = 2.dp),
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun ChipRow(
    label: String,
    options: List<Pair<Int, String>>,
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    Column {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { (v, text) ->
                FilterChip(
                    selected = selected == v,
                    onClick = { onSelect(v) },
                    label = { Text(text) },
                )
            }
        }
    }
}
