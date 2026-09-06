package cn.edu.xyc.campus.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import cn.edu.xyc.campus.data.local.ThemeStore

/** 主题外观：导入 zip 主题包 / 恢复默认。格式说明见 README「主题格式」。 */
@Composable
internal fun ThemeScreenDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val theme by ThemeStore.active
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            ThemeStore.importZip(context, uri)
                .onSuccess { name -> Toast.makeText(context, "主题已应用：$name", Toast.LENGTH_LONG).show() }
                .onFailure { Toast.makeText(context, "导入失败：${it.message}", Toast.LENGTH_LONG).show() }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.7f)
                .background(MaterialTheme.colorScheme.background, RoundedCornerShape(20.dp))
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("主题外观", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, "关闭") }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "当前主题：${theme?.name ?: "默认（新院蓝）"}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
            )
            theme?.let {
                if (it.author.isNotEmpty()) {
                    Text(
                        "作者：${it.author}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "支持导入 zip 主题包：包内放一个 theme.json（描述配色与图标映射）和若干 png 图片，" +
                    "可替换底部导航图标、登录页形象、默认头像与全局配色。具体格式见项目 README「主题格式」。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            Button(onClick = { picker.launch("*/*") }, modifier = Modifier.fillMaxWidth()) {
                Text("导入主题包（zip）")
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { ThemeStore.resetDefault(context) },
                enabled = theme != null,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("恢复默认主题") }
        }
    }
}
