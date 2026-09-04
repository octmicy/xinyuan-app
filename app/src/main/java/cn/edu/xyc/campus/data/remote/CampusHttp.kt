package cn.edu.xyc.campus.data.remote

import android.webkit.CookieManager
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * 全局共享 HTTP 会话：门户（ehallmobile）与教务（zfjwxt）、学工（ssxt）的 Cookie
 * 存在同一个 Jar 中，SSO 跳转后各系统会话自动可用。
 */
object CampusHttp {

    const val PORTAL_HOST = "ehallmobile.xyc.edu.cn"
    const val JWXT_HOST = "zfjwxt.xyc.edu.cn"
    const val XG_HOST = "ssxt.xyc.edu.cn"

    /** 移动端浏览器 UA（OkHttp / WebView / 门户移动版页面统一使用） */
    const val MOBILE_UA: String =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/126.0.0.0 Mobile Safari/537.36"

    private val cookieStore = mutableListOf<Cookie>()

    val cookieJar = object : CookieJar {
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            synchronized(cookieStore) {
                cookies.forEach { new ->
                    cookieStore.removeAll { it.name == new.name && it.domain == new.domain }
                    cookieStore += new
                }
            }
        }

        override fun loadForRequest(url: HttpUrl): List<Cookie> =
            synchronized(cookieStore) { cookieStore.filter { it.matches(url) } }
    }

    val client: OkHttpClient = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .addInterceptor { chain ->
            // 学校门户/WAF 校验 UA，浏览器 UA 才放行 SSO 链路
            val uaReq = chain.request().newBuilder()
                .header("User-Agent", MOBILE_UA)
                .build()
            chain.proceed(uaReq)
        }
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    /** 手动写入会话 cookie（如登录返回的 token） */
    fun setCookie(host: String, name: String, value: String) {
        synchronized(cookieStore) {
            cookieStore.removeAll { it.name == name && it.domain == host }
            cookieStore += Cookie.Builder().name(name).value(value).domain(host).path("/").build()
        }
    }

    /** 把 OkHttp 会话 Cookie 同步到 WebView（请假申请的内嵌学工页面需要登录态） */
    fun syncToWebView() {
        val cm = CookieManager.getInstance()
        cm.setAcceptCookie(true)
        val cookies = snapshotCookies()
        cookies.forEach { c ->
            val scheme = if (c.secure) "https://" else "http://"
            val sb = StringBuilder("${c.name}=${c.value}")
            if (c.domain.startsWith(".")) sb.append("; Domain=${c.domain}")
            sb.append("; Path=${if (c.path.isNullOrEmpty()) "/" else c.path}")
            if (c.secure) sb.append("; Secure")
            cm.setCookie(scheme + c.domain.removePrefix(".") + "/", sb.toString())
        }
        cm.flush()
    }

    /** 退出登录：清空全部会话（含 WebView cookie） */
    fun clearSession() {
        synchronized(cookieStore) { cookieStore.clear() }
        runCatching {
            CookieManager.getInstance().removeAllCookies(null)
            CookieManager.getInstance().flush()
        }
    }

    fun snapshotCookies(): List<Cookie> =
        synchronized(cookieStore) { cookieStore.toList() }

    fun dumpCookies(): String = synchronized(cookieStore) {
        cookieStore.joinToString("\n") { "${it.domain}  ${it.name}=${it.value.take(20)}" }
    }
}
