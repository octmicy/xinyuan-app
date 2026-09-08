package cn.edu.xyc.campus.ui.screens

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.edu.xyc.campus.R
import cn.edu.xyc.campus.data.local.ScheduleCache
import cn.edu.xyc.campus.data.model.ThirdApp
import cn.edu.xyc.campus.data.remote.PortalApi
import cn.edu.xyc.campus.data.remote.SessionStore
import cn.edu.xyc.campus.ui.theme.isAppDarkTheme

/** 应用白名单：门户名称 → 展示名 / 本地图标 /（可选）SPA 落地路由 */
private data class AppEntry(
    val portalName: String,   // 与门户 /app/getApplication 返回的 name 匹配
    val label: String = portalName,
    val iconRes: Int,
    val finalHash: String? = null, // 应用内打开后 SPA 跳转的目标路由
    val native: Boolean = false,   // 原生页面入口，不走门户/WebView
    val comingSoon: Boolean = false, // 敬请期待占位：点击仅提示制作中
)

private val ALLOWED = listOf(
    AppEntry("综测计算", "综测计算", R.drawable.app_zongce, native = true),
    AppEntry("今天吃什么", "今天吃什么", R.drawable.app_food, comingSoon = true),
    AppEntry("教务系统", iconRes = R.drawable.app_jwxt),
    AppEntry("我的图书馆", "图书馆电子证", R.drawable.app_library, finalHash = "/credential?from=Home"),
    AppEntry("就业系统", iconRes = R.drawable.app_career),
    AppEntry("毕业生离校系统", iconRes = R.drawable.app_graduate),
    AppEntry("学工系统", iconRes = R.drawable.app_xg),
    AppEntry("学生缴费", iconRes = R.drawable.app_pay),
    AppEntry("网络教学系统", iconRes = R.drawable.app_online),
)

private data class OpenTarget(val name: String, val url: String, val finalHash: String?)

@Composable
fun AppsScreen() {
    val context = LocalContext.current
    var loading by rememberSaveable { mutableStateOf(true) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var reloadKey by rememberSaveable { mutableStateOf(0) }
    var openTarget by remember { mutableStateOf<OpenTarget?>(null) }
    var showZongce by remember { mutableStateOf(false) }

    LaunchedEffect(reloadKey) {
        ScheduleCache.applications["APPS"]?.let {
            loading = false
            return@LaunchedEffect
        }
        if (!ScheduleCache.tryMark("APPS")) return@LaunchedEffect
        loading = true
        error = null
        PortalApi.getApplications()
            .onSuccess { ScheduleCache.applications["APPS"] = it }
            .onFailure { error = "加载失败: ${it.message}" }
        ScheduleCache.unmark("APPS")
        loading = false
    }

    val all = ScheduleCache.applications["APPS"].orEmpty()
    // 白名单过滤 + 同名去重（教务系统两个入口优先 xyoauthlogin）+ 关联本地图标
    // 综测为原生入口，不依赖门户数据，始终显示且排在最前
    val apps = remember(all) {
        ALLOWED.mapNotNull { entry ->
            if (entry.native || entry.comingSoon) return@mapNotNull entry to null
            all.filter { it.name == entry.portalName && it.href.isNotBlank() }.let { candidates ->
                candidates.firstOrNull { it.href.contains("xyoauthlogin") }
                    ?: candidates.firstOrNull()
            }?.let { app -> entry to app }
        }
    }

    Column(Modifier.fillMaxSize()) {
        Text(
            "校园应用",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        )
        Text(
            "点击应用在应用内打开",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            else -> {
                // 门户加载失败时仅提示，不影响原生入口与已匹配应用展示
                if (error != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                    ) {
                        Text(
                            error!!,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { reloadKey++ }) { Text("重试") }
                    }
                }
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    items(apps, key = { it.first.portalName }) { (entry, app) ->
                        AppCell(
                            label = entry.label,
                            iconRes = entry.iconRes,
                            onClick = {
                                when {
                                    entry.comingSoon -> {
                                        Toast.makeText(
                                            context,
                                            "该功能还在制作中，敬请期待 🍚",
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                    }
                                    entry.native -> showZongce = true
                                    else -> app?.let {
                                        openTarget = OpenTarget(entry.label, ticketUrl(it), entry.finalHash)
                                    }
                                }
                            },
                        )
                    }
                }
            }
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

    if (showZongce) {
        ZongceScreen(onDismiss = { showZongce = false })
    }
}

/** 按门户 openThirdPage 语义构造跳转地址：hrefType!=5 的应用拼接 ticket 免密登录 */
private fun ticketUrl(app: ThirdApp): String {
    val sep = if (app.href.contains("?")) "&" else "?"
    return if (app.hrefType == 5) app.href
    else app.href + sep + "ticket=" + SessionStore.token.orEmpty()
}

@Composable
private fun AppCell(
    label: String,
    iconRes: Int,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(4.dp),
    ) {
        // 图标卡片托底：浅色白底 / 深色深灰黑底（贴纸四边透明，底色即观感背景）
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .padding(horizontal = 4.dp)
                .shadow(3.dp, RoundedCornerShape(20.dp))
                .clip(RoundedCornerShape(20.dp))
                .background(if (isAppDarkTheme()) Color(0xFF17181A) else Color.White)
                .padding(12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = androidx.compose.ui.res.painterResource(iconRes),
                contentDescription = label,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            label,
            fontSize = 12.sp,
            lineHeight = 14.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
