package cn.edu.xyc.campus.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cn.edu.xyc.campus.data.grades.AvgScoreCalculator
import cn.edu.xyc.campus.data.remote.TermUtils

/** 去掉末尾多余的 .0（与 AvgScoreCalculator 的展示规则一致） */
private fun trimZero(v: Double): String {
    val s = "%.2f".format(v)
    return if (s.endsWith(".00")) s.dropLast(3) else if (s.endsWith("0")) s.dropLast(1) else s
}

/**
 * 平均学分绩「选择科目」弹窗：
 * 打开时拷贝一份排除集做临时勾选，底部实时预览计算结果；
 * 「完成」才把临时集回传给调用方（由调用方写 Prefs 并更新卡片），「取消」直接丢弃。
 */
@Composable
internal fun CourseSelectDialog(
    subjects: List<AvgScoreCalculator.Subject>,
    initiallyExcluded: Set<String>,
    onConfirm: (Set<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    // 拆帧：先弹对话框窗口，列表下一帧再填充，避免打开瞬间一大帧卡顿
    var listReady by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        listReady = true
    }
    // 临时排除集：点条目只改这里，确认才回传
    var tempExcluded by remember { mutableStateOf(initiallyExcluded) }
    val preview = AvgScoreCalculator.compute(subjects, tempExcluded)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择参与计算的科目") },
        text = {
            if (!listReady) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(Modifier.size(26.dp))
                }
            } else {
                Column(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "取消勾选的科目不计入平均学分绩",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { tempExcluded = emptySet() }) { Text("全选") }
                    }
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(max = 360.dp)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        subjects.forEach { sub ->
                            val checked = sub.key !in tempExcluded
                            val innovation =
                                sub.nature.contains("创新创业") || sub.category.contains("创新创业")
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        tempExcluded = if (checked) {
                                            tempExcluded + sub.key
                                        } else {
                                            tempExcluded - sub.key
                                        }
                                    }
                                    .padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(
                                    checked = checked,
                                    onCheckedChange = {
                                        tempExcluded = if (checked) {
                                            tempExcluded + sub.key
                                        } else {
                                            tempExcluded - sub.key
                                        }
                                    },
                                )
                                Column(Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            sub.courseName,
                                            style = MaterialTheme.typography.bodyMedium,
                                        )
                                        if (innovation) {
                                            Spacer(Modifier.width(6.dp))
                                            Box(
                                                Modifier
                                                    .background(
                                                        MaterialTheme.colorScheme.secondaryContainer,
                                                        RoundedCornerShape(6.dp),
                                                    )
                                                    .padding(horizontal = 6.dp, vertical = 1.dp),
                                            ) {
                                                Text(
                                                    "创新创业",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                )
                                            }
                                        }
                                    }
                                    Text(
                                        // GradeItem.termNo 存的是响应 xqm 编码（3/12/16），显示前转为学期序号
                                        "第${TermUtils.xqmToTermNo(sub.termNo)}学期 · ${trimZero(sub.credit)}学分",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                        if (subjects.isEmpty()) {
                            Text(
                                "本学年暂无成绩记录",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 16.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(Modifier.height(6.dp))
                    // 实时预览：随勾选即时重算
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "预计平均学分绩",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                preview.avgScore?.let { "%.2f".format(it) } ?: "—",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Text(
                            "参与 ${preview.count} 门科目",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(tempExcluded) }) { Text("完成") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
