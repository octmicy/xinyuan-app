package cn.edu.xyc.campus.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Open-Meteo 天气查询（免 key 公开接口）。
 *
 * 固定使用新余坐标（27.81, 114.93），不申请定位权限；查询当前天气与今日最高/最低温。
 * 进程内缓存 30 分钟：窗口内直接返回缓存；请求失败/超时返回 null（调用方隐藏天气卡片），
 * 但不清除旧缓存与时间戳，待窗口过期后由下一次调用重新发起请求。
 */
object WeatherApi {

    /** 一次天气快照：tempC 当前温度（℃），maxC/minC 今日最高/最低温（℃），code 为 WMO 天气码 */
    data class Weather(
        val tempC: Double,
        val maxC: Double,
        val minC: Double,
        val code: Int,
        val desc: String,
    )

    /** 新余市固定坐标（不申请定位权限） */
    private const val URL =
        "https://api.open-meteo.com/v1/forecast" +
            "?latitude=27.81&longitude=114.93&current_weather=true" +
            "&daily=temperature_2m_max,temperature_2m_min&timezone=auto"

    /** 缓存有效期：30 分钟 */
    private const val CACHE_TTL_MS = 30 * 60_000L

    private var cache: Weather? = null
    private var lastFetchAt = 0L

    /**
     * 查询新余当前天气。
     *
     * 先查进程内缓存：距上次成功拉取不足 30 分钟直接命中返回；
     * 否则请求网络，成功后更新缓存与时间戳。
     * 失败/超时返回 null（调用方隐藏卡片），且不清除旧缓存。
     */
    suspend fun fetch(): Weather? = withContext(Dispatchers.IO) {
        val cached = cache
        if (cached != null && System.currentTimeMillis() - lastFetchAt < CACHE_TTL_MS) {
            return@withContext cached
        }
        runCatching {
            // 独立 client：仅天气链路加 8s callTimeout（复用连接池与 CookieJar），弱网快速失败
            val client = CampusHttp.client.newBuilder()
                .callTimeout(8, TimeUnit.SECONDS)
                .build()
            val req = Request.Builder().url(URL).build()
            client.newCall(req).execute().use { resp ->
                val obj = JSONObject(resp.body?.string().orEmpty())
                val current = obj.getJSONObject("current_weather")
                val daily = obj.getJSONObject("daily")
                val code = current.getInt("weathercode")
                Weather(
                    tempC = current.getDouble("temperature"),
                    maxC = daily.getJSONArray("temperature_2m_max").getDouble(0),
                    minC = daily.getJSONArray("temperature_2m_min").getDouble(0),
                    code = code,
                    desc = wmoDesc(code),
                )
            }
        }.getOrNull()?.also {
            cache = it
            lastFetchAt = System.currentTimeMillis()
        }
    }

    /** WMO 天气码 → 中文描述（未识别返回「天气」） */
    fun wmoDesc(code: Int): String = when (code) {
        0 -> "晴"
        1, 2 -> "多云"
        3 -> "阴"
        45, 48 -> "雾"
        in 51..55 -> "毛毛雨"
        in 61..65 -> "雨"
        66, 67 -> "冻雨"
        in 71..77 -> "雪"
        in 80..82 -> "阵雨"
        85, 86 -> "阵雪"
        in 95..99 -> "雷雨"
        else -> "天气"
    }

    /** WMO 天气码 → 对应 emoji（未识别返回通用温度计） */
    fun wmoEmoji(code: Int): String = when (code) {
        0 -> "☀️"
        1, 2 -> "🌤"
        3 -> "☁️"
        45, 48 -> "🌫"
        in 51..55, in 61..65, 66, 67, in 80..82 -> "🌧"
        in 71..77, 85, 86 -> "❄️"
        in 95..99 -> "⛈"
        else -> "🌡"
    }
}
