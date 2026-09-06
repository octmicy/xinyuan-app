package cn.edu.xyc.campus.data.remote

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * 版本更新检查。
 * 策略：镜像源优先、多节点冗余、短超时快速切换、CDN 兜底——保证尽量收得到更新提示。
 *
 * 1) API 镜像池（社区加速节点，前缀 + GitHub API 原路径），任一可用即返回
 * 2) 静态 CDN 兜底：仓库根 update.json（发版时随代码更新），走 jsDelivr/Fastly/原生 raw
 * 3) GitHub 直连放最后（国内大概率连不上，但海外用户可用）
 *
 * 所有请求 8s 连接 / 10s 读取超时，单个节点失败立即切下一个。
 */
object UpdateChecker {

    data class UpdateInfo(
        val version: String,
        val downloadUrl: String,   // 原始 GitHub 直链
        val notes: String,
        val proxyPrefix: String?,  // 检测更新时验证可用的镜像前缀，下载复用同一镜像
    )

    private const val LATEST_PATH = "repos/octmicy/xinyuan-app/releases/latest"

    // 社区加速节点（前缀 + GitHub 原地址即可加速，API/下载文件通用）
    private val proxyHosts = listOf(
        "https://ghfast.top/",
        "https://gh-proxy.com/",
        "https://ghproxy.net/",
        "https://gh.llkk.cc/",
        "https://github.moeyy.xyz/",
    )

    // API 镜像池（镜像优先，直连殿后）
    private val apiEndpoints: List<String> =
        proxyHosts.map { it + "https://api.github.com/$LATEST_PATH" } +
            "https://api.github.com/$LATEST_PATH"

    /** 更新包下载源列表：镜像优先，官方直连殿后（供浏览器下载选择） */
    fun downloadMirrors(base: String): List<String> =
        proxyHosts.map { it + base } + base

    // 静态 CDN 兜底（仓库根 update.json，与 api 结构字段一致：version/url/notes）
    private val staticEndpoints = listOf(
        "https://cdn.jsdelivr.net/gh/octmicy/xinyuan-app@main/update.json",
        "https://fastly.jsdelivr.net/gh/octmicy/xinyuan-app@main/update.json",
        "https://raw.githubusercontent.com/octmicy/xinyuan-app/main/update.json",
    )

    // 检查更新专用：短超时，坏节点快速跳过
    private val client: OkHttpClient = CampusHttp.client.newBuilder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    /** 拉取最新版本信息；全部通道失败返回 null（视为无更新，不打扰用户） */
    suspend fun checkLatest(): UpdateInfo? = withContext(Dispatchers.IO) {
        apiEndpoints.firstNotNullOfOrNull { ep -> fetchApi(ep) }
            ?: staticEndpoints.firstNotNullOfOrNull { ep -> fetchStatic(ep) }
    }

    private fun fetchApi(ep: String): UpdateInfo? = runCatching {
        val prefix = proxyHosts.firstOrNull { ep.startsWith(it) }
        val req = Request.Builder().url(ep).header("Accept", "application/vnd.github+json").build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return@use null
            val obj = JSONObject(resp.body?.string().orEmpty())
            val version = obj.optString("tag_name").removePrefix("v")
            if (version.isEmpty()) return@use null
            var url = "https://github.com/octmicy/xinyuan-app/releases/latest"
            val assets = obj.optJSONArray("assets")
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val a = assets.optJSONObject(i) ?: continue
                    if (a.optString("name").endsWith(".apk")) {
                        url = a.optString("browser_download_url")
                        break
                    }
                }
            }
            UpdateInfo(version, url, obj.optString("body"), prefix)
        }
    }.getOrNull()

    private fun fetchStatic(ep: String): UpdateInfo? = runCatching {
        val req = Request.Builder().url(ep).build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return@use null
            val obj = JSONObject(resp.body?.string().orEmpty())
            val version = obj.optString("version")
            if (version.isEmpty()) return@use null
            // 静态 CDN 走通说明用户可达该 CDN，但下载仍需镜像代理 GitHub，取首个镜像为最优猜测
            UpdateInfo(
                version = version,
                downloadUrl = obj.optString(
                    "url",
                    "https://github.com/octmicy/xinyuan-app/releases/latest",
                ),
                notes = obj.optString("notes"),
                proxyPrefix = proxyHosts.firstOrNull(),
            )
        }
    }.getOrNull()

    /** 版本号比较：按 '.' 分段数值比较，如 0.2.4 < 0.3.0 */
    fun isNewer(latest: String, current: String): Boolean {
        val l = latest.split('.').map { it.trim().toIntOrNull() ?: 0 }
        val c = current.split('.').map { it.trim().toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(l.size, c.size)) {
            val li = l.getOrElse(i) { 0 }
            val ci = c.getOrElse(i) { 0 }
            if (li != ci) return li > ci
        }
        return false
    }

    /** 忽略/不再提醒 标记 */
    private const val PREFS = "update_flags"
    private const val KEY_IGNORED = "ignored_version"
    private const val KEY_NEVER = "never_remind"

    fun isIgnored(context: Context, version: String): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_IGNORED, null) == version

    fun setIgnored(context: Context, version: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_IGNORED, version).apply()
    }

    fun isNeverRemind(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_NEVER, false)

    fun setNeverRemind(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_NEVER, true).apply()
    }
}
