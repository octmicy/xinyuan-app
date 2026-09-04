package cn.edu.xyc.campus.ui.screens

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.OpenInBrowser
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import cn.edu.xyc.campus.data.remote.CampusHttp
import cn.edu.xyc.campus.data.remote.SessionStore

/** 学工系统移动版（SPA hash 路由，从 app.js 逆向） */
private const val XG_BASE = "http://ssxt.xyc.edu.cn/webApp/xuegong/index.html"
private const val XG_LOGIN_PREFIX = "http://ssxt.xyc.edu.cn/wiseduIndex.jsp?ticket="
private const val XG_HOME_MARK = "http://ssxt.xyc.edu.cn/webApp/xuegong/index.html"
private const val ROUTE_APPLY = "#/qingjia/qj_s_add" // 请假申请表单
private const val ROUTE_RECORD = "#/qingjia/qj_s_index" // 我的请假记录

/**
 * 请假申请：WebView 走完整学工登录链（wiseduIndex.jsp?ticket=门户token
 * → casLogin.jsp 种会话 → 落地学工首页），随后跳转请假表单。
 * 直接加载 SPA 页面是"伪登录"——学工的登录态由 casLogin 环节建立。
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LeaveScreen() {
    val context = LocalContext.current
    var mode by rememberSaveable { mutableStateOf("apply") }
    var loginDone by remember { mutableStateOf(false) }
    var loginFailed by remember { mutableStateOf(false) }

    val webView = remember {
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.userAgentString = CampusHttp.MOBILE_UA
        }
    }

    fun currentUrl(): String = XG_BASE + (if (mode == "apply") ROUTE_APPLY else ROUTE_RECORD)

    // 登录链落地学工首页后，跳到目标请假路由
    webView.webViewClient = object : WebViewClient() {
        override fun onPageFinished(view: WebView, url: String?) {
            url ?: return
            if (!loginDone && url.startsWith(XG_HOME_MARK)) {
                loginDone = true
                val route = if (mode == "apply") ROUTE_APPLY else ROUTE_RECORD
                view.evaluateJavascript(
                    "window.location.hash = '${route.removePrefix("#")}';",
                    null,
                )
            }
            // 学工弹出自身登录页 = 门户会话失效
            if (loginDone && url.contains("login", ignoreCase = true)) {
                loginFailed = true
            }
        }
    }

    // 进入本页：先同步门户 Cookie，再走完整登录链（casLogin 会给 ssxt 种会话）
    LaunchedEffect(Unit) {
        CampusHttp.syncToWebView()
        val ticket = SessionStore.token
        if (ticket.isNullOrEmpty()) {
            loginFailed = true
        } else {
            webView.loadUrl(XG_LOGIN_PREFIX + ticket)
        }
    }

    // 切换模式：登录完成后在 SPA 内改 hash，不整页刷新
    LaunchedEffect(mode) {
        if (!loginDone) return@LaunchedEffect
        val route = if (mode == "apply") ROUTE_APPLY else ROUTE_RECORD
        webView.evaluateJavascript(
            "if (location.hash != '$route') window.location.hash = '${route.removePrefix("#")}';",
            null,
        )
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
        ) {
            Text("请假申请", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.weight(1f))
            IconButton(
                onClick = {
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(currentUrl())))
                    }
                },
            ) { Icon(Icons.Rounded.OpenInBrowser, "浏览器打开") }
        }
        Row(
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        ) {
            FilterChip(
                selected = mode == "apply",
                onClick = { mode = "apply" },
                label = { Text("请假申请") },
            )
            FilterChip(
                selected = mode == "record",
                onClick = { mode = "record" },
                label = { Text("请假记录") },
            )
        }
        Text(
            if (mode == "apply") "填写请假事由与时间后提交，销假也在本页操作"
            else "查看已提交的请假单与审批状态",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        Box(Modifier.weight(1f)) {
            if (loginFailed) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                ) {
                    Text(
                        "学工系统登录失败，请退出 App 重新登录门户后重试",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            } else {
                AndroidView(factory = { webView }, modifier = Modifier.fillMaxSize())
            }
        }
    }
}
