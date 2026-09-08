package cn.edu.xyc.campus.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider as DayNightProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.unit.ColorProvider
import cn.edu.xyc.campus.MainActivity
import cn.edu.xyc.campus.R
import cn.edu.xyc.campus.data.local.ThemeStore
import cn.edu.xyc.campus.data.remote.CredentialLauncher
import cn.edu.xyc.campus.ui.theme.ThemeModeStore

/**
 * 「图书馆电子证」小组件：桌面一键直达电子证（应用内 WebView 打开门户 ticket 链 + SPA 自动落地）。
 * 1×1 小格（可缩放），仅图标撑满，点击发 Intent extra → MainActivity → CredentialLauncher 事件 → AppRoot 弹 WebView。
 */
class CredentialWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        ThemeStore.init(context) // 主题包配色
        ThemeModeStore.init(context) // 深浅色偏好（桌面触发渲染时进程里没跑过 MainActivity）
        provideContent { Content(context) }
    }

    @Composable
    private fun Content(context: Context) {
        val colors = wColors(context)
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(colors.bg)
                .cornerRadius(16.dp)
                .clickable(
                    // glance 1.1.1 无泛型 actionStartActivity：直接带 Intent extra 启动 MainActivity
                    actionStartActivity(
                        Intent(context, MainActivity::class.java).apply {
                            putExtra(CredentialLauncher.EXTRA_OPEN_CREDENTIAL, true)
                        },
                    ),
                )
                .padding(6.dp),
            contentAlignment = Alignment.Center,
        ) {
            // 1×1 小格：仅图标撑满，点击直达电子证
            Image(
                provider = ImageProvider(R.drawable.app_library),
                contentDescription = "图书馆电子证",
                modifier = GlanceModifier.fillMaxSize(),
            )
        }
    }

    /** 配色：主题包 widget* 色优先，否则日夜双色（同 TodayWidget 口径） */
    private fun wColors(context: Context): CColors {
        fun c(key: String, day: String, night: String): ColorProvider {
            val themed = ThemeStore.color(key, "")
            if (themed.isNotEmpty()) {
                return ColorProvider(Color(android.graphics.Color.parseColor(themed)))
            }
            val d = Color(android.graphics.Color.parseColor(day))
            val n = Color(android.graphics.Color.parseColor(night))
            return when (ThemeModeStore.mode.value) {
                ThemeModeStore.Mode.LIGHT -> ColorProvider(d)
                ThemeModeStore.Mode.DARK -> ColorProvider(n)
                ThemeModeStore.Mode.FOLLOW -> DayNightProvider(d, n)
            }
        }
        return CColors(
            bg = c("widgetBg", "#E8F1FF", "#171C25"),
            primary = c("widgetPrimary", "#1D3F8C", "#ADC6FF"),
        )
    }
}

private data class CColors(val bg: ColorProvider, val primary: ColorProvider)

/** 小组件接收器 */
class CredentialWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CredentialWidget()
}
