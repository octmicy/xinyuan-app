package cn.edu.xyc.campus.data.remote

import android.content.Context
import cn.edu.xyc.campus.data.local.ScheduleCache
import cn.edu.xyc.campus.data.model.ThirdApp
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay

/**
 * 「图书馆电子证」小组件 → App 的启动事件总线：
 * 小组件点击 → MainActivity（extra）→ request() → AppRoot 收集后弹出应用内 WebView 直达电子证。
 */
object CredentialLauncher {
    const val EXTRA_OPEN_CREDENTIAL = "open_credential"

    /** 图书馆应用在门户 /app/getApplication 返回中的名称（与 AppsScreen 白名单一致） */
    const val LIBRARY_PORTAL_NAME = "我的图书馆"

    /** 电子证 SPA 落地路由（登录链完成后自动跳转） */
    const val LIBRARY_ROUTE = "/credential?from=Home"

    /**
     * 打开电子证的一次性事件（Channel：事件只投递给在场消费者一次，不重放）。
     * 勿改用 StateFlow —— 重放会让每次冷启动都误弹电子证。
     */
    val requests = Channel<Unit>(Channel.BUFFERED)

    fun request() {
        requests.trySend(Unit)
    }

    /**
     * 解析电子证跳转目标：
     * - 未登录时短暂等待自动登录完成（冷启动点小组件的常见场景），超时返回 null 由调用方提示
     * - 门户应用列表优先读缓存，未命中则拉取一次
     */
    suspend fun resolveTarget(context: Context): CredentialTarget? {
        // 最多等 5s 自动登录（每 400ms 检查一次会话令牌）
        var waited = 0L
        while (SessionStore.token.isNullOrEmpty() && waited < 5_000L) {
            delay(400)
            waited += 400
        }
        if (SessionStore.token.isNullOrEmpty()) return null

        val list = ScheduleCache.applications["APPS"] ?: PortalApi.getApplications().getOrNull()?.also {
            ScheduleCache.applications["APPS"] = it
        } ?: return null
        val candidates = list.filter { it.name == LIBRARY_PORTAL_NAME && it.href.isNotBlank() }
        val app = candidates.firstOrNull { it.href.contains("xyoauthlogin") }
            ?: candidates.firstOrNull()
            ?: return null
        return CredentialTarget(name = "图书馆电子证", url = app.ticketUrl(), finalHash = LIBRARY_ROUTE)
    }
}

/** 电子证跳转目标（应用内 WebView 直达参数） */
data class CredentialTarget(val name: String, val url: String, val finalHash: String)

/** 按门户 openThirdPage 语义构造跳转地址：hrefType!=5 的应用拼接 ticket 免密登录 */
fun ThirdApp.ticketUrl(): String {
    val sep = if (href.contains("?")) "&" else "?"
    return if (hrefType == 5) href
    else href + sep + "ticket=" + SessionStore.token.orEmpty()
}
