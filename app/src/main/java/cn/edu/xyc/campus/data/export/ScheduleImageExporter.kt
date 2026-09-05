package cn.edu.xyc.campus.data.export

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import androidx.compose.ui.graphics.toArgb
import cn.edu.xyc.campus.data.model.Course
import cn.edu.xyc.campus.data.model.SectionTimes
import cn.edu.xyc.campus.ui.screens.COURSE_COLORS
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 课表导出为图片：用 Canvas 按 1080px 宽绘制周课表
 * （标题 / 星期表头 / 细网格 / 彩色课程格，配色与 App 内一致）。
 */
object ScheduleImageExporter {

    private val BG = Color.parseColor("#F7FAFF")
    private val GRID = Color.parseColor("#D9E2F2")
    private val PRIMARY = Color.parseColor("#1E5AA8")
    private val DARK = Color.parseColor("#173A6B")
    private val SECONDARY = Color.parseColor("#6B7B99")

    /** 生成图片文件，存到 cacheDir/export 目录（png），返回文件 */
    fun export(
        context: Context,
        courses: List<Course>,
        termLabel: String,
        weekLabel: String,
        dateRange: String,
    ): File {
        val width = 1080
        val pad = 40f
        val labelW = 88f
        val dayW = (width - pad * 2 - labelW) / 7f
        val titleH = 140f
        val dayH = 60f
        val sectionH = 118f
        val rows = 12
        val height = (pad + titleH + dayH + sectionH * rows + pad).toInt()

        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(BG)

        // 标题区
        val titlePaint = TextPaint().apply {
            color = DARK; textSize = 52f; isAntiAlias = true; isFakeBoldText = true
        }
        canvas.drawText("我的课表", pad, pad + 50f, titlePaint)
        val subPaint = TextPaint().apply { color = SECONDARY; textSize = 30f; isAntiAlias = true }
        canvas.drawText("$termLabel · $weekLabel", pad, pad + 104f, subPaint)
        if (dateRange.isNotEmpty()) {
            val w = subPaint.measureText(dateRange)
            canvas.drawText(dateRange, width - pad - w, pad + 104f, subPaint)
        }

        // 星期表头
        val headY = pad + titleH
        val headPaint = TextPaint().apply {
            color = DARK; textSize = 30f; isAntiAlias = true
            textAlign = Paint.Align.CENTER; isFakeBoldText = true
        }
        listOf("一", "二", "三", "四", "五", "六", "日").forEachIndexed { i, name ->
            canvas.drawText(name, pad + labelW + dayW * i + dayW / 2, headY + 42f, headPaint)
        }

        // 网格线
        val gridTop = headY + dayH
        val gridPaint = Paint().apply { color = GRID; strokeWidth = 2f; isAntiAlias = true }
        for (r in 0..rows) {
            val y = gridTop + sectionH * r
            canvas.drawLine(pad, y, width - pad, y, gridPaint)
        }
        for (c in 0..7) {
            val x = pad + labelW + dayW * c
            canvas.drawLine(x, gridTop, x, gridTop + sectionH * rows, gridPaint)
        }

        // 节次列（节数 + 开始时间）
        val secPaint = TextPaint().apply {
            color = SECONDARY; textSize = 24f; isAntiAlias = true; textAlign = Paint.Align.CENTER
        }
        val times = SectionTimes.table(true)
        for (r in 0 until rows) {
            val cy = gridTop + sectionH * r
            canvas.drawText("${r + 1}", pad + labelW / 2, cy + 44f, secPaint)
            canvas.drawText(times[r].start, pad + labelW / 2, cy + 78f, secPaint)
        }

        // 课程格子：同一起止节的课并排均分列宽
        val cellPaint = Paint().apply { isAntiAlias = true }
        (1..7).forEach { day ->
            courses.filter { it.dayOfWeek == day }
                .groupBy { it.startSection to it.endSection }
                .forEach { (range, list) ->
                    val (s, e) = range
                    val share = dayW / list.size
                    list.forEachIndexed { idx, c ->
                        val left = pad + labelW + dayW * (day - 1) + share * idx
                        val rect = RectF(
                            left + 4f,
                            gridTop + sectionH * (s - 1) + 4f,
                            left + share - 4f,
                            gridTop + sectionH * e - 4f,
                        )
                        val (bgInt, fgInt) = if (c.isCustom) {
                            Color.parseColor("#FFF1C9") to Color.parseColor("#8A6D05")
                        } else {
                            val p = COURSE_COLORS[(c.name.hashCode().let { if (it < 0) -it else it }) % COURSE_COLORS.size]
                            p.first.toArgb() to p.second.toArgb()
                        }
                        cellPaint.color = bgInt
                        canvas.drawRoundRect(rect, 16f, 16f, cellPaint)

                        val namePaint = TextPaint().apply {
                            color = fgInt; textSize = 30f; isAntiAlias = true; isFakeBoldText = true
                        }
                        val nameLayout = StaticLayout.Builder
                            .obtain(
                                c.name, 0, c.name.length, namePaint,
                                (rect.width() - 20f).toInt().coerceAtLeast(60),
                            )
                            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                            .setMaxLines(3)
                            .setEllipsize(TextUtils.TruncateAt.END)
                            .build()
                        canvas.withTranslation(rect.left + 10f, rect.top + 10f) { nameLayout.draw(this) }

                        if (c.room.isNotEmpty()) {
                            // 教室文字限宽一行省略，避免长教室名溢出格子
                            val roomText = "@${c.room}"
                            val roomPaint = TextPaint().apply {
                                color = fgInt; textSize = 24f; isAntiAlias = true; alpha = 220
                            }
                            val roomLayout = StaticLayout.Builder
                                .obtain(
                                    roomText, 0, roomText.length, roomPaint,
                                    (rect.width() - 20f).toInt().coerceAtLeast(60),
                                )
                                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                                .setMaxLines(1)
                                .setEllipsize(TextUtils.TruncateAt.END)
                                .build()
                            canvas.withTranslation(rect.left + 10f, rect.bottom - 12f - roomLayout.height) {
                                roomLayout.draw(this)
                            }
                        }
                    }
                }
        }

        val dir = File(context.cacheDir, "export").apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
        val file = File(dir, "课表_${weekLabel.replace(" ", "")}_$stamp.png")
        file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return file
    }

    private inline fun Canvas.withTranslation(x: Float, y: Float, block: Canvas.() -> Unit) {
        val checkpoint = save()
        translate(x, y)
        try {
            block()
        } finally {
            restoreToCount(checkpoint)
        }
    }
}
