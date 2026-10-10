package cn.edu.xyc.campus.data.local

import android.content.Context

/**
 * 首页快捷入口显隐（启用集合）持久化。
 *
 * 首页提供 5 个快捷入口（图书馆电子证、请假申请、教务系统、学工系统、网络教学），
 * 用户可自定义显示哪些；启用集合以 SharedPreferences StringSet 存储。
 *
 * 语义：无记录（getEnabled 返回 null）= 用户从未自定义，调用方使用默认前 3 个
 * （见 [DEFAULT_ENTRIES]）；记录为空集 = 用户明确全部关闭（不删 key，与「从未自定义」区分）。
 */
object HomePrefs {

    /** 图书馆电子证 */
    const val ENTRY_LIBRARY = "library"

    /** 请假申请 */
    const val ENTRY_LEAVE = "leave"

    /** 教务系统 */
    const val ENTRY_JWXT = "jwxt"

    /** 学工系统 */
    const val ENTRY_XG = "xg"

    /** 网络教学 */
    const val ENTRY_ONLINE = "online"

    /** 默认展示的快捷入口（用户从未自定义时的前 3 个：电子证/请假申请/教务系统） */
    val DEFAULT_ENTRIES = setOf(ENTRY_LIBRARY, ENTRY_LEAVE, ENTRY_JWXT)

    private const val PREFS = "home_prefs"
    private const val KEY_ENABLED = "enabled_entries"

    /**
     * 读取用户启用的快捷入口 key 集合；null = 用户从未自定义（调用方使用 [DEFAULT_ENTRIES]）。
     *
     * 注意：getStringSet 返回的是 prefs 内部引用，这里拷贝成 HashSet 返回，
     * 避免调用方原地修改污染内部状态导致后续写入失效（Android 已知坑）。
     */
    fun getEnabled(context: Context): Set<String>? {
        val stored = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getStringSet(KEY_ENABLED, null) ?: return null
        return HashSet(stored)
    }

    /**
     * 保存用户启用的快捷入口集合。
     * 空集合也原样存入（不删 key）：语义 = 用户明确全部关闭，而非「从未自定义」。
     */
    fun setEnabled(context: Context, keys: Set<String>) {
        // 传入全新集合写入，避免与内部集合引用相同导致 apply 不落盘
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putStringSet(KEY_ENABLED, HashSet(keys)).apply()
    }
}
