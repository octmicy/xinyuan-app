package cn.edu.xyc.campus.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * 学工系统（ssxt.xyc.edu.cn）客户端。
 *
 * 学生学院名称的唯一正确来源：学工 getById.htm 返回 JSON 的 dwmc 字段
 * （xymc 为 null 不可用；学籍 xsxx 的学院字段常为空；成绩的 kkbmmc 是开课院系会显示错误，均不用）。
 *
 * 认证为纯 Cookie 会话：由 wiseduIndex.jsp?ticket=<门户token> 完成学工 CAS 登录链后种下 cookie，
 * cookie 由 CampusHttp.cookieJar 在进程内保持；无需 token 与自定义头。
 */
object XgApi {

    private const val XG = "https://ssxt.xyc.edu.cn"
    private const val GET_BY_ID = "$XG/syt/student/getById.htm"
    private const val REFERER = "$XG/webApp/xuegong/index.html"

    /** 本次进程内是否已做过学工登录（ticket 一次性，登录后会话 cookie 由 cookieJar 保持） */
    private var loggedIn = false

    /**
     * 拉取学生学院名称（学工 getById.htm 的 dwmc）；未登录/失败返回 null。
     *
     * 首次调用先 GET wiseduIndex.jsp?ticket=<门户token> 走学工登录链（跟随重定向，只为让
     * 会话 cookie 落地，不解析 body），再 GET getById.htm 取 dwmc。
     * 未登录、网络异常、字段为空等一律返回 null（不抛异常），由调用方决定是否隐藏学院行。
     */
    suspend fun fetchCollege(): String? = withContext(Dispatchers.IO) {
        runCatching {
            // 独立 client：仅学工链路加 8s callTimeout（复用连接池与 CookieJar），弱网快速失败
            val client = CampusHttp.client.newBuilder()
                .callTimeout(8, TimeUnit.SECONDS)
                .build()

            if (!loggedIn) {
                val token = SessionStore.token ?: return@runCatching null
                val loginReq = Request.Builder()
                    .url("$XG/wiseduIndex.jsp?ticket=$token")
                    .header("Referer", PortalApi.BASE + "/mobile/index")
                    .get()
                    .build()
                // 只为实现 cookie 落地，读掉 body 释放连接
                client.newCall(loginReq).execute().use { it.body?.string().orEmpty() }
                loggedIn = true
            }

            val req = Request.Builder()
                .url(GET_BY_ID)
                .header("Referer", REFERER)
                .get()
                .build()
            client.newCall(req).execute().use { resp ->
                val dwmc = JSONObject(resp.body?.string().orEmpty()).optString("dwmc").trim()
                if (dwmc.isEmpty()) null else dwmc
            }
        }.getOrNull()
    }
}