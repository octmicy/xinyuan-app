package cn.edu.xyc.campus.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import cn.edu.xyc.campus.data.local.CredStore
import cn.edu.xyc.campus.data.local.ScheduleCache
import cn.edu.xyc.campus.data.local.StoredCredential
import cn.edu.xyc.campus.data.local.TodayStore
import cn.edu.xyc.campus.data.remote.JwxtApi
import cn.edu.xyc.campus.data.remote.LoginResult
import cn.edu.xyc.campus.data.remote.PortalApi
import cn.edu.xyc.campus.data.remote.SessionStore
import cn.edu.xyc.campus.ui.screens.LoginScreen
import cn.edu.xyc.campus.ui.screens.MainTabs
import cn.edu.xyc.campus.widget.TodayWidget
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.launch

@Composable
fun AppRoot() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var loggedIn by rememberSaveable { mutableStateOf(false) }
    var autoChecking by rememberSaveable { mutableStateOf(CredStore.load() != null) }
    var prefill by remember { mutableStateOf<StoredCredential?>(null) }

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

    when {
        autoChecking -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(Modifier.size(36.dp))
                Spacer(Modifier.height(14.dp))
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
