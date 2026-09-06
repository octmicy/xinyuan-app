package cn.edu.xyc.campus.ui.screens

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.edu.xyc.campus.R
import cn.edu.xyc.campus.data.local.AvatarStore
import cn.edu.xyc.campus.data.local.ScheduleCache
import cn.edu.xyc.campus.data.model.ProfileCard
import cn.edu.xyc.campus.data.remote.JwxtApi
import cn.edu.xyc.campus.data.remote.JwxtResult
import cn.edu.xyc.campus.data.remote.PortalApi
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun ProfileScreen(onLogout: () -> Unit) {
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var loading by rememberSaveable { mutableStateOf(true) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var profile by remember { mutableStateOf<ProfileCard?>(null) }
    var reloadKey by rememberSaveable { mutableStateOf(0) }
    var showDonate by rememberSaveable { mutableStateOf(false) }
    var showFeedback by rememberSaveable { mutableStateOf(false) }
    var showTheme by rememberSaveable { mutableStateOf(false) }
    var avatarVersion by rememberSaveable { mutableStateOf(0) }
    val versionName = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: ""
    }
    var checkingUpdate by rememberSaveable { mutableStateOf(false) }
    var manualUpdate by remember { mutableStateOf<cn.edu.xyc.campus.data.remote.UpdateChecker.UpdateInfo?>(null) }
    val checkUpdate: () -> Unit = {
        if (!checkingUpdate) {
            checkingUpdate = true
            scope.launch {
                val info = cn.edu.xyc.campus.data.remote.UpdateChecker.checkLatest()
                checkingUpdate = false
                when {
                    info == null -> Toast.makeText(context, "检查失败，请稍后再试", Toast.LENGTH_SHORT).show()
                    !cn.edu.xyc.campus.data.remote.UpdateChecker.isNewer(info.version, versionName) ->
                        Toast.makeText(context, "已是最新版本 v$versionName", Toast.LENGTH_SHORT).show()
                    else -> manualUpdate = info
                }
            }
        }
    }

    // 系统相册选图（Photo Picker，旧系统自动回退到系统文件选择器）
    val pickAvatar = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                AvatarStore.save(context, uri)
                avatarVersion++
            }
        }
    }
    val onPickAvatar: () -> Unit = {
        pickAvatar.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
        )
    }

    LaunchedEffect(reloadKey) {
        // 命中预取缓存零等待
        ScheduleCache.profileData["PROFILE"]?.let {
            profile = it
            loading = false
            return@LaunchedEffect
        }
        if (!ScheduleCache.tryMark("PROFILE")) return@LaunchedEffect
        loading = true
        error = null
        when (val r = JwxtApi.getProfile()) {
            is JwxtResult.Ok -> {
                ScheduleCache.profileData["PROFILE"] = r.data
                profile = r.data
            }
            is JwxtResult.SessionExpired -> error = r.message
            is JwxtResult.Failed -> error = r.message
        }
        ScheduleCache.unmark("PROFILE")
        loading = false
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "我的",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 16.dp),
        )

        when {
            loading -> Box(Modifier.height(240.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            error != null -> ErrorPane(error!!, onRetry = { reloadKey++ })
            else -> {
                val card = profile
                if (card == null) {
                    ErrorPane("数据为空", onRetry = { reloadKey++ })
                } else {
                    val info = card.info
                    Avatar(size = 84.dp, version = avatarVersion, onClick = onPickAvatar)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "点击头像可更换",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(info.name.ifEmpty { "未知" }, style = MaterialTheme.typography.titleLarge)
                    Text(
                        "学号 ${info.studentId}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(20.dp))

                    Card(
                        Modifier.fillMaxSize(),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            InfoRow("班级", info.className)
                            InfoRow("专业", info.major)
                            InfoRow("年级", info.gradeYear)
                            if (card.college.isNotEmpty()) InfoRow("学院", card.college)
                        }
                    }

                    // ---- 主题外观 ----
                    Spacer(Modifier.height(20.dp))
                    Row(
                        Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { showTheme = true }
                            .padding(horizontal = 4.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Rounded.Palette,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Text("主题外观", style = MaterialTheme.typography.bodyMedium)
                    }

                    // ---- 项目地址 + 赞助 ----
                    Spacer(Modifier.height(8.dp))
                    Card(
                        Modifier.fillMaxSize(),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                            Text(
                                "支持项目",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(4.dp))
                            Row(
                                Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable {
                                        runCatching {
                                            context.startActivity(
                                                Intent(
                                                    Intent.ACTION_VIEW,
                                                    Uri.parse("https://github.com/octmicy/xinyuan-app"),
                                                ),
                                            )
                                        }
                                    }
                                    .padding(horizontal = 4.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    Icons.Rounded.Code,
                                    contentDescription = "GitHub",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp),
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    "GitHub：octmicy/xinyuan-app",
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Spacer(Modifier.height(2.dp))
                            Row {
                                Button(
                                    onClick = { showDonate = true },
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                        horizontal = 18.dp,
                                        vertical = 8.dp,
                                    ),
                                ) {
                                    Icon(
                                        Icons.Rounded.Favorite,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text("赞助开发者", style = MaterialTheme.typography.labelLarge)
                                }
                                Spacer(Modifier.width(10.dp))
                                OutlinedButton(
                                    onClick = { showFeedback = !showFeedback },
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                        horizontal = 16.dp,
                                        vertical = 8.dp,
                                    ),
                                ) {
                                    Text("问题反馈", style = MaterialTheme.typography.labelLarge)
                                }
                            }

                            // ---- 问题反馈（点开才展开）----
                            if (showFeedback) {
                                Spacer(Modifier.height(12.dp))
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .height(1.dp)
                                        .background(MaterialTheme.colorScheme.outlineVariant),
                                )
                                Spacer(Modifier.height(10.dp))
                                Text(
                                    "遇到问题或有建议？可以到仓库提 Issue，或发邮件给开发者（2335260621@qq.com）。不知道怎么写？点「复制模板」照着填就行。",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(Modifier.height(8.dp))
                                Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(
                                        onClick = {
                                            runCatching {
                                                context.startActivity(
                                                    Intent(
                                                        Intent.ACTION_VIEW,
                                                        Uri.parse("https://github.com/octmicy/xinyuan-app/issues"),
                                                    ),
                                                )
                                            }
                                        },
                                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    ) { Text("提 Issue", style = MaterialTheme.typography.labelMedium) }
                                    OutlinedButton(
                                        onClick = {
                                            val intent = Intent(Intent.ACTION_SENDTO).apply {
                                                data = Uri.parse("mailto:2335260621@qq.com")
                                                putExtra(Intent.EXTRA_SUBJECT, "【新院助手反馈】")
                                                putExtra(Intent.EXTRA_TEXT, feedbackTemplate(context))
                                            }
                                            runCatching {
                                                context.startActivity(Intent.createChooser(intent, "发送邮件"))
                                            }
                                        },
                                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    ) { Text("发邮件", style = MaterialTheme.typography.labelMedium) }
                                    val clipboard = LocalClipboardManager.current
                                    OutlinedButton(
                                        onClick = {
                                            clipboard.setText(AnnotatedString(feedbackTemplate(context)))
                                            android.widget.Toast.makeText(context, "模板已复制，粘贴到 Issue 或邮件里照着填即可", android.widget.Toast.LENGTH_LONG).show()
                                        },
                                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    ) { Text("复制模板", style = MaterialTheme.typography.labelMedium) }
                                }
                            }
                        }
                    }

                    // ---- 退出登录 / 检查更新（页面最底部）----
                    Spacer(Modifier.height(28.dp))
                    Row {
                        OutlinedButton(
                            onClick = {
                                PortalApi.clearSession()
                                JwxtApi.resetSso()
                                onLogout()
                            },
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error,
                            ),
                        ) { Text("退出登录") }
                        Spacer(Modifier.width(12.dp))
                        OutlinedButton(onClick = checkUpdate, enabled = !checkingUpdate) {
                            if (checkingUpdate) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    strokeWidth = 2.dp,
                                )
                                Spacer(Modifier.width(6.dp))
                            }
                            Text(if (checkingUpdate) "检查中…" else "检查更新")
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                    Text(
                        "学籍数据来自学校教务系统，仅在本机展示",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Spacer(Modifier.height(20.dp))
                    Text(
                        "新院助手 v$versionName",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }

    if (showTheme) {
        ThemeScreenDialog(onDismiss = { showTheme = false })
    }

    manualUpdate?.let { info ->
        cn.edu.xyc.campus.ui.components.UpdateDialog(
            version = info.version,
            notes = info.notes,
            downloadUrl = info.downloadUrl,
            proxyPrefix = info.proxyPrefix,
            onDismiss = { manualUpdate = null },
        )
    }

    if (showDonate) {
        DonateDialog(onDismiss = { showDonate = false })
    }
}

/** 反馈模板：新手也能照着填，App 版本自动带上 */
private fun feedbackTemplate(context: Context): String {
    val version = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull() ?: "未知"
    return """
        你好，我在使用「新院助手」时遇到问题/有一些建议：

        1. 问题描述：
        （发生了什么？比如：点开学期课表会闪退）

        2. 怎么操作的：
        （比如：打开课表 → 点右上角「学期课表」）

        3. 希望的效果：
        （比如：能正常打开）

        4. 手机型号 / 系统版本：
        （比如：红米 Note 12 / 澎湃OS）

        5. App 版本：v$version

        6. 截图（可选，方便的话附一张）
    """.trimIndent()
}

@Composable
private fun DonateDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("请作者喝杯奶茶 🧋") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Image(
                    painter = painterResource(R.drawable.donate_wechat),
                    contentDescription = "微信收款码",
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp)),
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "微信扫码赞助 · 金额随意，心意最重要",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}

/** 圆形头像：优先用户上传（filesDir/avatar.jpg），否则内置默认图。点击触发更换。 */
@Composable
private fun Avatar(size: androidx.compose.ui.unit.Dp, version: Int, onClick: () -> Unit) {
    val context = LocalContext.current
    val bitmap = remember(version) {
        AvatarStore.load(context)
    }
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "头像",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            cn.edu.xyc.campus.ui.components.ThemeImage(
                key = "avatar_default",
                resId = R.drawable.avatar_default,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        Modifier
            .fillMaxSize()
            .padding(vertical = 6.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            value.ifEmpty { "—" },
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
