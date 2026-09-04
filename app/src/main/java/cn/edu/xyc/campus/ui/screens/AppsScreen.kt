package cn.edu.xyc.campus.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

/** 应用白名单：名称 → 本地图标资源（按展示顺序） */
private val ALLOWED = listOf(
    "教务系统" to R.drawable.app_jwxt,
    "我的图书馆" to R.drawable.app_library,
    "就业系统" to R.drawable.app_career,
    "毕业生离校系统" to R.drawable.app_graduate,
    "学工系统" to R.drawable.app_xg,
    "学生缴费" to R.drawable.app_pay,
    "网络教学系统" to R.drawable.app_online,
)

@Composable
fun AppsScreen() {
    val context = LocalContext.current
    var loading by rememberSaveable { mutableStateOf(true) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var reloadKey by rememberSaveable { mutableStateOf(0) }

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
    val apps = remember(all) {
        ALLOWED.mapNotNull { (name, iconRes) ->
            all.filter { it.name == name && it.href.isNotBlank() }.let { candidates ->
                candidates.firstOrNull { it.href.contains("xyoauthlogin") }
                    ?: candidates.firstOrNull()
            }?.let { app -> (name to iconRes) to app }
        }.map { (iconPair, app) -> app to iconPair.second }
    }

    Column(Modifier.fillMaxSize()) {
        Text(
            "校园应用",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        )
        Text(
            "点击应用将携带登录票据在浏览器打开",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            error != null -> ErrorPane(error!!, onRetry = { reloadKey++ })
            apps.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("暂无应用", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(apps, key = { it.first.name }) { (app, iconRes) ->
                    AppCell(app = app, iconRes = iconRes, onClick = { openWithTicket(context, app) })
                }
            }
        }
    }
}

/** 按门户 openThirdPage 语义：hrefType!=5 的应用拼接 ticket 免密打开 */
private fun openWithTicket(context: android.content.Context, app: ThirdApp) {
    val sep = if (app.href.contains("?")) "&" else "?"
    val url = if (app.hrefType == 5) app.href
    else app.href + sep + "ticket=" + SessionStore.token.orEmpty()
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
}

@Composable
private fun AppCell(app: ThirdApp, iconRes: Int, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(4.dp),
    ) {
        Image(
            painter = androidx.compose.ui.res.painterResource(iconRes),
            contentDescription = app.name,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .padding(horizontal = 4.dp),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            app.name,
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
