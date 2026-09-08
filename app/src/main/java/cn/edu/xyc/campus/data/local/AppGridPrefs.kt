package cn.edu.xyc.campus.data.local

import android.content.Context

/**
 * 应用宫格偏好持久化（SharedPreferences，进程被杀后恢复）：
 * - order：宫格显示顺序（portalName 有序列表，未自定义时按内置顺序）
 * - hidden：被隐藏（关闭）的应用集合
 */
object AppGridPrefs {

    private const val PREFS = "app_grid_prefs"
    private const val KEY_ORDER = "order"
    private const val KEY_HIDDEN = "hidden"
    private const val SEP = "\u0001" // portalName 不可能出现的分隔符

    /** 宫格顺序；无记录返回 null = 未自定义排序 */
    fun getOrder(context: Context): List<String>? {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_ORDER, null) ?: return null
        if (raw.isEmpty()) return null
        return raw.split(SEP).filter { it.isNotEmpty() }
    }

    fun setOrder(context: Context, order: List<String>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_ORDER, order.joinToString(SEP)).apply()
    }

    /** 被隐藏的应用 key 集合；空集 = 全部显示 */
    fun getHidden(context: Context): Set<String> = HashSet(
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getStringSet(KEY_HIDDEN, emptySet()) ?: emptySet(),
    )

    fun setHidden(context: Context, keys: Set<String>) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit().apply {
            if (keys.isEmpty()) remove(KEY_HIDDEN) else putStringSet(KEY_HIDDEN, HashSet(keys))
        }.apply()
    }
}
