package cn.edu.xyc.campus.ui.screens

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.os.Handler
import android.os.Looper
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.OpenInBrowser
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import cn.edu.xyc.campus.data.remote.CampusHttp

/**
 * 应用内打开第三方系统：走门户跳转登录链（Cookie 由 CampusHttp 同步）。
 * finalHash：SPA 登录落地后自动跳目标路由（如图书馆电子证）。
 * 注入带重试——SPA 初始化完成前设置 hash 会被路由重置，故延迟+校验多次。
 * 外壳与 App 品牌统一：渐变顶栏、品牌加载态（网页本体无法主题化）。
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
    var currentHost by remember { mutableStateOf("") }
    var menuOpen by remember { mutableStateOf(false) }

    val primary = MaterialTheme.colorScheme.primary
    val primaryContainer = MaterialTheme.colorScheme.primaryContainer
    val pageBg = MaterialTheme.colorScheme.background

    val webView = remember {
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.userAgentString = CampusHttp.MOBILE_UA
            setBackgroundColor(AndroidColor.TRANSPARENT)
        }
    }
    val handler = remember { Handler(Looper.getMainLooper()) }

    // finalHash 注入：落地目标域后直接 loadUrl 导航到目标 hash（同域 hash 变化不重置登录态，
    // 比 evaluateJavascript 设 hash 更稳），然后读取实际 hash 校验，未到目标则重试（最多 4 次）
    var injectedDone by remember { mutableStateOf(false) }
    var navAttempts by remember { mutableIntStateOf(0) }
    fun tryInject(): Unit {
        if (injectedDone) return
        val target = "https://mfindxyc.libsp.cn/#" + (finalHash?.removePrefix("#") ?: "")
        android.util.Log.d("XycApp", "libsp nav attempt ${navAttempts + 1}: $target")
        webView.loadUrl(target)
        navAttempts++
        // 2.2s 后校验是否已到目标路由，SPA 初始化晚则重试
        handler.postDelayed({
            webView.evaluateJavascript("window.location.hash") { h ->
                android.util.Log.d("XycApp", "libsp current hash=$h")
                val ok = h?.contains("credential") == true
                if (ok) {
                    injectedDone = true
                } else if (navAttempts < 4) {
                    tryInject()
                }
            }
        }, 2200)
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
            // 登录链落地到图书馆域（任何路径）后开始注入目标路由
            if (finalHash != null && !injectedDone && url.contains("mfindxyc.libsp.cn")) {
                handler.postDelayed({ tryInject() }, 1200)
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
            // 品牌渐变顶栏（与登录页同风格）
            Column(
                Modifier.background(
                    Brush.verticalGradient(listOf(primary, primaryContainer)),
                ),
            ) {
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
                if (progress < 100) {
                    LinearProgressIndicator(
                        progress = { progress / 100f },
                        modifier = Modifier.fillMaxWidth().height(3.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        trackColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.25f),
                    )
                }
            }

            Box(Modifier.fillMaxSize()) {
                AndroidView(
                    factory = { webView },
                    modifier = Modifier.fillMaxSize(),
                )
                // 品牌加载态：首屏较慢时展示（进度过半后淡出交给网页自身）
                if (progress < 25) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(pageBg),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            cn.edu.xyc.campus.ui.components.ThemeImage(
                                key = "login_logo",
                                resId = cn.edu.xyc.campus.R.drawable.ic_launcher_foreground,
                                modifier = Modifier.size(88.dp),
                            )
                            Spacer(Modifier.height(12.dp))
                            CircularProgressIndicator(Modifier.size(24.dp))
                        }
                    }
                }
            }
            Spacer(Modifier.navigationBarsPadding())
        }
    }
}
