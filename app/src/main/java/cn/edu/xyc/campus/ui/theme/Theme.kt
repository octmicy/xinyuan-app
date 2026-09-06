package cn.edu.xyc.campus.ui.theme

import android.os.Build
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import cn.edu.xyc.campus.data.local.ThemeStore

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

private val DarkColors = darkColorScheme(
    primary = Color(0xFFADC6FF),
    onPrimary = Color(0xFF0F3866),
    primaryContainer = Color(0xFF2A4A80),
    onPrimaryContainer = Color(0xFFD6E3FF),
    secondaryContainer = Color(0xFF2A4A80),
    onSecondaryContainer = Color(0xFFD6E3FF),
)

@Composable
fun XycCampusTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
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
