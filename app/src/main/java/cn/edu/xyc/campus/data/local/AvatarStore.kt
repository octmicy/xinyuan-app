package cn.edu.xyc.campus.data.local

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 用户自定义头像：中心裁方形 → 缩放 512px → JPEG 存 filesDir/avatar.jpg。
 * 属于本机个性化偏好，退出登录不清除。
 */
object AvatarStore {

    private const val FILE = "avatar.jpg"

    fun load(context: Context): Bitmap? = runCatching {
        val f = File(context.filesDir, FILE)
        if (!f.exists()) null else BitmapFactory.decodeFile(f.absolutePath)
    }.getOrNull()

    suspend fun save(context: Context, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            // 先读边界算采样率，避免大图直接解码占内存
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            } ?: error("无法读取所选图片")
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= 512 && bounds.outHeight / (sample * 2) >= 512) {
                sample *= 2
            }
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            val bmp = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, opts)
            } ?: error("解码失败")

            val side = minOf(bmp.width, bmp.height)
            val x = (bmp.width - side) / 2
            val y = (bmp.height - side) / 2
            val square = Bitmap.createBitmap(bmp, x, y, side, side)
            val scaled = if (side > 512) Bitmap.createScaledBitmap(square, 512, 512, true) else square
            File(context.filesDir, FILE).outputStream().use {
                scaled.compress(Bitmap.CompressFormat.JPEG, 90, it)
            }
            if (square !== bmp) square.recycle()
            if (scaled !== square) scaled.recycle()
            bmp.recycle()
        }.isSuccess
    }
}
