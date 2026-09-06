package cn.edu.xyc.campus.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.runtime.getValue
import cn.edu.xyc.campus.data.local.ThemeStore

/**
 * 支持主题覆盖的图片：主题包里配置了对应 key 的图标文件就用它，否则回退内置资源。
 * key 见 ThemeStore 文档（nav_schedule / login_logo / avatar_default 等）。
 */
@Composable
fun ThemeImage(
    key: String,
    resId: Int,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    contentScale: ContentScale = ContentScale.Fit,
) {
    val theme by ThemeStore.active
    val context = LocalContext.current
    val bmp = remember(key, theme) {
        theme?.let {
            ThemeStore.iconFile(key)?.let { f ->
                runCatching { BitmapFactory.decodeFile(f.absolutePath) }.getOrNull()
            }
        }
    }
    if (bmp != null) {
        Image(
            bitmap = bmp.asImageBitmap(),
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = contentScale,
        )
    } else {
        Image(
            painter = painterResource(resId),
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = contentScale,
        )
    }
}
