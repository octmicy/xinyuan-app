package cn.edu.xyc.campus.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

private val DAY_LABELS = listOf("一", "二", "三", "四", "五", "六", "日")

private val TIME_REGEX = Regex("^\\d{1,2}:\\d{2}$")

/** 输入自动格式化：只打数字（如 1830 / 1630）自动补冒号成 18:30 / 16:30 */
private fun fmtTime(raw: String): String {
    val d = raw.filter { it.isDigit() }.take(4)
    return when {
        d.isEmpty() -> ""
        d.length <= 2 -> d
        else -> d.take(d.length - 2) + ":" + d.takeLast(2)
    }
}

/**
 * 添加自定义课程：名称必填，时间二选一——
 * 按节次（起止节次，作息表推算时间）或按具体时间（如 18:30-20:00，网格按重叠节次折算）。
 * 可选单双周。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CustomCourseDialog(
    onDismiss: () -> Unit,
    onSave: (
        name: String, teacher: String, room: String,
        day: Int, parity: Int,
        startSec: Int, endSec: Int,
        startTime: String, endTime: String,
    ) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var teacher by rememberSaveable { mutableStateOf("") }
    var room by rememberSaveable { mutableStateOf("") }
    var day by rememberSaveable { mutableIntStateOf(0) } // 0=未选，1-7
    var parity by rememberSaveable { mutableIntStateOf(0) }
    var timeMode by rememberSaveable { mutableIntStateOf(0) } // 0=按节次 1=按具体时间
    var startSec by rememberSaveable { mutableIntStateOf(1) }
    var endSec by rememberSaveable { mutableIntStateOf(2) }
    var startTime by rememberSaveable { mutableStateOf("") }
    var endTime by rememberSaveable { mutableStateOf("") }
    var startExpanded by remember { mutableStateOf(false) }
    var endExpanded by remember { mutableStateOf(false) }
    var invalid by remember { mutableStateOf(false) }

    fun timeOk(t: String) = TIME_REGEX.matches(t) &&
        t.split(":").let { (it[0].toIntOrNull() ?: 99) < 24 && (it[1].toIntOrNull() ?: 99) < 60 }

    fun submit() {
        val timeInvalid = timeMode == 1 &&
            (!timeOk(startTime) || !timeOk(endTime) || startTime >= endTime)
        if (name.isBlank() || day == 0 || timeInvalid) {
            invalid = true
            return
        }
        onSave(
            name.trim(), teacher.trim(), room.trim(), day, parity,
            startSec, endSec,
            if (timeMode == 1) startTime.trim() else "",
            if (timeMode == 1) endTime.trim() else "",
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加自定义课程") },
        text = {
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .fillMaxWidth(),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; invalid = false },
                    label = { Text("课程名称 *") },
                    singleLine = true,
                    isError = invalid && name.isBlank(),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (invalid && name.isBlank()) {
                    Text(
                        "请填写课程名称",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = teacher,
                    onValueChange = { teacher = it },
                    label = { Text("老师（选填）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = room,
                    onValueChange = { room = it },
                    label = { Text("地点（选填）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(10.dp))
                Text("星期", style = MaterialTheme.typography.labelMedium)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    DAY_LABELS.forEachIndexed { i, label ->
                        FilterChip(
                            selected = day == i + 1,
                            onClick = { day = i + 1 },
                            label = { Text(label) },
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = timeMode == 0,
                        onClick = { timeMode = 0; invalid = false },
                        label = { Text("按节次") },
                    )
                    FilterChip(
                        selected = timeMode == 1,
                        onClick = { timeMode = 1; invalid = false },
                        label = { Text("按具体时间") },
                    )
                }

                if (timeMode == 0) {
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ExposedDropdownMenuBox(
                            expanded = startExpanded,
                            onExpandedChange = { startExpanded = it },
                            modifier = Modifier.weight(1f),
                        ) {
                            OutlinedTextField(
                                value = "第${startSec}节",
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("开始节次") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(startExpanded) },
                                modifier = Modifier
                                    .menuAnchor()
                                    .fillMaxWidth(),
                            )
                            ExposedDropdownMenu(
                                expanded = startExpanded,
                                onDismissRequest = { startExpanded = false },
                            ) {
                                (1..12).forEach { i ->
                                    DropdownMenuItem(
                                        text = { Text("第${i}节") },
                                        onClick = {
                                            startSec = i
                                            if (endSec < i) endSec = i
                                            startExpanded = false
                                        },
                                    )
                                }
                            }
                        }
                        ExposedDropdownMenuBox(
                            expanded = endExpanded,
                            onExpandedChange = { endExpanded = it },
                            modifier = Modifier.weight(1f),
                        ) {
                            OutlinedTextField(
                                value = "第${endSec}节",
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("结束节次") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(endExpanded) },
                                modifier = Modifier
                                    .menuAnchor()
                                    .fillMaxWidth(),
                            )
                            ExposedDropdownMenu(
                                expanded = endExpanded,
                                onDismissRequest = { endExpanded = false },
                            ) {
                                (startSec..12).forEach { i ->
                                    DropdownMenuItem(
                                        text = { Text("第${i}节") },
                                        onClick = {
                                            endSec = i
                                            endExpanded = false
                                        },
                                    )
                                }
                            }
                        }
                    }
                } else {
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = startTime,
                            onValueChange = { startTime = fmtTime(it); invalid = false },
                            label = { Text("开始时间") },
                            placeholder = { Text("1830") },
                            singleLine = true,
                            isError = invalid && !timeOk(startTime),
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = endTime,
                            onValueChange = { endTime = fmtTime(it); invalid = false },
                            label = { Text("结束时间") },
                            placeholder = { Text("2000") },
                            singleLine = true,
                            isError = invalid && !timeOk(endTime),
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (invalid && timeMode == 1 && (!timeOk(startTime) || !timeOk(endTime) || startTime >= endTime)) {
                        Text(
                            "只需输入数字（如 1830 = 18:30），且开始早于结束",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))
                Text("重复", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(0 to "每周", 1 to "单周", 2 to "双周").forEach { (p, label) ->
                        FilterChip(
                            selected = parity == p,
                            onClick = { parity = p },
                            label = { Text(label) },
                        )
                    }
                }
                if (invalid && day == 0) {
                    Text(
                        "请选择星期",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = ::submit) { Text("添加") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
