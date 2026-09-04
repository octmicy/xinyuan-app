package cn.edu.xyc.campus.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = XycBlue,
    onPrimary = Color.White,
    primaryContainer = XycBlueContainer,
    onPrimaryContainer = XycBlueDark,
    secondaryContainer = XycBlueContainer,
    onSecondaryContainer = XycBlueDark,
)

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
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        content = content,
    )
}
