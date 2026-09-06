package cn.edu.xyc.campus.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.unit.dp
import cn.edu.xyc.campus.data.remote.UpdateChecker

/**
 * 发现新版本弹窗：标题带版本号，正文为发布说明。
 * onIgnored / onNeverRemind 传 null 时隐藏对应按钮（手动检查不需要）。
 */
@Composable
fun UpdateDialog(
    version: String,
    notes: String,
    downloadUrl: String,
    onDismiss: () -> Unit,
    onIgnored: (() -> Unit)? = null,
    onNeverRemind: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("发现新版本 v$version") },
        text = {
            Column(
                Modifier
                    .heightIn(max = 300.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    notes.ifEmpty { "更新详情见发布页" },
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(downloadUrl)))
                }
                onDismiss()
            }) { Text("更新") }
        },
        dismissButton = {
            if (onIgnored != null || onNeverRemind != null) {
                Row {
                    onIgnored?.let { TextButton(onClick = it) { Text("忽略此版本") } }
                    onNeverRemind?.let { TextButton(onClick = it) { Text("不再提醒") } }
                }
            } else {
                TextButton(onClick = onDismiss) { Text("关闭") }
            }
        },
    )
}
