package cn.edu.xyc.campus.data.local

import android.content.Context

/**
 * 学生学院名称本地持久化（SharedPreferences）。
 *
 * 学院名来自学工系统（XgApi.fetchCollege），拉取成功后缓存到本机，
 * 之后「我的」页面直接读取，避免每次进入都请求学工。空字符串 = 没存过。
 */
object CollegeStore {

    private const val PREFS = "college_store"
    private const val KEY = "college"

    /** 读取已缓存的学院名称；没存过返回空字符串。 */
    fun get(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, "").orEmpty()

    /** 保存学院名称；传空串时删除记录（避免存空值）。 */
    fun set(context: Context, college: String) {
        val editor = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        if (college.isBlank()) editor.remove(KEY) else editor.putString(KEY, college)
        editor.apply()
    }
}