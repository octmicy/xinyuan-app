package cn.edu.xyc.campus.data.local

import android.content.Context
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

/**
 * 图书馆借阅数据：电子证登录落地后由 WebView 内 fetch /find/loanInfo/loanList 取回并落盘。
 * 服务端返回结构未完全校准（当前样本为空 {}），故存原始 JSON、解析时做宽容处理。
 */
object BorrowStore {

    private const val FILE = "library_borrows.json"

    /** 保存原始响应文本（list 接口的 data 部分） */
    fun save(context: Context, raw: String) {
        runCatching {
            File(context.filesDir, FILE).writeText(raw)
        }
    }

    /** 读取原始响应文本（无则返回 null） */
    fun loadRaw(context: Context): String? {
        val f = File(context.filesDir, FILE)
        return if (f.exists()) f.readText() else null
    }

    /**
     * 宽容解析出条目列表：兼容 data 为数组或 {list:[...]} 两种形态；
     * 解析失败返回空数组（等真实样本出现后再精修字段）。
     */
    fun parseEntries(raw: String?): JSONArray {
        if (raw.isNullOrBlank()) return JSONArray()
        return runCatching {
            val obj = JSONObject(raw)
            when {
                obj.has("data") -> {
                    val d = obj.get("data")
                    when (d) {
                        is JSONArray -> d
                        is JSONObject -> d.optJSONArray("list") ?: d.optJSONArray("rows") ?: JSONArray()
                        else -> JSONArray()
                    }
                }
                else -> JSONArray()
            }
        }.getOrDefault(JSONArray())
    }

    /** 从条目对象里宽容提取一个日期串（yyyy-MM-dd 开头），用于应还日期猜测 */
    fun extractDate(entry: JSONObject): String? {
        val keys = entry.keys()
        for (k in keys) {
            val v = entry.optString(k)
            val m = Regex("(\\d{4}-\\d{2}-\\d{2})").find(v)
            if (m != null) return m.groupValues[1]
        }
        return null
    }

    /** 从条目对象里宽容提取书名：取最长的非日期字符串字段 */
    fun extractTitle(entry: JSONObject): String {
        var best = ""
        val keys = entry.keys()
        for (k in keys) {
            val v = entry.optString(k)
            if (v.length > best.length && !v.contains(Regex("\\d{4}-\\d{2}-\\d{2}")) && !v.contains("http")) {
                best = v
            }
        }
        return best
    }
}
