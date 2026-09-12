package cn.edu.xyc.campus.data.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** 上课提醒闹钟触发：发通知并滚动规划后续提醒 */
class ClassReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val name = intent.getStringExtra(EXTRA_NAME) ?: return
        val sections = intent.getStringExtra(EXTRA_SECTIONS).orEmpty()
        val room = intent.getStringExtra(EXTRA_ROOM).orEmpty()
        val time = intent.getStringExtra(EXTRA_TIME).orEmpty()

        ClassReminderManager.notifyClass(context, name, "$sections $time $room")
        // 滚动规划，保证未来 48 小时的提醒持续存在
        ClassReminderManager.plan(context)
    }

    companion object {
        const val EXTRA_NAME = "name"
        const val EXTRA_SECTIONS = "sections"
        const val EXTRA_ROOM = "room"
        const val EXTRA_TIME = "time"
    }
}

/** 开机重排提醒闹钟（课程数据从磁盘缓存读取） */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED && ClassReminderManager.isEnabled(context)) {
            ClassReminderManager.plan(context)
        }
    }
}
