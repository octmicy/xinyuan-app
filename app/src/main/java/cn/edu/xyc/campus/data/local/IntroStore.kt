package cn.edu.xyc.campus.data.local

import android.content.Context

/** 一次性引导标记（新手引导只在首次打开出现） */
object IntroStore {
    private const val PREFS = "app_flags"
    private const val KEY = "intro_done"

    fun isDone(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY, false)

    fun setDone(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY, true).apply()
    }
}
