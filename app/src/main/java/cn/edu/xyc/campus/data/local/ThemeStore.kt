package cn.edu.xyc.campus.data.local

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.mutableStateOf
import java.io.File
import java.util.zip.ZipInputStream

/**
 * 主题系统：
 * 主题 = 一个 zip 包，内含 theme.json（配色 + 图标映射）与若干 png 图标文件。
 * 导入后解压到 filesDir/themes/<name>/，active.json 记录当前主题；恢复默认即删除该文件。
 *
 * theme.json 格式（version 必须=1，colors/icons 均可只写要覆盖的项）：
 * ```json
 * {
 *   "name": "我的主题",
 *   "author": "octmicy",
 *   "version": 1,
 *   "colors": {
 *     "primary": "#1E5AA8",
 *     "onPrimary": "#FFFFFF",
 *     "primaryContainer": "#D6E3FF",
 *     "onPrimaryContainer": "#0F3866",
 *     "secondaryContainer": "#D6E3FF",
 *     "onSecondaryContainer": "#0F3866",
 *     "background": "#FFFFFF",
 *     "widgetBg": "#E8F1FF",
 *     "widgetCard": "#FFFFFF",
 *     "widgetText": "#22304D",
 *     "widgetPrimary": "#1D3F8C",
 *     "widgetSecondary": "#6B7B99"
 *   },
 *   "icons": {
 *     "nav_schedule": "nav_schedule.png",
 *     "nav_grades": "nav_grades.png",
 *     "nav_apps": "nav_apps.png",
 *     "nav_leave": "nav_leave.png",
 *     "nav_profile": "nav_profile.png",
 *     "login_logo": "login_logo.png",
 *     "avatar_default": "avatar_default.png"
 *   }
 * }
 * ```
 */
object ThemeStore {

    const val FORMAT_VERSION = 1

    data class ThemeConfig(
        val name: String,
        val author: String,
        val colors: Map<String, String>,
        val icons: Map<String, String>,
        val dir: File,
    )

    /** 当前主题（null = 内置默认），状态驱动：导入/恢复后全局即时生效 */
    val active = mutableStateOf<ThemeConfig?>(null)

    private const val ACTIVE_FILE = "theme_active.json"

    fun init(context: Context) {
        synchronized(active) {
            if (active.value != null) return
            runCatching {
                val f = File(context.filesDir, ACTIVE_FILE)
                if (f.exists()) {
                    val obj = org.json.JSONObject(f.readText())
                    val dir = File(obj.getString("dir"))
                    if (dir.isDirectory) {
                        val colors = mutableMapOf<String, String>()
                        obj.optJSONObject("colors")?.let { c ->
                            c.keys().forEach { k -> colors[k] = c.getString(k) }
                        }
                        val icons = mutableMapOf<String, String>()
                        obj.optJSONObject("icons")?.let { c ->
                            c.keys().forEach { k -> icons[k] = c.getString(k) }
                        }
                        active.value = ThemeConfig(
                            name = obj.optString("name"),
                            author = obj.optString("author"),
                            colors = colors,
                            icons = icons,
                            dir = dir,
                        )
                    }
                }
            }
        }
    }

    /** 主题生效的取色入口：无覆盖时返回 fallback */
    fun color(key: String, fallback: String): String =
        active.value?.colors?.get(key) ?: fallback

    /** 图标覆盖文件（不存在或未覆盖返回 null，调用方回退内置资源） */
    fun iconFile(key: String): File? {
        val theme = active.value ?: return null
        val name = theme.icons[key] ?: return null
        val f = File(theme.dir, name)
        return if (f.exists()) f else null
    }

    fun resetDefault(context: Context) {
        runCatching { File(context.filesDir, ACTIVE_FILE).delete() }
        active.value = null
    }

    /** 从 zip 导入主题，成功返回主题名 */
    fun importZip(context: Context, uri: Uri): Result<String> = runCatching {
        var meta: org.json.JSONObject? = null
        val extracted = mutableMapOf<String, ByteArray>()
        context.contentResolver.openInputStream(uri)?.use { input ->
            ZipInputStream(input.buffered()).use { zip ->
                var entry = zip.nextEntry
                var count = 0
                while (entry != null) {
                    if (++count > 64) error("主题包文件过多")
                    val name = entry.name.substringAfterLast('/') // 忽略目录层级，扁平化
                    if (name.isEmpty() || name.contains("..")) { entry = zip.nextEntry; continue }
                    val bytes = zip.readBytes()
                    if (bytes.size > 6 * 1024 * 1024) error("主题包单文件超过 6MB")
                    when {
                        name == "theme.json" -> meta = org.json.JSONObject(String(bytes))
                        name.substringAfterLast('.') in setOf("png", "jpg", "jpeg", "webp") ->
                            extracted[name] = bytes
                    }
                    entry = zip.nextEntry
                }
            }
        } ?: error("无法读取所选文件")

        val themeJson = meta ?: error("主题包缺少 theme.json")
        if (themeJson.optInt("version") != FORMAT_VERSION) error("主题格式版本不支持")
        val name = themeJson.optString("name").ifEmpty { error("theme.json 缺少 name") }

        // 过滤出 icons 里引用的文件并校验存在
        val icons = mutableMapOf<String, String>()
        themeJson.optJSONObject("icons")?.let { obj ->
            obj.keys().forEach { k ->
                val file = obj.getString(k)
                if (extracted.containsKey(file)) icons[k] = file
            }
        }
        val colors = mutableMapOf<String, String>()
        themeJson.optJSONObject("colors")?.let { obj ->
            obj.keys().forEach { k -> colors[k] = obj.getString(k) }
        }

        // 落盘
        val safeName = name.replace(Regex("[^\\u4e00-\\u9fa5A-Za-z0-9_-]"), "_").take(32)
        val dir = File(File(context.filesDir, "themes"), safeName).apply { mkdirs() }
        extracted.filterKeys { it in icons.values }.forEach { (fileName, bytes) ->
            File(dir, fileName).writeBytes(bytes)
        }

        // 写 active.json
        val activeObj = org.json.JSONObject()
            .put("dir", dir.absolutePath)
            .put("name", name)
            .put("author", themeJson.optString("author"))
            .put("colors", org.json.JSONObject(colors))
            .put("icons", org.json.JSONObject(icons))
        File(context.filesDir, ACTIVE_FILE).writeText(activeObj.toString())

        synchronized(active) {
            active.value = ThemeConfig(
                name = name,
                author = themeJson.optString("author"),
                colors = colors,
                icons = icons,
                dir = dir,
            )
        }
        name
    }
}
