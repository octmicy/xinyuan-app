package cn.edu.xyc.campus.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.SystemBarStyle
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.edu.xyc.campus.R
import cn.edu.xyc.campus.data.local.CredStore
import cn.edu.xyc.campus.data.local.CrashLog
import cn.edu.xyc.campus.data.local.IntroStore
import cn.edu.xyc.campus.data.local.ScheduleCache
import cn.edu.xyc.campus.data.local.StoredCredential
import cn.edu.xyc.campus.data.local.TodayStore
import cn.edu.xyc.campus.data.remote.CredentialLauncher
import cn.edu.xyc.campus.data.remote.CredentialTarget
import cn.edu.xyc.campus.data.remote.JwxtApi
import cn.edu.xyc.campus.data.remote.LoginResult
import cn.edu.xyc.campus.data.remote.PortalApi
import cn.edu.xyc.campus.data.remote.SessionStore
import cn.edu.xyc.campus.data.remote.UpdateChecker
import cn.edu.xyc.campus.ui.screens.AppWebViewDialog
import cn.edu.xyc.campus.ui.screens.LoginScreen
import cn.edu.xyc.campus.ui.screens.MainTabs
import cn.edu.xyc.campus.ui.screens.feedbackTemplate
import cn.edu.xyc.campus.ui.theme.ThemeModeStore
import cn.edu.xyc.campus.widget.TodayWidget
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.launch

@Composable
fun AppRoot() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var loggedIn by rememberSaveable { mutableStateOf(false) }
    var autoChecking by rememberSaveable { mutableStateOf(CredStore.load() != null) }
    var introDone by rememberSaveable { mutableStateOf(IntroStore.isDone(context)) }
    var updateInfo by remember { mutableStateOf<UpdateChecker.UpdateInfo?>(null) }
    var prefill by remember { mutableStateOf<StoredCredential?>(null) }
    // 自动登录失败/超时落到登录页时展示的提示（如"请连接校园网"）
    var loginMessage by remember { mutableStateOf("") }
    // 「图书馆电子证」小组件点击 → 弹应用内 WebView 直达（顶层弹出，任意 tab 均生效）
    var credentialTarget by remember { mutableStateOf<CredentialTarget?>(null) }

    // 后台久置（>10 分钟）回前台时静默重登：门户会话过期会导致接口全部加载失败
    val activity = context as? ComponentActivity
    var stoppedAt by remember { mutableStateOf(0L) }
    DisposableEffect(activity) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_STOP -> stoppedAt = System.currentTimeMillis()
                androidx.lifecycle.Lifecycle.Event.ON_START -> {
                    val away = System.currentTimeMillis() - stoppedAt
                    if (stoppedAt > 0 && away > 10 * 60_000L && loggedIn) {
                        scope.launch {
                            val cred = CredStore.load() ?: return@launch
                            when (val r = PortalApi.login(cred.account, cred.password)) {
                                is LoginResult.Success -> {
                                    SessionStore.token = r.token
                                    SessionStore.account = cred.account
                                    ScheduleCache.clear() // 会话已换新，各页重新拉取
                                }
                                else -> Unit // 静默失败：等接口报会话过期时由页面级重试兜底
                            }
                        }
                    }
                }
                else -> Unit
            }
        }
        activity?.lifecycle?.addObserver(observer)
        onDispose { activity?.lifecycle?.removeObserver(observer) }
    }

    // 状态栏/导航栏图标色跟随应用内深浅色（ThemeModeStore 可覆盖系统夜间模式）
    val appDark = ThemeModeStore.resolvedDark(context)
    LaunchedEffect(appDark) {
        (context as? ComponentActivity)?.enableEdgeToEdge(
            statusBarStyle = if (appDark) {
                SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
            } else {
                SystemBarStyle.light(
                    android.graphics.Color.TRANSPARENT,
                    android.graphics.Color.TRANSPARENT,
                )
            },
            navigationBarStyle = if (appDark) {
                SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
            } else {
                SystemBarStyle.light(
                    android.graphics.Color.TRANSPARENT,
                    android.graphics.Color.TRANSPARENT,
                )
            },
        )
    }

    // 进入主界面后静默检查更新（直连+镜像自动回退；忽略/不再提醒的不弹）
    LaunchedEffect(loggedIn) {
        if (!loggedIn) return@LaunchedEffect
        delay(2500)
        val info = UpdateChecker.checkLatest() ?: return@LaunchedEffect
        val current = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: return@LaunchedEffect
        if (UpdateChecker.isNewer(info.version, current) &&
            !UpdateChecker.isIgnored(context, info.version) &&
            !UpdateChecker.isNeverRemind(context)
        ) {
            updateInfo = info
        }
    }

    // 冷启动静默重登：解决"清理后台后要重新输入账号密码"
    LaunchedEffect(autoChecking) {
        if (!autoChecking) return@LaunchedEffect
        val cred = CredStore.load()
        if (cred == null) {
            android.util.Log.d("XycApp", "auto login: no credential")
            autoChecking = false
            return@LaunchedEffect
        }
        when (val r = PortalApi.login(cred.account, cred.password)) {
            is LoginResult.Success -> {
                android.util.Log.d("XycApp", "auto login OK")
                SessionStore.token = r.token
                SessionStore.account = cred.account
                loggedIn = true
                loginMessage = ""
                // 登录前就添加了课表小组件的场景：拿到会话后立即刷新一次
                runCatching { TodayWidget().updateAll(context) }
            }
            is LoginResult.Timeout -> {
                // 登录超时：退回登录页并提示检查网络（多为使用流量未连校园网）
                android.util.Log.e("XycApp", "auto login timeout")
                prefill = cred
                loginMessage = "登录超时。如果你现在正在使用流量，请连接校园网后再尝试登录。"
            }
            else -> {
                android.util.Log.e("XycApp", "auto login failed: $r")
                prefill = cred // 失败落到登录页（预填凭证）
            }
        }
        autoChecking = false
    }

    // 更新弹窗：标题带新版本号，正文为发布说明，可 更新/忽略/本次版本不再提醒
    updateInfo?.let { info ->
        cn.edu.xyc.campus.ui.components.UpdateDialog(
            version = info.version,
            notes = info.notes,
            downloadUrl = info.downloadUrl,
            proxyPrefix = info.proxyPrefix,
            onDismiss = { updateInfo = null },
            onIgnored = {
                UpdateChecker.setIgnored(context, info.version)
                updateInfo = null
            },
            onNeverRemind = {
                UpdateChecker.setNeverRemind(context)
                updateInfo = null
            },
        )
    }

    // 本地崩溃日志检测：上次异常退出的残留日志 → 提示一键附带进反馈模板（不联网上传）
    var crashFiles by remember { mutableStateOf(CrashLog.pending(context)) }
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    if (crashFiles.isNotEmpty()) {
        val brief = runCatching { crashFiles.first().readText().take(300) }.getOrDefault("")
        AlertDialog(
            onDismissRequest = { crashFiles = emptyList() }, // 点外部仅收起，日志保留待下次询问
            title = { Text("上次异常退出") },
            text = {
                Text(
                    brief.ifBlank { "检测到崩溃日志" },
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 6,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val full = (CrashLog.summary(context)?.let { "$it\n\n" } ?: "") + feedbackTemplate(context)
                    clipboard.setText(androidx.compose.ui.text.AnnotatedString(full))
                    android.widget.Toast.makeText(
                        context,
                        "已复制反馈文本（含崩溃日志），可粘贴到 Issue 或邮件发送",
                        android.widget.Toast.LENGTH_LONG,
                    ).show()
                    CrashLog.clear(context)
                    crashFiles = emptyList()
                }) { Text("复制反馈并清理") }
            },
            dismissButton = {
                TextButton(onClick = {
                    CrashLog.clear(context)
                    crashFiles = emptyList()
                }) { Text("删除日志") }
            },
        )
    }

    // 小组件「图书馆电子证」点击事件（Channel 一次性消费，不会跨启动重放）：
    // 解析目标（未登录时内部等待自动登录）→ 弹应用内 WebView
    LaunchedEffect(Unit) {
        CredentialLauncher.requests.consumeAsFlow().collect {
            val target = CredentialLauncher.resolveTarget(context)
            if (target == null) {
                android.widget.Toast.makeText(
                    context,
                    if (SessionStore.token.isNullOrEmpty()) "请先登录后再使用小组件" else "未找到图书馆入口，请打开应用检查",
                    android.widget.Toast.LENGTH_SHORT,
                ).show()
            } else {
                credentialTarget = target
            }
        }
    }

    // 电子证直达 WebView（与宫格「图书馆电子证」同链路：ticket 免密 + SPA 自动落地目标路由）
    credentialTarget?.let { t ->
        AppWebViewDialog(
            name = t.name,
            url = t.url,
            finalHash = t.finalHash,
            onDismiss = { credentialTarget = null },
        )
    }

    when {
        // 首次打开：新手引导优先（完成后进入自动登录/登录流程）
        !introDone -> OnboardingScreen(onDone = {
            IntroStore.setDone(context)
            introDone = true
        })
        autoChecking -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Image(
                    painter = painterResource(R.drawable.ic_launcher_foreground),
                    contentDescription = null,
                    modifier = Modifier.size(96.dp),
                )
                Spacer(Modifier.height(8.dp))
                Text("新院助手", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(18.dp))
                CircularProgressIndicator(Modifier.size(28.dp))
                Spacer(Modifier.height(12.dp))
                Text("自动登录中…", style = MaterialTheme.typography.bodyMedium)
            }
        }
        loggedIn -> MainTabs(
            onLogout = {
                PortalApi.clearSession()
                JwxtApi.resetSso()
                ScheduleCache.clear()
                TodayStore.clear(context) // 小组件回到"打开App同步课表"
                scope.launch {
                    runCatching { TodayWidget().updateAll(context) }
                }
                CredStore.clear() // 退出登录同时清凭证，保证能真正退出
                loggedIn = false
            },
        )
        else -> LoginScreen(
            initialAccount = prefill?.account.orEmpty(),
            initialPassword = prefill?.password.orEmpty(),
            initialMessage = loginMessage,
            onLoginSuccess = {
                // 手动登录成功同样立即刷新课表小组件
                scope.launch { runCatching { TodayWidget().updateAll(context) } }
                loggedIn = true
            },
        )
    }
}
