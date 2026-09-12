package cn.edu.xyc.campus.data.local

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 本地崩溃日志：未捕获异常写入 filesDir/crash/（不上传、不联网）。
 * 下次启动检测到残留时，App 会提示用户「复制反馈并清理」（一键附带进现有反馈模板）。
 * 只保留最近 [MAX_FILES] 条，单条读取时截断，避免撑大存储。
 */
object CrashLog {

    private const val DIR = "crash"
    private const val MAX_FILES = 3

    /** 安装全局崩溃处理器：先落盘再交还系统默认处理（保留系统崩溃行为） */
    fun install(context: Context) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            runCatching { write(context, t, e) } // 崩溃路径上写文件要兜底，防二次异常
            previous?.uncaughtException(t, e)
        }
    }

    private fun write(context: Context, thread: Thread, e: Throwable) {
        val dir = File(context.filesDir, DIR).apply { mkdirs() }
        val ts = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val version = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "?"
        val text = buildString {
            appendLine("时间: $ts")
            appendLine("版本: v$version")
            appendLine("线程: ${thread.name}")
            appendLine("堆栈:")
            appendLine(Log.getStackTraceString(e))
        }
        File(dir, "crash_${System.currentTimeMillis()}.txt").writeText(text)
        // 只保留最近 MAX_FILES 条（文件名含时间戳，倒序即最新在前）
        dir.listFiles()
            ?.sortedByDescending { it.name }
            ?.drop(MAX_FILES)
            ?.forEach { it.delete() }
    }

    /** 待反馈的崩溃日志文件（时间倒序） */
    fun pending(context: Context): List<File> =
        File(context.filesDir, DIR).listFiles()
            ?.sortedByDescending { it.name }
            .orEmpty()

    /** 拼接进反馈文本的崩溃段落；无残留返回 null。单条截断防超大 */
    fun summary(context: Context): String? {
        val files = pending(context)
        if (files.isEmpty()) return null
        return buildString {
            appendLine("【自动附带：上次异常退出日志】")
            files.forEach { f ->
                appendLine(f.readText().take(2000))
            }
        }.trimEnd()
    }

    /** 清理全部崩溃日志（用户选择忽略或已附带反馈后调用） */
    fun clear(context: Context) {
        pending(context).forEach { it.delete() }
    }
}
