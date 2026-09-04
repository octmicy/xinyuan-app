package cn.edu.xyc.campus.ui.screens

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
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
    var avatarVersion by rememberSaveable { mutableStateOf(0) }

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

                    Card(Modifier.fillMaxSize()) {
                        Column(Modifier.padding(16.dp)) {
                            InfoRow("班级", info.className)
                            InfoRow("专业", info.major)
                            InfoRow("年级", info.gradeYear)
                            if (card.college.isNotEmpty()) InfoRow("学院", card.college)
                        }
                    }

                    Spacer(Modifier.height(24.dp))
                    OutlinedButton(onClick = {
                        PortalApi.clearSession()
                        JwxtApi.resetSso()
                        onLogout()
                    }) { Text("退出登录") }

                    Spacer(Modifier.height(16.dp))
                    Text(
                        "学籍数据来自学校教务系统，仅在本机展示",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    // ---- 项目地址 + 赞助 ----
                    Spacer(Modifier.height(28.dp))
                    Text(
                        "支持项目",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxSize(),
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(
                        Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                runCatching {
                                    context.startActivity(
                                        Intent(
                                            Intent.ACTION_VIEW,
                                            Uri.parse("https://github.com/octmicy/xinxue-app"),
                                        ),
                                    )
                                }
                            }
                            .padding(horizontal = 4.dp, vertical = 8.dp),
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
                            "GitHub：octmicy/xinxue-app",
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
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
                }
            }
        }
    }

    if (showDonate) {
        DonateDialog(onDismiss = { showDonate = false })
    }
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
            Image(
                painter = painterResource(R.drawable.avatar_default),
                contentDescription = "头像",
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
