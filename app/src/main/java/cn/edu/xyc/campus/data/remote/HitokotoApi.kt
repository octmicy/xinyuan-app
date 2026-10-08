package cn.edu.xyc.campus.data.remote

import cn.edu.xyc.campus.data.local.Quote
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * 「一言」公益 API（https://v1.hitokoto.cn，免 key）动画分类名句。
 *
 * 弱网/接口不可用时快速失败返回 null（调用方转 QuoteStore 本地兜底）；
 * 连续失败达到 2 次后熔断，后续调用直接返回 null，避免每次进首页都白等 8 秒，
 * 直到下一次成功才清零恢复请求。
 */
object HitokotoApi {

    private const val URL = "https://v1.hitokoto.cn/?c=a&max_length=40"

    /** 连续失败达到该次数后熔断（直接返回 null） */
    private const val FAIL_LIMIT = 2

    private var failCount = 0

    /**
     * 拉取一条动画名句。
     *
     * 熔断中（连续失败 ≥2 次）或请求失败/超时返回 null（调用方转本地随机）；
     * 成功时失败计数清零。
     */
    suspend fun fetch(): Quote? = withContext(Dispatchers.IO) {
        if (failCount >= FAIL_LIMIT) return@withContext null
        runCatching {
            // 独立 client：仅一言链路加 8s callTimeout（复用连接池与 CookieJar），弱网快速失败
            val client = CampusHttp.client.newBuilder()
                .callTimeout(8, TimeUnit.SECONDS)
                .build()
            val req = Request.Builder().url(URL).build()
            client.newCall(req).execute().use { resp ->
                val obj = JSONObject(resp.body?.string().orEmpty())
                val content = obj.optString("hitokoto")
                val from = obj.optString("from")
                Quote(
                    content = content,
                    source = if (from.isNotBlank()) from else "一言",
                )
            }
        }.getOrNull()?.also { failCount = 0 } ?: run {
            failCount++
            null
        }
    }
}
