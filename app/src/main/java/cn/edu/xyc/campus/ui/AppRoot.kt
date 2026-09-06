package cn.edu.xyc.campus.ui

import android.content.Intent
import android.net.Uri
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
import cn.edu.xyc.campus.data.local.IntroStore
import cn.edu.xyc.campus.data.local.ScheduleCache
import cn.edu.xyc.campus.data.local.StoredCredential
import cn.edu.xyc.campus.data.local.TodayStore
import cn.edu.xyc.campus.data.remote.JwxtApi
import cn.edu.xyc.campus.data.remote.LoginResult
import cn.edu.xyc.campus.data.remote.PortalApi
import cn.edu.xyc.campus.data.remote.SessionStore
import cn.edu.xyc.campus.data.remote.UpdateChecker
import cn.edu.xyc.campus.ui.screens.LoginScreen
import cn.edu.xyc.campus.ui.screens.MainTabs
import cn.edu.xyc.campus.widget.TodayWidget
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.delay
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
            onLoginSuccess = { loggedIn = true },
        )
    }
}
