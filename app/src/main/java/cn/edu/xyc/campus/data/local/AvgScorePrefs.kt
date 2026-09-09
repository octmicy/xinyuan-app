package cn.edu.xyc.campus.data.local

import android.content.Context

/**
 * 平均学分绩（GPA）计算中「参与科目的勾选状态」按学年持久化。
 *
 * 平均学分绩页面里每门课都可勾选/取消参与计算，取消勾选的课记录在本文件，
 * 按学年（yearKey 形如 "2025-2026"）分开存储，切学年互不影响。
 *
 * 语义：某学年无记录 = 默认全部参与（全选）；记录的集合 = 被取消勾选的课程 key
 * （courseId 或 courseName，由调用方决定）。集合存 SharedPreferences 的 StringSet，
 * key 加 "excluded_" 前缀区分。
 */
object AvgScorePrefs {

    private const val PREFS = "avg_score_prefs"
    private const val PREFIX = "excluded_"
    private const val KEY_CARD_HIDDEN = "card_hidden"

    private fun keyOf(yearKey: String) = "$PREFIX$yearKey"

    /**
     * 读取某学年被取消勾选的课程 key 集合（courseId 或 courseName）；
     * 无记录返回空集 = 默认全选。
     *
     * 注意：getStringSet 返回的是 prefs 内部引用，这里拷贝成 HashSet 返回，
     * 避免调用方原地修改污染内部状态导致后续写入失效（Android 已知坑）。
     */
    fun getExcluded(context: Context, yearKey: String): Set<String> {
        val stored = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getStringSet(keyOf(yearKey), null) ?: return emptySet()
        return HashSet(stored)
    }

    /** 保存某学年取消勾选集合；空集合时清除该学年记录（视为全选）。 */
    fun setExcluded(context: Context, yearKey: String, keys: Set<String>) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        if (keys.isEmpty()) {
            editor.remove(keyOf(yearKey))
        } else {
            // 传入全新集合写入，避免与内部集合引用相同导致 apply 不落盘
            editor.putStringSet(keyOf(yearKey), HashSet(keys))
        }
        editor.apply()
    }

    /** 成绩页「平均学分绩」卡片是否被用户隐藏（全局开关，默认显示）。 */
    fun isCardHidden(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_CARD_HIDDEN, false)

    fun setCardHidden(context: Context, hidden: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_CARD_HIDDEN, hidden).apply()
    }
}
