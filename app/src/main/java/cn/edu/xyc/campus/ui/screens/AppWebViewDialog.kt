package cn.edu.xyc.campus.ui.screens

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.OpenInBrowser
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import cn.edu.xyc.campus.data.remote.CampusHttp

/**
 * 应用内打开第三方系统：走门户跳转登录链（Cookie 由 CampusHttp 同步），
 * 可选 finalHash：SPA 站点落地后自动跳到目标路由（如图书馆电子证 /credential?from=Home）。
 * 顶栏跟随 App 主题色，带域名副标题与更多菜单（浏览器打开）。
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun AppWebViewDialog(
    name: String,
    url: String,
    finalHash: String? = null,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var progress by remember { mutableIntStateOf(0) }
    var injected by remember { mutableStateOf(false) }
    var currentHost by remember { mutableStateOf("") }
    var menuOpen by remember { mutableStateOf(false) }

    // 顶栏用主题色容器，白字；WebView 背景对齐 App 底色避免闪白
    val barColor = MaterialTheme.colorScheme.primary
    val pageBg = MaterialTheme.colorScheme.background

    val webView = remember {
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.userAgentString = CampusHttp.MOBILE_UA
            setBackgroundColor(AndroidColor.TRANSPARENT)
        }
    }

    webView.webChromeClient = object : WebChromeClient() {
        override fun onProgressChanged(view: WebView?, newProgress: Int) {
            progress = newProgress
        }
    }
    webView.webViewClient = object : WebViewClient() {
        override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) {
            currentHost = runCatching { Uri.parse(url.orEmpty()).host }.getOrNull().orEmpty()
        }

        override fun onPageFinished(view: WebView, url: String?) {
            url ?: return
            // SPA 站点登录落地后跳到目标路由（只注入一次）
            if (finalHash != null && !injected && url.contains("mfindxyc.libsp.cn")) {
                injected = true
                view.evaluateJavascript(
                    "window.location.hash = '${finalHash.removePrefix("#")}';",
                    null,
                )
            }
        }
    }

    LaunchedEffect(Unit) {
        CampusHttp.syncToWebView()
        webView.loadUrl(url)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .background(pageBg),
        ) {
            // 主题色顶栏
            Column(Modifier.background(barColor)) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            "返回",
                            tint = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            name,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (currentHost.isNotEmpty()) {
                            Text(
                                currentHost,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.75f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(
                                Icons.Rounded.MoreVert,
                                "更多",
                                tint = MaterialTheme.colorScheme.onPrimary,
                            )
                        }
                        DropdownMenu(
                            expanded = menuOpen,
                            onDismissRequest = { menuOpen = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("用浏览器打开") },
                                leadingIcon = {
                                    Icon(Icons.Rounded.OpenInBrowser, contentDescription = null)
                                },
                                onClick = {
                                    menuOpen = false
                                    runCatching {
                                        context.startActivity(
                                            Intent(Intent.ACTION_VIEW, Uri.parse(webView.url ?: url)),
                                        )
                                    }
                                },
                            )
                        }
                    }
                }
                // 主题色进度条（贴顶栏下沿，完成后隐藏）
                if (progress < 100) {
                    LinearProgressIndicator(
                        progress = { progress / 100f },
                        modifier = Modifier.fillMaxWidth().height(3.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        trackColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.25f),
                    )
                }
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 6.dp)
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(bottomStart = 14.dp, bottomEnd = 14.dp)),
            ) {
                AndroidView(
                    factory = { webView },
                    modifier = Modifier.fillMaxSize(),
                    onReset = { it.setBackgroundColor(AndroidColor.TRANSPARENT) },
                )
            }
            Spacer(Modifier.navigationBarsPadding())
        }
    }
}
