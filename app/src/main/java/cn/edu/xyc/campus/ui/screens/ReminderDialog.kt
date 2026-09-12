package cn.edu.xyc.campus.ui.screens

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import cn.edu.xyc.campus.data.reminder.ClassReminderManager

/**
 * 课程提醒设置：开关 + 提前量（5/10/15/20/30 分钟）。
 * Android 13+ 引导通知权限；Android 12+ 引导精确闹钟权限（未授予时降级非精确，可能延迟数分钟）。
 */
@Composable
internal fun ReminderDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(ClassReminderManager.isEnabled(context)) }
    var advance by remember { mutableIntStateOf(ClassReminderManager.advanceMinutes(context)) }

    val notifPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            ClassReminderManager.setEnabled(context, true)
            enabled = true
        } else {
            Toast(context, "未授予通知权限，提醒将不可见")
        }
    }

    fun enable() {
        if (Build.VERSION.SDK_INT >= 33) {
            val granted = context.checkSelfPermission(
                android.Manifest.permission.POST_NOTIFICATIONS,
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notifPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }
        ClassReminderManager.setEnabled(context, true)
        enabled = true
        Toast(context, "课程提醒已开启（课前 $advance 分钟）")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("课程提醒") },
        text = {
            Column {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("上课前提醒我", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = enabled, onCheckedChange = { if (it) enable() else {
                        ClassReminderManager.setEnabled(context, false)
                        enabled = false
                    } })
                }
                if (enabled) {
                    Text(
                        "提前",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(5, 10, 15, 20, 30).forEach { m ->
                            FilterChip(
                                selected = advance == m,
                                onClick = {
                                    advance = m
                                    ClassReminderManager.setAdvanceMinutes(context, m)
                                },
                                label = { Text("${m}分钟") },
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    val am = context.getSystemService(AlarmManager::class.java)
                    val exactOk = Build.VERSION.SDK_INT < 31 || am?.canScheduleExactAlarms() == true
                    if (!exactOk) {
                        Text(
                            "未授予精确闹钟权限，提醒可能延迟几分钟。",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                        TextButton(onClick = {
                            runCatching {
                                context.startActivity(
                                    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM),
                                )
                            }
                        }) { Text("去授权精确闹钟") }
                    }
                    Text(
                        "仅提醒已同步到本机的课表课程（含自定义课程），打开课表页即可同步最新数据。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("完成") } },
    )
}

private fun Toast(context: Context, msg: String) {
    android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
}
