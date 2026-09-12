package cn.edu.xyc.campus.data.reminder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import cn.edu.xyc.campus.MainActivity
import cn.edu.xyc.campus.R
import cn.edu.xyc.campus.data.local.BorrowStore
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * 图书馆借阅到期提醒：电子证落地后自动同步借阅列表，
 * 有书应还日期在 3 天内（含超期）时发通知。字段宽容解析（服务端样本未完全校准）。
 */
object BorrowReminder {

    private const val CHANNEL_ID = "borrow_due"
    private const val PREFS = "borrow_reminder"
    private const val KEY_LAST = "last_notified_signature"

    fun checkDue(context: Context, raw: String) {
        val entries = BorrowStore.parseEntries(raw)
        if (entries.length() == 0) return
        createChannel(context)

        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Calendar.getInstance().time)
        var warned = 0
        for (i in 0 until entries.length()) {
            val e = entries.optJSONObject(i) ?: continue
            val due = BorrowStore.extractDate(e) ?: continue
            val title = BorrowStore.extractTitle(e).ifEmpty { "馆藏图书" }
            // 宽容判定：due 与今天差值（天）
            val dueMs = runCatching {
                SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(due)?.time
            }.getOrNull() ?: continue
            val daysLeft = ((dueMs - System.currentTimeMillis()) / 86_400_000L).toInt()
            if (daysLeft > 3) continue

            val signature = "$title|$due"
            if (getSignature(context) == signature) continue // 同一本书同一日期只提醒一次
            setSignature(context, signature)
            notify(context, title, due, daysLeft)
            warned++
        }
        android.util.Log.d("XycApp", "借阅提醒：检查 ${entries.length()} 条，临期 $warned 条")
    }

    private fun notify(context: Context, title: String, due: String, daysLeft: Int) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        val text = if (daysLeft < 0) {
            "《$title》已超期 ${-daysLeft} 天，请尽快归还（应还 $due）"
        } else {
            "《$title》还有 $daysLeft 天到期（应还 $due），记得续借或归还"
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
            .setContentTitle("图书馆借阅提醒")
            .setContentText(text)
            .setStyle(android.app.Notification.BigTextStyle().bigText(text))
            .setContentIntent(pi)
            .setAutoCancel(true)
        nm.notify(("borrow" + title + due).hashCode() and 0x7FFFFFFF, builder.build())
    }

    private fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "借阅到期", NotificationManager.IMPORTANCE_DEFAULT)
                    .apply { description = "图书应还日期临近提醒" },
            )
        }
    }

    private fun getSignature(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_LAST, "").orEmpty()

    private fun setSignature(context: Context, value: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_LAST, value).apply()
    }
}
