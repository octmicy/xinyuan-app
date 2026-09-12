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
import cn.edu.xyc.campus.data.local.BorrowStore

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
    var zoomReloadedFor by remember { mutableStateOf<String?>(null) }

    val primary = MaterialTheme.colorScheme.primary
    val primaryContainer = MaterialTheme.colorScheme.primaryContainer
    val pageBg = MaterialTheme.colorScheme.background

    val webView = remember {
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.userAgentString = CampusHttp.MOBILE_UA
            // 兼容性修复（成绩等宽表格页面显示不全）：
            // - wideViewPort + overviewMode：按页面 viewport 渲染并自适应屏宽
            // - 混合内容放行：教务老站存在 http 资源，默认阻止会导致内容缺失
            // - 第三方 Cookie 放行：门户 SSO 跨域跳教务域，默认拒绝会丢会话/资源
            // - TEXT_AUTOSIZING：按 viewport 智能调字体，避免固定行高撑破布局
            // - 固定 textZoom=100：系统字体放大时 WebView 文本等比放大易撑破布局
            // - 双指缩放兜底：页面自身超宽时用户可缩放查看
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true
            settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            settings.layoutAlgorithm = android.webkit.WebSettings.LayoutAlgorithm.TEXT_AUTOSIZING
            settings.textZoom = 100
            settings.setSupportZoom(true)
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
            android.webkit.CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            setBackgroundColor(AndroidColor.TRANSPARENT)
        }
    }
    val handler = remember { Handler(Looper.getMainLooper()) }

    // finalHash 直达（图书馆 libsp SPA，dva+hash 路由）。逆向结论：
    // 门户 href+ticket 链落地 libsp 后，客户端直接改 hash 会被 credential 页守卫
    // （登录链未完成时 isEmpty(userInfo) → push /login）弹回首页。
    // 官方深链协议：SPA 下发的 findConfig.mssoLoginUrl（unified-auth.chaoxing.com 入口，
    // refer 含 find/sso/login/xyc/1?page=Home），把 page= 换成目标路由再走一遍
    // → chaoxing 会话复用 → 服务端 302 落地 #/<page>?jwt=... → SPA 正常执行 Junmp
    // 登录链（写 userInfo）并停在目标路由，无任何守卫问题。
    // 兜底：mssoLoginUrl 读不到时，轮询 sessionStorage.userInfo（visibilitychange
    // 触发 SPA 自带 updateUserInfo 补登录）就绪后再 hash 导航。
    var injectedDone by remember { mutableStateOf(false) }
    var injectStarted by remember { mutableStateOf(false) }
    var injectOrigin by remember { mutableStateOf("") }
    var navAttempts by remember { mutableIntStateOf(0) }
    var recoverTries by remember { mutableIntStateOf(0) }

    fun injectPage(): String = finalHash?.removePrefix("#")?.trim('/')?.substringBefore('?') ?: ""

    // 兜底：stage 0 = 检查/恢复登录态；stage 2 = hash 导航目标路由并校验
    /** 借阅同步：同源 fetch 借阅列表（jwtOpacAuth 会话）→ 落盘 → 临期通知 */
    fun syncBorrows(view: WebView) {
        view.evaluateJavascript(
            "(function(){try{var h=sessionStorage.getItem('jwtHeader');var hd={};if(h&&sessionStorage.getItem('jwt')){hd[h]=sessionStorage.getItem('jwt')}" +
                "return fetch('/find/loanInfo/loanList',{method:'POST',credentials:'include',headers:Object.assign({'Content-Type':'application/json'},hd),body:JSON.stringify({page:1,rows:50})})" +
                ".then(function(r){return r.text()}).catch(function(e){return 'ERR:'+e.message})}catch(e){return 'CERR:'+e.message}})()",
        ) { v ->
            val text = v?.trim()?.removeSurrounding("\"") ?: return@evaluateJavascript
            if (text.startsWith("ERR") || text.startsWith("CERR")) {
                android.util.Log.d("XycApp", "borrows sync failed: $text")
                return@evaluateJavascript
            }
            cn.edu.xyc.campus.data.local.BorrowStore.save(context, text)
            cn.edu.xyc.campus.data.reminder.BorrowReminder.checkDue(context, text)
        }
    }


    fun injectStep(stage: Int) {
        if (injectedDone || injectOrigin.isEmpty()) return
        when (stage) {
            0 -> webView.evaluateJavascript(
                "JSON.stringify({u:(sessionStorage.getItem('userInfo')||'').length>0," +
                    "c:document.cookie.indexOf('jwt=')>=0})",
            ) { v ->
                android.util.Log.d("XycApp", "libsp login state=$v")
                val hasUser = v?.contains("\"u\":true") == true
                val hasJwt = v?.contains("\"c\":true") == true
                when {
                    hasUser -> injectStep(2)
                    // SPA 自带恢复：visibilitychange → updateUserInfo → getLoginUserInfo 补 userInfo，
                    // 预置 loginNextPath 让恢复流程自己跳到目标路由
                    hasJwt && recoverTries < 6 -> {
                        recoverTries++
                        val page = injectPage()
                        webView.evaluateJavascript(
                            "(function(){try{localStorage.setItem('loginNextPath','/$page');" +
                                "document.dispatchEvent(new Event('visibilitychange'));return 1}catch(e){return 0}})()",
                            null,
                        )
                        handler.postDelayed({ injectStep(0) }, 1500)
                    }
                    navAttempts < 4 -> injectStep(2) // 恢复不了也硬试一次
                }
            }
            else -> {
                navAttempts++
                val target = injectOrigin + "#" + (finalHash?.removePrefix("#") ?: "")
                android.util.Log.d("XycApp", "libsp nav attempt $navAttempts: $target")
                webView.loadUrl(target)
                handler.postDelayed({
                    webView.evaluateJavascript("window.location.hash") { h ->
                        android.util.Log.d("XycApp", "libsp current hash=$h")
                        if (h?.contains(injectPage()) == true) {
                            injectedDone = true
                        } else if (navAttempts < 4) {
                            injectStep(0)
                        }
                    }
                }, 2200)
            }
        }
    }

    fun verifyHash(attempt: Int, host: String) {
        if (injectedDone) return
        handler.postDelayed({
            if (injectedDone) return@postDelayed
            webView.evaluateJavascript("window.location.hash") { h ->
                android.util.Log.d("XycApp", "libsp current hash=$h")
                when {
                    h?.contains(injectPage()) == true -> {
                        injectedDone = true
                        syncBorrows(webView)
                    }
                    attempt < 2 -> verifyHash(attempt + 1, host)
                    else -> injectStep(0) // 官方链未达 → 轮询兜底
                }
            }
        }, 3500)
    }

    fun startOfficialDeepLink(host: String) {
        val page = injectPage()
        if (page.isEmpty()) return
        // 从 SPA 配置里取官方 SSO 入口（学校下发的 findConfig），替换 page= 为目标路由
        webView.evaluateJavascript(
            "(function(){try{return JSON.parse(localStorage.getItem('findConfig')||'{}').mssoLoginUrl||''}catch(e){return ''}})()",
        ) { v ->
            val sso = v?.trim()?.removeSurrounding("\"")?.takeIf { it.startsWith("http") }
            if (sso != null && sso.contains("page=")) {
                val target = sso.replace(Regex("page=[A-Za-z0-9_]+"), "page=$page")
                android.util.Log.d("XycApp", "libsp official sso: $target")
                webView.loadUrl(target)
                verifyHash(1, host)
            } else {
                android.util.Log.d("XycApp", "libsp no mssoLoginUrl, fallback polling")
                injectOrigin = "https://$host/"
                injectStep(0)
            }
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
            // 注意：不重置 zoomReloadedFor——同一 URL 只允许一次缩放重载，否则与深链导航互相触发死循环
        }

        override fun onPageFinished(view: WebView, url: String?) {
            url ?: return
            // 强制可缩放：页面 viewport 带 user-scalable=no / maximum-scale 限制时改写并重载一次
            view.evaluateJavascript(
                "(function(){try{var ms=document.querySelectorAll('meta[name=viewport]');var ch=0;" +
                    "for(var i=0;i<ms.length;i++){var c=ms[i].getAttribute('content')||'';" +
                    "var nc=c.replace(/user-scalable\\s*=\\s*no/ig,'user-scalable=yes').replace(/maximum-scale\\s*=\\s*[\\\\d.]+/ig,'maximum-scale=10');" +
                    "if(nc!==c){ms[i].setAttribute('content',nc);ch++}}return 'patched:'+ch}catch(e){return 'ERR'}})()",
            ) { v ->
                android.util.Log.d("XycApp", "viewport patch: $v")
                if (v?.contains("patched:0") == false && zoomReloadedFor != url) {
                    zoomReloadedFor = url
                    view.post { view.reload() }
                }
            }
            // 登录链落地图书馆域（任何 libsp 子域）后启动直达；只启动一次，
            // 避免 loadUrl 触发的 onPageFinished 重复排队
            val host = runCatching { Uri.parse(url).host.orEmpty() }.getOrNull().orEmpty()
            android.util.Log.d("XycApp", "libsp page finished url=$url")
            if (finalHash != null && !injectStarted && host.endsWith("libsp.cn")) {
                injectStarted = true
                // 【调研桩】借阅列表接口探测（拿到真实返回结构后移除）
                handler.postDelayed({
                    view.evaluateJavascript(
                        "(function(){try{var h=sessionStorage.getItem('jwtHeader');var hd={};if(h&&sessionStorage.getItem('jwt')){hd[h]=sessionStorage.getItem('jwt')}" +
                            "return fetch('/find/loanInfo/loanList',{method:'POST',credentials:'include',headers:Object.assign({'Content-Type':'application/json'},hd),body:JSON.stringify({page:1,rows:50})})" +
                            ".then(function(r){return r.text()}).then(function(t){return 'OK:'+t.substring(0,3500)}).catch(function(e){return 'ERR:'+e.message})}catch(e){return 'CERR:'+e.message}})()",
                    ) { v -> android.util.Log.d("XycApp", "libsp loanList: $v") }
                }, 12000)
                // 【调研桩】Dump 页面链接（借阅列表路由发现用，发布版可移除）
                handler.postDelayed({
                    view.evaluateJavascript(
                        "(function(){try{var out=[];document.querySelectorAll('a').forEach(function(a){var h=a.getAttribute('href');if(h&&h.length>1)out.push(h+' | '+(a.innerText||'').trim().substring(0,16))});return out.join('|§|').substring(0,3000)}catch(e){return 'ERR'}})()",
                    ) { v -> android.util.Log.d("XycApp", "libsp links: $v") }
                }, 6000)
                // 已在目标路由（链路未来变化直达）就无需处理
                if (url.contains("#/" + injectPage())) {
                    injectedDone = true
                } else {
                    handler.postDelayed({ startOfficialDeepLink(host) }, 800)
                }
            }
            // 兼容补丁：正方移动端页面在 WebView 下 body/.mui-content 高度塌陷为 0，
            // 文档不可滚动 → 首屏之外的成绩看不到（实测 DevTools: body h=0、docScrollH=innerHeight）。
            // 延迟到页面 JS 初始化后检测，确认塌陷才把高度链改回 auto 恢复滚动。
            if (host.endsWith("xyc.edu.cn")) {
                handler.postDelayed({
                    view.evaluateJavascript(
                        "(function(){try{" +
                            "var de=document.documentElement,b=document.body;" +
                            "if(!b) return 'no-body';" +
                            "if(b.getBoundingClientRect().height===0||document.documentElement.scrollHeight<=innerHeight){" +
                            "de.style.height='auto';" +
                            "b.style.height='auto';b.style.overflowY='auto';" +
                            "var mc=document.querySelector('.mui-content');if(mc)mc.style.height='auto';" +
                            "var sc=document.querySelector('.mui-scroll-wrapper');if(sc)sc.style.height='auto';" +
                            "return 'patched:'+document.documentElement.scrollHeight" +
                            "} return 'skip'}catch(e){return 'err:'+e.message}})()",
                    ) { v -> android.util.Log.d("XycApp", "body-collapse patch: $v") }
                }, 1200)
            }
            // 【调研桩】图书馆页面文本Dump（借阅数据结构分析用，发布版可移除）
            if (host.endsWith("libsp.cn")) {
                handler.postDelayed({
                    view.evaluateJavascript(
                        "(function(){try{return document.body.innerText.substring(0,4000)}catch(e){return 'ERR:'+e.message}})()",
                    ) { v -> android.util.Log.d("XycApp", "libsp page text: $v") }
                }, 8000)
            }
        }
    }

    LaunchedEffect(Unit) {
        CampusHttp.syncToWebView()
        webView.loadUrl(url)
    }

    // 返回 = 网页有历史先回退上一页；到底了才关闭窗口（系统返回键与顶栏按钮同逻辑）
    fun goBackOrClose() {
        if (webView.canGoBack()) webView.goBack() else onDismiss()
    }

    Dialog(
        onDismissRequest = { goBackOrClose() },
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
                    IconButton(onClick = { goBackOrClose() }) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            if (webView.canGoBack()) "返回上一页" else "关闭",
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
