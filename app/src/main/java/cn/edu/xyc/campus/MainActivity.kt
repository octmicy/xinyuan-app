package cn.edu.xyc.campus

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import cn.edu.xyc.campus.data.local.CredStore
import cn.edu.xyc.campus.data.local.CustomCourseStore
import cn.edu.xyc.campus.data.local.ScheduleCache
import cn.edu.xyc.campus.data.local.ThemeStore
import cn.edu.xyc.campus.ui.AppRoot
import cn.edu.xyc.campus.ui.theme.ThemeModeStore
import cn.edu.xyc.campus.ui.theme.XycCampusTheme
import cn.edu.xyc.campus.widget.TodayWidget
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            // debug 构建允许 chrome://inspect 远程调试 WebView（排障用，release 不启用）
            android.webkit.WebView.setWebContentsDebuggingEnabled(true)
        }
        CredStore.init(this)
        CustomCourseStore.init(this)
        ThemeStore.init(this)
        ScheduleCache.init(this)
        enableEdgeToEdge()
        // 打开 App 即重渲染小组件（读磁盘缓存，覆盖跨天/静默登录后的数据翻转）
        lifecycleScope.launch {
            runCatching { TodayWidget().updateAll(this@MainActivity) }
        }
        setContent {
            XycCampusTheme {
                AppRoot()
            }
        }
    }
}
