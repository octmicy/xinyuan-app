package cn.edu.xyc.campus.data.remote

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject

/**
 * 版本更新检查：按顺序尝试 GitHub 直连与公共加速镜像，哪个通用哪个（自动配置镜像源）。
 * 只做只读查询，下载交给浏览器打开发布页/直链。
 */
object UpdateChecker {

    data class UpdateInfo(val version: String, val downloadUrl: String, val notes: String)

    private const val LATEST_PATH = "repos/octmicy/xinyuan-app/releases/latest"

    // 直连优先；以下镜像为社区公共加速节点，失效会自动跳到下一个
    private val endpoints = listOf(
        "https://api.github.com/$LATEST_PATH",
        "https://ghfast.top/https://api.github.com/$LATEST_PATH",
        "https://gh-proxy.com/https://api.github.com/$LATEST_PATH",
    )

    /** 拉取最新版本信息；全部通道失败返回 null（视为无更新，不打扰用户） */
    suspend fun checkLatest(): UpdateInfo? = withContext(Dispatchers.IO) {
        endpoints.firstNotNullOfOrNull { ep ->
            runCatching {
                val req = Request.Builder()
                    .url(ep)
                    .header("Accept", "application/vnd.github+json")
                    .build()
                CampusHttp.client.newCall(req).execute().use { resp ->
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
                    UpdateInfo(version, url, obj.optString("body"))
                }
            }.getOrNull()
        }
    }

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
