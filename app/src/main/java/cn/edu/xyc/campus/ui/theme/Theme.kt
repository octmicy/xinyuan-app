package cn.edu.xyc.campus.ui.theme

import android.content.Context
import android.os.Build
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import cn.edu.xyc.campus.data.local.ThemeStore

/** 应用内深浅色偏好：跟随系统 / 强制浅色 / 强制深色（应用内选择优先于系统夜间模式） */
object ThemeModeStore {

    enum class Mode { FOLLOW, LIGHT, DARK }

    private const val PREFS = "theme_mode"
    private const val KEY = "mode"

    /** 当前偏好（状态驱动：切换后全局即时重组） */
    val mode = mutableStateOf(Mode.FOLLOW)

    fun init(context: Context) {
        val saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(KEY, Mode.FOLLOW.ordinal)
        mode.value = Mode.entries.getOrElse(saved) { Mode.FOLLOW }
    }

    fun set(context: Context, m: Mode) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putInt(KEY, m.ordinal).apply()
        mode.value = m
    }

    /** 解析后的实际深色态（FOLLOW 时跟随系统；供状态栏样式/小组件配色取用） */
    fun resolvedDark(context: Context, systemDark: Boolean = isSystemDark(context)): Boolean =
        when (mode.value) {
            Mode.LIGHT -> false
            Mode.DARK -> true
            Mode.FOLLOW -> systemDark
        }

    fun isSystemDark(context: Context): Boolean =
        (context.resources.configuration.uiMode and
            android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
}

private fun themeColor(theme: ThemeStore.ThemeConfig?, key: String, fallback: Color): Color =
    theme?.colors?.get(key)?.let { hex ->
        runCatching { Color(AndroidColor.parseColor(hex)) }.getOrNull()
    } ?: fallback

private fun lightScheme(theme: ThemeStore.ThemeConfig?) = lightColorScheme(
    primary = themeColor(theme, "primary", XycBlue),
    onPrimary = themeColor(theme, "onPrimary", Color.White),
    primaryContainer = themeColor(theme, "primaryContainer", XycBlueContainer),
    onPrimaryContainer = themeColor(theme, "onPrimaryContainer", XycBlueDark),
    secondaryContainer = themeColor(theme, "secondaryContainer", XycBlueContainer),
    onSecondaryContainer = themeColor(theme, "onSecondaryContainer", XycBlueDark),
    background = themeColor(theme, "background", Color.White),
)

private val LightColors = lightScheme(null)

/** 深色配色映射：延续校色蓝的深色变体（主题包 colors 仅作用于浅色模式） */
private val DarkColors = darkColorScheme(
    primary = Color(0xFFADC6FF),
    onPrimary = Color(0xFF0F3866),
    primaryContainer = Color(0xFF2A4A80),
    onPrimaryContainer = Color(0xFFD9E6FF),
    secondaryContainer = Color(0xFF26364F),
    onSecondaryContainer = Color(0xFFD9E6FF),
    background = Color(0xFF10151D),
    onBackground = Color(0xFFE1E7F1),
    surface = Color(0xFF10151D),
    onSurface = Color(0xFFE1E7F1),
    surfaceVariant = Color(0xFF232C3A),
    onSurfaceVariant = Color(0xFF9FAEC7),
    outline = Color(0xFF55637D),
    outlineVariant = Color(0xFF2C3746),
)

/** 应用内实际生效的深色态（档位优先于系统夜间模式；所有配色判断都应使用此函数） */
@Composable
fun isAppDarkTheme(): Boolean = when (ThemeModeStore.mode.value) {
    ThemeModeStore.Mode.LIGHT -> false
    ThemeModeStore.Mode.DARK -> true
    ThemeModeStore.Mode.FOLLOW -> isSystemInDarkTheme()
}

@Composable
fun XycCampusTheme(
    darkTheme: Boolean = isAppDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    // 主题包激活时动态取配色（ThemeStore.active 为状态，导入/恢复即时重组）
    val lightColors = lightScheme(ThemeStore.active.value)
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> lightColors
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        content = content,
    )
}
