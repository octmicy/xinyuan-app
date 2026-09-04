package cn.edu.xyc.campus.data.remote

import cn.edu.xyc.campus.data.model.ThirdApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.Request
import org.json.JSONObject

/** 登录结果 */
sealed class LoginResult {
    /** 成功：token 为门户会话令牌 */
    data class Success(val token: String) : LoginResult()

    /** 新设备风控：需要短信验证绑定设备 */
    data class NeedSms(val message: String) : LoginResult()

    /** 业务失败（密码错误 / 维护中 / 其他） */
    data class Failure(val code: String, val message: String) : LoginResult()

    /** 网络或程序异常 */
    data class Error(val throwable: Throwable) : LoginResult()
}

/** 登录态内存持有（M4 再做加密持久化） */
object SessionStore {
    var token: String? = null
    var account: String = ""

    /** SSO 落地页 HTML（内含 wapLogin 菜单签名），由 ensureJwxtSession 填充 */
    var jwxtHomeHtml: String? = null
}

/** 学院信息门户（正方 CampusHoy）客户端 */
object PortalApi {

    const val BASE: String = "https://ehallmobile.xyc.edu.cn"
    private const val API: String = "$BASE/api/v4/api"

    /**
     * 账密登录门户（loginType=2，3DES 加密链见 CryptoUtil）。
     * 接口: POST /api/v4/api/login
     */
    suspend fun login(account: String, password: String): LoginResult = withContext(Dispatchers.IO) {
        try {
            val enc = CryptoUtil.reEncrypt(account, password)
            val form = FormBody.Builder()
                .add("userDevice", account + System.currentTimeMillis())
                .add("loginName", enc.userName)
                .add("key", enc.key)
                .add("passWord", enc.userPassWord)
                .add("loginType", "2")
                .build()
            val req = Request.Builder()
                .url("$API/login")
                .header("Referer", "$BASE/mobile/index")
                .header("Origin", BASE)
                .post(form)
                .build()
            CampusHttp.client.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                val obj = JSONObject(body)
                when (val code = obj.optString("code")) {
                    "200" -> {
                        val token = obj.optString("token")
                        CampusHttp.setCookie(CampusHttp.PORTAL_HOST, "token", token)
                        LoginResult.Success(token)
                    }
                    "1111" -> LoginResult.NeedSms(obj.optString("message"))
                    else -> LoginResult.Failure(code, obj.optString("message"))
                }
            }
        } catch (t: Throwable) {
            LoginResult.Error(t)
        }
    }

    /** 校验登录态 */
    suspend fun currentUser(): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val req = authBuilder("$API/user/currentUser").build()
            CampusHttp.client.newCall(req).execute().use { it.body?.string().orEmpty() }
        }
    }

    /** 第三方应用列表（ehall 聚合入口数据源） */
    suspend fun getApplications(): Result<List<ThirdApp>> = withContext(Dispatchers.IO) {
        runCatching {
            val req = authBuilder("$API/app/getApplication").build()
            CampusHttp.client.newCall(req).execute().use { resp ->
                val arr = JSONObject(resp.body?.string().orEmpty()).optJSONArray("data")
                (0 until (arr?.length() ?: 0)).mapNotNull { i ->
                    arr?.optJSONObject(i)?.let {
                        ThirdApp(
                            name = it.optString("name", ""),
                            href = it.optString("href", ""),
                            hrefType = it.optInt("hrefType", 0),
                        )
                    }
                }
            }
        }
    }

    fun authBuilder(url: String): Request.Builder =
        Request.Builder().url(url)
            .header("Authorization", SessionStore.token.orEmpty())
            .header("X-Requested-With", "XMLHttpRequest")

    fun clearSession() {
        SessionStore.token = null
        SessionStore.jwxtHomeHtml = null
        CampusHttp.clearSession()
    }
}
