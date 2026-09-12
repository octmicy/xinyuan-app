package cn.edu.xyc.campus.data.reminder

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import cn.edu.xyc.campus.MainActivity
import cn.edu.xyc.campus.R
import cn.edu.xyc.campus.data.local.CustomCourseStore
import cn.edu.xyc.campus.data.local.ScheduleCache
import cn.edu.xyc.campus.data.model.Course
import cn.edu.xyc.campus.data.model.SectionTimes
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * 上课提醒（系统通知版）：基于落盘课表（周次+课表，杀后台后仍可读）+ 自定义课 + 作息表，
 * 为未来 48 小时内的每节课设置上课前 X 分钟的提醒闹钟（优先精确，降级非精确）。
 * 规划时机：App 打开 / 收到提醒后 / 开机——滚动覆盖，无需用户每天打开。
 * 与小组件的课前提醒态互补：小组件要用户看桌面才可见，通知是主动触达。
 */
object ClassReminderManager {

    private const val PREFS = "class_reminder"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_ADVANCE = "advance_minutes"
    private const val CHANNEL_ID = "class_reminder"
    private const val REQUEST_BASE = 3100

    private data class WeekRef(val key: String, val zs: Int)

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_ENABLED, enabled).apply()
        if (enabled) plan(context) else cancelAll(context)
    }

    fun advanceMinutes(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_ADVANCE, 10)

    fun setAdvanceMinutes(context: Context, minutes: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putInt(KEY_ADVANCE, minutes).apply()
        if (isEnabled(context)) plan(context)
    }

    fun cancelAll(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        for (i in 0 until 32) {
            am.cancel(pendingIntent(context, i, "", "", "", ""))
        }
    }

    /** 规划未来 48 小时内的课程提醒（先清后设，上限 30 个） */
    fun plan(context: Context) {
        if (!isEnabled(context)) return
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        cancelAll(context)

        val advance = advanceMinutes(context)
        val now = System.currentTimeMillis()
        val dayFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val base = Calendar.getInstance()
        var index = 0

        for (offset in 0..2) {
            val day = (base.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, offset) }
            val courses = coursesFor(context, day) ?: continue
            for (c in courses) {
                val table = SectionTimes.tableFor(c.room)
                val sec = table.getOrNull(c.startSection - 1) ?: continue
                val parts = sec.start.split(":")
                val hh = parts.getOrNull(0)?.toIntOrNull() ?: continue
                val mm = parts.getOrNull(1)?.toIntOrNull() ?: continue
                val dayCal = (day.clone() as Calendar).apply {
                    set(Calendar.HOUR_OF_DAY, hh)
                    set(Calendar.MINUTE, mm)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                val alarmAt = dayCal.timeInMillis - advance * 60_000L
                if (alarmAt <= now || index >= 30) continue

                val timeText = "上课时间 ${sec.start}，已提前 $advance 分钟提醒"
                val pi = pendingIntent(
                    context, index, c.name,
                    "${c.startSection}-${c.endSection}节", "@${c.room}", timeText,
                )
                val exact = if (Build.VERSION.SDK_INT >= 31) am.canScheduleExactAlarms() else true
                if (exact) {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, alarmAt, pi)
                } else {
                    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, alarmAt, pi)
                }
                index++
            }
        }
        android.util.Log.d("XycApp", "课程提醒：已规划 $index 个闹钟")
    }

    /** 某天的课程（教务周课表 + 自定义课，按该日星期与单双周过滤），找不到所在教学周返回 null */
    private fun coursesFor(context: Context, day: Calendar): List<Course>? {
        val dayFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val dayStr = dayFmt.format(day.time)
        // xqj: 1=周一 … 7=周日；Calendar.DAY_OF_WEEK: 周日=1
        val xqj = if (day.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY) 7 else day.get(Calendar.DAY_OF_WEEK) - 1

        var weekKey: String? = null
        var zs = 0
        ScheduleCache.weeksList.forEach { (key, list) ->
            if (weekKey != null) return@forEach
            list.forEach { w ->
                val mondayStr = w.rq.substringBefore('/').trim()
                if (mondayStr <= dayStr && dayStr <= plusDays(mondayStr, 6)) {
                    weekKey = key
                    zs = w.zs
                }
            }
        }
        val wk = weekKey ?: return null
        val dayKey = "D:${wk.removePrefix("W:")}-$zs"

        val base = ScheduleCache.weekData[dayKey]?.first ?: emptyList()
        val customs = CustomCourseStore.readForWeek(context, zs)
        return (base + customs).filter { it.dayOfWeek == xqj }
            .sortedWith(compareBy({ it.startSection }, { it.name }))
    }

    private fun plusDays(dateStr: String, days: Int): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val d = runCatching { fmt.parse(dateStr) }.getOrNull() ?: return dateStr
        val cal = Calendar.getInstance().apply { time = d; add(Calendar.DAY_OF_YEAR, days) }
        return fmt.format(cal.time)
    }

    private fun pendingIntent(
        context: Context,
        requestCode: Int,
        name: String,
        sections: String,
        room: String,
        time: String,
    ): PendingIntent {
        val intent = Intent(context, ClassReminderReceiver::class.java).apply {
            putExtra(ClassReminderReceiver.EXTRA_NAME, name)
            putExtra(ClassReminderReceiver.EXTRA_SECTIONS, sections)
            putExtra(ClassReminderReceiver.EXTRA_ROOM, room)
            putExtra(ClassReminderReceiver.EXTRA_TIME, time)
        }
        return PendingIntent.getBroadcast(
            context,
            REQUEST_BASE + requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /** 发送上課提醒通知（Receiver 调用） */
    fun notifyClass(context: Context, name: String, detail: String) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "上课提醒",
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply { description = "上课前提醒" },
            )
        }
        val pi = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = if (Build.VERSION.SDK_INT >= 26) {
            android.app.Notification.Builder(context, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            android.app.Notification.Builder(context)
        }
        builder
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("快上课啦")
            .setContentText("$name · $detail")
            .setStyle(android.app.Notification.BigTextStyle().bigText("$name\n$detail"))
            .setContentIntent(pi)
            .setAutoCancel(true)
        nm.notify((REQUEST_BASE + name.hashCode()) and 0x7FFFFFFF, builder.build())
    }
}
