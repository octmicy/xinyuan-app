package cn.edu.xyc.campus.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.unit.dp
import cn.edu.xyc.campus.data.remote.UpdateChecker

/**
 * 发现新版本弹窗：标题带版本号，正文为发布说明。
 * 点「更新」直接用检测更新时验证可用的镜像源下载（检测通了下载就通），官方直连仅作兜底说明。
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
    proxyPrefix: String? = null,
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
                Spacer(Modifier.padding(top = 6.dp))
                Text(
                    "将通过${if (proxyPrefix != null) "镜像源" else "官方直连"}下载安装包",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                // 复用检测更新时验证可用的镜像前缀，保证下载链路与检测一致
                val finalUrl = (proxyPrefix ?: "") + downloadUrl
                openInBrowser(context, finalUrl)
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

/**
 * 用浏览器打开下载链接。
 *
 * 关键在 `CATEGORY_BROWSABLE`：它把候选限定为「浏览器类应用」，
 * 避免链接被系统自带下载器 / 网盘 / 应用商店等非浏览器组件接管（MIUI 上尤其明显）；
 * 系统若已设置默认浏览器则直接用它打开，未设置时由系统弹出选择器（用户可勾选"始终"）。
 * 极端情况（无任何浏览器可处理）回退普通 ACTION_VIEW，保证不会因 Intent 无法解析而静默失败。
 */
private fun openInBrowser(context: android.content.Context, url: String) {
    val uri = Uri.parse(url)
    val browserIntent = Intent(Intent.ACTION_VIEW, uri).apply {
        addCategory(Intent.CATEGORY_BROWSABLE)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    val fallback = Intent(Intent.ACTION_VIEW, uri).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(browserIntent) }
        .onFailure { runCatching { context.startActivity(fallback) } }
}
