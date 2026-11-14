package com.haydaybot.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PointF
import android.graphics.Rect
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.MatOfByte
import org.opencv.imgcodecs.Imgcodecs
import org.opencv.imgproc.Imgproc

/**
 * OpenCV template matching for Hay Day screen elements.
 * Templates are loaded from assets/templates/<category>/<name>.png.
 * Temporary OpenCV matrices are released after each operation.
 */
class VisionEngine(
    private val context: Context,
    private val confidenceThreshold: Float = 0.80f,
) : AutoCloseable {

    data class MatchResult(
        val name: String,
        val confidence: Float,
        val rect: Rect,
    ) {
        val center: PointF
            get() = PointF(
                rect.left + rect.width() / 2f,
                rect.top + rect.height() / 2f
            )
    }

    private val templateCache = HashMap<String, Mat>()

    init {
        require(confidenceThreshold in 0f..1f) {
            "confidenceThreshold must be between 0 and 1"
        }
    }

    @Synchronized
    fun find(screen: Bitmap, category: String, name: String): MatchResult? {
        val template = loadTemplate(category, name) ?: return null
        val screenMat = bitmapToMat(screen)
        return try {
            matchSingle(screenMat, template, name)
        } finally {
            screenMat.release()
        }
    }

    @Synchronized
    fun findAll(screen: Bitmap, category: String, name: String): List<MatchResult> {
        val template = loadTemplate(category, name) ?: return emptyList()
        val screenMat = bitmapToMat(screen)
        return try {
            matchAll(screenMat, template, name)
        } finally {
            screenMat.release()
        }
    }

    @Synchronized
    fun findAny(screen: Bitmap, category: String, names: List<String>): MatchResult? {
        if (names.isEmpty()) return null
        val screenMat = bitmapToMat(screen)
        return try {
            for (name in names) {
                val template = loadTemplate(category, name) ?: continue
                val match = matchSingle(screenMat, template, name)
                if (match != null) return match
            }
            null
        } finally {
            screenMat.release()
        }
    }

    fun pixelColor(screen: Bitmap, x: Int, y: Int): Triple<Int, Int, Int> {
        require(x in 0 until screen.width && y in 0 until screen.height) {
            "Pixel ($x, $y) is outside ${screen.width}x${screen.height} screenshot"
        }
        val px = screen.getPixel(x, y)
        return Triple(
            android.graphics.Color.red(px),
            android.graphics.Color.green(px),
            android.graphics.Color.blue(px)
        )
    }

    @Synchronized
    override fun close() {
        templateCache.values.forEach { it.release() }
        templateCache.clear()
    }

    @Synchronized
    private fun loadTemplate(category: String, name: String): Mat? {
        val key = "$category/$name"
        templateCache[key]?.let { return it }

        return try {
            context.assets.open("templates/$category/$name.png").use { stream ->
                val bytes = stream.readBytes()
                val buffer = MatOfByte(*bytes)
                try {
                    val decoded = Imgcodecs.imdecode(buffer, Imgcodecs.IMREAD_COLOR)
                    if (decoded.empty()) {
                        decoded.release()
                        null
                    } else {
                        templateCache[key] = decoded
                        decoded
                    }
                } finally {
                    buffer.release()
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun matchSingle(screen: Mat, template: Mat, name: String): MatchResult? {
        if (!isMatchSizeValid(screen, template)) return null

        val result = Mat()
        return try {
            Imgproc.matchTemplate(screen, template, result, Imgproc.TM_CCOEFF_NORMED)
            val extrema = Core.minMaxLoc(result)
            if (extrema.maxVal < confidenceThreshold) {
                null
            } else {
                val x = extrema.maxLoc.x.toInt()
                val y = extrema.maxLoc.y.toInt()
                MatchResult(
                    name,
                    extrema.maxVal.toFloat(),
                    Rect(x, y, x + template.cols(), y + template.rows())
                )
            }
        } finally {
            result.release()
        }
    }

    private fun matchAll(screen: Mat, template: Mat, name: String): List<MatchResult> {
        if (!isMatchSizeValid(screen, template)) return emptyList()

        val result = Mat()
        try {
            Imgproc.matchTemplate(screen, template, result, Imgproc.TM_CCOEFF_NORMED)
            val candidates = mutableListOf<MatchResult>()
            val value = FloatArray(1)
            for (row in 0 until result.rows()) {
                for (col in 0 until result.cols()) {
                    result.get(row, col, value)
                    if (value[0] >= confidenceThreshold) {
                        candidates.add(
                            MatchResult(
                                name,
                                value[0],
                                Rect(
                                    col,
                                    row,
                                    col + template.cols(),
                                    row + template.rows()
                                )
                            )
                        )
                    }
                }
            }
            return nonMaximumSuppression(candidates, 0.30f)
        } finally {
            result.release()
        }
    }

    private fun isMatchSizeValid(screen: Mat, template: Mat): Boolean =
        !screen.empty() &&
            !template.empty() &&
            template.cols() <= screen.cols() &&
            template.rows() <= screen.rows()

    private fun nonMaximumSuppression(
        matches: List<MatchResult>,
        iouThreshold: Float
    ): List<MatchResult> {
        val remaining = matches.sortedByDescending { it.confidence }.toMutableList()
        val selected = mutableListOf<MatchResult>()
        while (remaining.isNotEmpty()) {
            val best = remaining.removeAt(0)
            selected.add(best)
            remaining.removeAll { intersectionOverUnion(best.rect, it.rect) >= iouThreshold }
        }
        return selected
    }

    private fun intersectionOverUnion(a: Rect, b: Rect): Float {
        val left = maxOf(a.left, b.left)
        val top = maxOf(a.top, b.top)
        val right = minOf(a.right, b.right)
        val bottom = minOf(a.bottom, b.bottom)
        val width = right - left
        val height = bottom - top
        if (width <= 0 || height <= 0) return 0f
        val intersection = width.toFloat() * height
        val union = a.width().toFloat() * a.height() +
            b.width().toFloat() * b.height() - intersection
        return if (union <= 0f) 0f else intersection / union
    }

    private fun bitmapToMat(bitmap: Bitmap): Mat {
        require(!bitmap.isRecycled) { "Cannot analyze a recycled screenshot" }
        val mat = Mat()
        Utils.bitmapToMat(bitmap, mat)
        Imgproc.cvtColor(mat, mat, Imgproc.COLOR_RGBA2BGR)
        return mat
    }
}
