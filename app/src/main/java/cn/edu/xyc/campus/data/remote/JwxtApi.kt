package cn.edu.xyc.campus.data.remote

import cn.edu.xyc.campus.data.model.Course
import cn.edu.xyc.campus.data.model.GradeItem
import cn.edu.xyc.campus.data.model.ProfileCard
import cn.edu.xyc.campus.data.model.StudentInfo
import cn.edu.xyc.campus.data.model.WapMenu
import cn.edu.xyc.campus.data.model.WeekInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.Request
import org.json.JSONObject
import java.util.Calendar
import java.util.concurrent.atomic.AtomicBoolean

/** 学期计算（校历接口 Y210501 待接入，先用规则推算） */
object TermUtils {
    data class Term(
        val xnm: String,   // 学年起始年
        val xqm: String,   // 3=第1学期 12=第2学期 16=第3学期
        val termNo: Int,   // 1/2/3
        val label: String, // "2025-2026 第1学期"
    )

    fun current(now: Calendar = Calendar.getInstance()): Term {
        val y = now.get(Calendar.YEAR)
        return when (now.get(Calendar.MONTH) + 1) {
            in 9..12 -> Term(y.toString(), "3", 1, "$y-${y + 1} 第1学期")
            1 -> Term((y - 1).toString(), "3", 1, "${y - 1}-$y 第1学期")
            8 -> Term((y - 1).toString(), "12", 2, "${y - 1}-$y 第2学期")
            else -> Term((y - 1).toString(), "12", 2, "${y - 1}-$y 第2学期")
        }
    }

    fun of(xnm: String, termNo: Int): Term {
        val y = xnm.toIntOrNull() ?: return current()
        val label = "$xnm-${y + 1} 第${termNo}学期"
        return when (termNo) {
            1 -> Term(xnm, "3", 1, label)
            2 -> Term(xnm, "12", 2, label)
            else -> Term(xnm, "16", 3, label)
        }
    }

    fun xnmToLabel(xnm: String): String {
        val y = xnm.toIntOrNull() ?: return xnm
        return "$y-${y + 1}"
    }
}

/** 教务数据结果 */
sealed class JwxtResult<out T> {
    data class Ok<T>(val data: T) : JwxtResult<T>()
    data class SessionExpired(val message: String) : JwxtResult<Nothing>()
    data class Failed(val message: String) : JwxtResult<Nothing>()
}

/**
 * 正方教务系统（zfjwxt.xyc.edu.cn/jwglxt）数据层。
 * 链路经 M1 实测验证（docs/教务接口实测.md）。
 */
object JwxtApi {

    private const val JWXT = "https://zfjwxt.xyc.edu.cn"

    private var ssoDone = false
    private val ssoMutex = kotlinx.coroutines.sync.Mutex()

    fun resetSso() {
        ssoDone = false
    }

    /**
     * 确保教务会话：GET /sso/xyoauthlogin?ticket=<门户token>
     * 落地页 HTML 内联菜单（clickMenu → wapLogin 签名），缓存备用。
     * Mutex 互斥：登录后课表/成绩/我的并发触发时只执行一次 SSO 跳转（ticket 一次性）。
     */
    suspend fun ensureSession(): Boolean = withContext(Dispatchers.IO) {
        ssoMutex.withLock {
            if (ssoDone && SessionStore.jwxtHomeHtml != null) return@withLock true
            val token = SessionStore.token ?: return@withLock false
            try {
                val req = Request.Builder()
                    .url(JWXT + "/sso/xyoauthlogin?ticket=" + token)
                    .header("Referer", PortalApi.BASE + "/mobile/index")
                    .get()
                    .build()
                CampusHttp.client.newCall(req).execute().use { resp ->
                    val html = resp.body?.string().orEmpty()
                    val ok = resp.isSuccessful && resp.request.url.host == CampusHttp.JWXT_HOST &&
                        !resp.request.url.encodedPath.contains("login_slogin")
                    android.util.Log.d(
                        "XycApp",
                        "SSO final=${resp.request.url} code=${resp.code} ok=$ok len=${html.length}",
                    )
                    if (ok) {
                        SessionStore.jwxtHomeHtml = html
                        ssoDone = true
                    }
                    ok
                }
            } catch (t: Throwable) {
                android.util.Log.e("XycApp", "SSO exception", t)
                false
            }
        }
    }

    private suspend fun postForm(url: String, form: Map<String, String>, referer: String): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val body = FormBody.Builder().apply { form.forEach { (k, v) -> add(k, v) } }.build()
                val req = Request.Builder()
                    .url(url)
                    .header("Referer", referer)
                    .header("X-Requested-With", "XMLHttpRequest")
                    .post(body)
                    .build()
                CampusHttp.client.newCall(req).execute().use { resp ->
                    resp.body?.string().orEmpty().also {
                        check(resp.isSuccessful) { "HTTP ${resp.code}" }
                    }
                }
            }
        }

    private suspend fun getMenu(choice: String): WapMenu? = withContext(Dispatchers.IO) {
        // 首页 HTML 未缓存时重新拉一次（签名每次 SSO 会刷新）
        if (SessionStore.jwxtHomeHtml == null && !ensureSession()) return@withContext null
        val html = SessionStore.jwxtHomeHtml ?: return@withContext null
        val re = Regex(
            "clickMenu\\('([^']*)','([^']*)','([^']*)','([^']*)','([^']*)','([^']*)','([^']*)'\\)" +
                "[^>]*title=\"([^\"]*)\""
        )
        re.findAll(html)
            .map { WapMenu(it.groupValues[1], it.groupValues[2], it.groupValues[3], it.groupValues[4],
                it.groupValues[5], it.groupValues[6], it.groupValues[7], it.groupValues[8]) }
            .firstOrNull { it.choice == choice }
    }

    /** 走 wapLogin 签名链打开某功能，返回落地页 URL（并建立该页会话） */
    private suspend fun openWapMenu(menu: WapMenu): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder()
                .url(menu.toUrl(JWXT))
                .header("Referer", JWXT + "/jwglxt/xtgl/index_initMenu.html")
                .get()
                .build()
            CampusHttp.client.newCall(req).execute().use { resp ->
                resp.request.url.toString()
            }
        }
    }

    // ---------------- 课表 + 学籍（旧版移动课表：按周查询） ----------------

    /**
     * 某学期的周次列表。
     * POST /kbcx/xskbcxMobile_cxZc.html {xnm, xqm}
     */
    suspend fun getWeeks(xnm: String, xqm: String): JwxtResult<List<WeekInfo>> {
        if (!ensureSession()) return JwxtResult.SessionExpired("教务会话失效，请重新登录")
        val menu = getMenu("Y253510") ?: return JwxtResult.Failed("未找到课表菜单入口")
        val pageUrl = openWapMenu(menu).getOrElse { return JwxtResult.Failed("打开课表页失败: ${it.message}") }
        val body = postForm(
            JWXT + "/jwglxt/kbcx/xskbcxMobile_cxZc.html",
            mapOf("xnm" to xnm, "xqm" to xqm),
            pageUrl,
        ).getOrElse { return JwxtResult.Failed("网络异常: ${it.message}") }
        return try {
            val arr = org.json.JSONArray(body)
            val list = (0 until arr.length()).mapNotNull { i ->
                val it = arr.optJSONObject(i) ?: return@mapNotNull null
                WeekInfo(zs = it.optInt("zs", 0), rq = it.optString("rq", ""))
            }.filter { it.zs > 0 }
            JwxtResult.Ok(list)
        } catch (t: Throwable) {
            JwxtResult.Failed("解析失败: ${t.message}")
        }
    }

    /**
     * 某周课表（服务器按 zs 返回该周的课）。
     * POST /kbcx/xskbcxMobile_cxXsKb.html {xnm, xqm, zs, doType:"app", kblx:""}
     */
    suspend fun getScheduleByWeek(
        term: TermUtils.Term,
        zs: Int,
    ): JwxtResult<Pair<List<Course>, StudentInfo>> {
        android.util.Log.d("XycApp", "getScheduleByWeek zs=$zs start")
        if (!ensureSession()) return JwxtResult.SessionExpired("教务会话失效，请重新登录")
        android.util.Log.d("XycApp", "getScheduleByWeek zs=$zs session ok")
        val menu = getMenu("Y253510") ?: return JwxtResult.Failed("未找到课表菜单入口")
        android.util.Log.d("XycApp", "getScheduleByWeek zs=$zs menu ok")
        val pageUrl = openWapMenu(menu).getOrElse { return JwxtResult.Failed("打开课表页失败: ${it.message}") }
        android.util.Log.d("XycApp", "getScheduleByWeek zs=$zs wap done")
        val body = postForm(
            JWXT + "/jwglxt/kbcx/xskbcxMobile_cxXsKb.html",
            mapOf(
                "xnm" to term.xnm,
                "xqm" to term.xqm,
                "zs" to zs.toString(),
                "doType" to "app",
                "kblx" to "",
            ),
            pageUrl,
        ).getOrElse { return JwxtResult.Failed("网络异常: ${it.message}") }
        android.util.Log.d("XycApp", "getScheduleByWeek zs=$zs data len=${body.length}")
        return parseKbResponse(body)
    }

    private fun parseKbResponse(body: String): JwxtResult<Pair<List<Course>, StudentInfo>> {
        return try {
            val obj = JSONObject(body)
            val kb = obj.optJSONArray("kbList") ?: org.json.JSONArray()
            // xqj 才是课程的星期（1=周一）；day 字段是响应生成日的星期，全部相同
            val courses = (0 until kb.length()).mapNotNull { i ->
                val it = kb.optJSONObject(i) ?: return@mapNotNull null
                val jc = it.optString("jcor", "1-1").split("-")
                Course(
                    name = it.optString("kcmc", ""),
                    teacher = it.optString("xm", ""),
                    room = it.optString("cdmc", ""),
                    building = it.optString("lh", ""),
                    dayOfWeek = it.optString("xqj", "1").toIntOrNull() ?: 1,
                    startSection = jc.getOrNull(0)?.toIntOrNull() ?: 1,
                    endSection = jc.getOrNull(1)?.toIntOrNull() ?: 1,
                    weekText = it.optString("zcd", ""),
                    credit = it.optString("xf", ""),
                    nature = it.optString("kcxz", ""),
                    classGroup = it.optString("jxbmc", ""),
                )
            }.distinctBy {
                // 合班课同一时段会重复下发多条完全相同的记录，去重
                "${it.dayOfWeek}-${it.startSection}-${it.endSection}-${it.name}-${it.room}"
            }
            val xs = obj.optJSONObject("xsxx") ?: JSONObject()
            val info = StudentInfo(
                name = xs.optString("XM", ""),
                studentId = xs.optString("XH", ""),
                className = xs.optString("BJMC", ""),
                major = xs.optString("ZYMC", ""),
                gradeYear = xs.optString("NJDM_ID", ""),
            )
            JwxtResult.Ok(courses to info)
        } catch (t: Throwable) {
            JwxtResult.Failed("解析失败: ${t.message}")
        }
    }

    /**
     * 学期整表（不带 zs，一次返回整学期全部课程，含 zcd 周次文本）。
     * 参数只带 xnm/xqm——逆向自学校前端 cxXskbcx.js 的学期课表 tab（paramMap3）。
     */
    suspend fun getTermSchedule(xnm: String, xqm: String): JwxtResult<List<Course>> {
        if (!ensureSession()) return JwxtResult.SessionExpired("教务会话失效，请重新登录")
        val menu = getMenu("Y253510") ?: return JwxtResult.Failed("未找到课表菜单入口")
        val pageUrl = openWapMenu(menu).getOrElse { return JwxtResult.Failed("打开课表页失败: ${it.message}") }
        val body = postForm(
            JWXT + "/jwglxt/kbcx/xskbcxMobile_cxXsKb.html",
            mapOf("xnm" to xnm, "xqm" to xqm),
            pageUrl,
        ).getOrElse { return JwxtResult.Failed("网络异常: ${it.message}") }
        return when (val r = parseKbResponse(body)) {
            is JwxtResult.Ok -> JwxtResult.Ok(r.data.first)
            is JwxtResult.SessionExpired -> JwxtResult.SessionExpired(r.message)
            is JwxtResult.Failed -> JwxtResult.Failed(r.message)
        }
    }

    // ---------------- 成绩 ----------------

    /**
     * 某学期成绩。
     * 1) wapLogin(choice=Y305005) 进入移动端成绩页（建立页面会话/Referer）
     * 2) POST /jwglxt/cjcx/cjcxMobile_cxXsgrcj.html?doType=app {xnm, xqm, pkey:""}
     */
    suspend fun getGrades(term: TermUtils.Term): JwxtResult<List<GradeItem>> {
        if (!ensureSession()) return JwxtResult.SessionExpired("教务会话失效，请重新登录")
        val menu = getMenu("Y305005") ?: return JwxtResult.Failed("未找到成绩菜单入口")
        val pageUrl = openWapMenu(menu).getOrElse { return JwxtResult.Failed("打开成绩页失败: ${it.message}") }

        val body = postForm(
            JWXT + "/jwglxt/cjcx/cjcxMobile_cxXsgrcj.html?doType=app",
            mapOf("xnm" to term.xnm, "xqm" to term.xqm, "pkey" to ""),
            pageUrl,
        ).getOrElse { return JwxtResult.Failed("网络异常: ${it.message}") }
        return try {
            val arr = org.json.JSONArray(body)
            val list = (0 until arr.length()).mapNotNull { i ->
                val it = arr.optJSONObject(i) ?: return@mapNotNull null
                GradeItem(
                    termName = it.optString("xnmmc", ""),
                    termNo = it.optInt("xqm", 0),
                    courseName = it.optString("kcmc", ""),
                    courseId = it.optString("kch", ""),
                    nature = it.optString("kcxzmc", ""),
                    category = it.optString("kclbmc", ""),
                    credit = it.optDouble("xf", 0.0),
                    score = it.optString("cj", ""),
                    gradePoint = it.optDouble("jd", 0.0),
                    teacher = it.optString("jsxm", ""),
                    school = it.optString("kkbmmc", ""),
                    pass = it.optString("cjsfzf", "否") == "否",
                )
            }
            JwxtResult.Ok(list)
        } catch (t: Throwable) {
            JwxtResult.Failed("解析失败: ${t.message}")
        }
    }

    // ---------------- 学籍卡 ----------------
    suspend fun getProfile(): JwxtResult<ProfileCard> {
        val term = TermUtils.current()
        return when (val r = getScheduleByWeek(term, 1)) {
            is JwxtResult.Ok -> {
                val grades = getGrades(term)
                val college = (grades as? JwxtResult.Ok)?.data?.firstOrNull()?.school.orEmpty()
                JwxtResult.Ok(ProfileCard(r.data.second, college))
            }
            is JwxtResult.SessionExpired -> JwxtResult.SessionExpired(r.message)
            is JwxtResult.Failed -> JwxtResult.Failed(r.message)
        }
    }
}
