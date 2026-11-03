package com.manish.vahakvigil.ui.components

import android.graphics.Paint
import android.graphics.Path as AndroidPath
import android.graphics.PathMeasure
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import com.manish.vahakvigil.ui.theme.vahakColors

/** Shared detailed motorcycle illustration, progressively traced along its real vector strokes. */
@Composable
fun ProgressiveMotorcycleLineArt(
    modifier: Modifier = Modifier,
    progress: Float = 1f,
    lineColor: Color = androidx.compose.material3.MaterialTheme.vahakColors.motorcycleLine
) {
    val color = lineColor.toArgb()
    Canvas(
        modifier = modifier.drawWithCache {
            val paths = motorcyclePaths(size.width, size.height)
            val measured = paths.map { (path, alpha, thin) ->
                Triple(PathMeasure(path, false), alpha, thin)
            }
            val totalLength = measured.sumOf { it.first.length.toDouble() }.toFloat().coerceAtLeast(1f)
            val mainPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = color
                style = Paint.Style.STROKE
                strokeWidth = (size.width * 0.0055f).coerceIn(1.8f, 3.2f)
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }
            val partialPath = AndroidPath()
            onDrawBehind {
                var distanceBeforePath = 0f
                val revealDistance = progress.coerceIn(0f, 1f) * totalLength
                drawIntoCanvas { canvas ->
                    val native = canvas.nativeCanvas
                    measured.forEach { (measure, alpha, thin) ->
                        val pathLength = measure.length
                        val localDistance = (revealDistance - distanceBeforePath).coerceIn(0f, pathLength)
                        if (localDistance > 0f && pathLength > 0f) {
                            partialPath.rewind()
                            measure.getSegment(0f, localDistance, partialPath, true)
                            mainPaint.alpha = (255 * alpha).toInt()
                            mainPaint.strokeWidth = (size.width * if (thin) 0.0034f else 0.0055f).coerceIn(1.2f, 3.2f)
                            native.drawPath(partialPath, mainPaint)
                        }
                        distanceBeforePath += pathLength
                    }
                }
            }
        }
    ) {}
}

private data class MotorcycleStroke(val path: AndroidPath, val alpha: Float = 1f, val thin: Boolean = false)

private fun motorcyclePaths(w: Float, h: Float): List<MotorcycleStroke> {
    val rearX = w * 0.24f
    val frontX = w * 0.76f
    val wheelY = h * 0.68f
    val tireRadius = h * 0.20f
    val rimRadius = tireRadius * 0.72f
    val discRadius = tireRadius * 0.44f
    val hubRadius = tireRadius * 0.20f
    val upperTriple = android.graphics.PointF(w * 0.65f, h * 0.27f)
    val lowerTriple = android.graphics.PointF(w * 0.68f, h * 0.36f)
    fun line(x1: Float, y1: Float, x2: Float, y2: Float, alpha: Float = 1f, thin: Boolean = false) =
        MotorcycleStroke(AndroidPath().apply { moveTo(x1, y1); lineTo(x2, y2) }, alpha, thin)
    fun circle(cx: Float, cy: Float, radius: Float, alpha: Float = 1f, thin: Boolean = false) =
        MotorcycleStroke(AndroidPath().apply { addCircle(cx, cy, radius, AndroidPath.Direction.CW) }, alpha, thin)
    val strokes = mutableListOf<MotorcycleStroke>()

    // Rear wheel: tire, rim, brake disc, hub and three real spokes.
    strokes += circle(rearX, wheelY, tireRadius)
    strokes += circle(rearX, wheelY, rimRadius, 0.7f, true)
    strokes += circle(rearX, wheelY, discRadius, 1f, true)
    strokes += circle(rearX, wheelY, hubRadius)
    repeat(3) { i ->
        val a = Math.toRadians(i * 120.0 + 30.0)
        strokes += line(
            rearX + kotlin.math.cos(a).toFloat() * hubRadius,
            wheelY + kotlin.math.sin(a).toFloat() * hubRadius,
            rearX + kotlin.math.cos(a).toFloat() * rimRadius,
            wheelY + kotlin.math.sin(a).toFloat() * rimRadius,
            0.65f,
            true
        )
    }

    // Front wheel, rotor bracket and spokes.
    strokes += circle(frontX, wheelY, tireRadius)
    strokes += circle(frontX, wheelY, rimRadius, 0.7f, true)
    strokes += circle(frontX, wheelY, discRadius, 1f, true)
    strokes += circle(frontX, wheelY, hubRadius)
    strokes += line(frontX - discRadius * 0.6f, wheelY - discRadius * 0.6f, frontX - discRadius * 0.2f, wheelY - discRadius * 0.8f, thin = true)
    repeat(3) { i ->
        val a = Math.toRadians(i * 120.0 - 15.0)
        strokes += line(
            frontX + kotlin.math.cos(a).toFloat() * hubRadius,
            wheelY + kotlin.math.sin(a).toFloat() * hubRadius,
            frontX + kotlin.math.cos(a).toFloat() * rimRadius,
            wheelY + kotlin.math.sin(a).toFloat() * rimRadius,
            0.65f,
            true
        )
    }

    // Front assembly: paired telescopic fork detail, fender, cockpit and cowl.
    strokes += line(frontX, wheelY, upperTriple.x, upperTriple.y)
    strokes += line(frontX - 3f, wheelY - 1f, upperTriple.x - 3f, upperTriple.y - 1f, 0.6f, true)
    strokes += MotorcycleStroke(AndroidPath().apply {
        moveTo(w * 0.69f, h * 0.58f)
        cubicTo(w * 0.71f, h * 0.50f, w * 0.77f, h * 0.49f, w * 0.82f, h * 0.55f)
    })
    strokes += line(upperTriple.x, upperTriple.y, w * 0.63f, h * 0.24f)
    strokes += MotorcycleStroke(AndroidPath().apply {
        moveTo(upperTriple.x, upperTriple.y)
        lineTo(w * 0.73f, h * 0.30f)
        lineTo(w * 0.72f, h * 0.37f)
        lineTo(lowerTriple.x, lowerTriple.y)
    })
    strokes += line(w * 0.73f, h * 0.30f, w * 0.78f, h * 0.29f, thin = true)
    strokes += line(w * 0.78f, h * 0.29f, w * 0.82f, h * 0.31f, thin = true)

    // Fuel tank and lower sculpted contour.
    strokes += MotorcycleStroke(AndroidPath().apply {
        moveTo(upperTriple.x, upperTriple.y)
        cubicTo(w * 0.61f, h * 0.23f, w * 0.54f, h * 0.22f, w * 0.50f, h * 0.26f)
        cubicTo(w * 0.46f, h * 0.30f, w * 0.44f, h * 0.38f, w * 0.42f, h * 0.43f)
    })
    strokes += MotorcycleStroke(AndroidPath().apply {
        moveTo(w * 0.59f, h * 0.31f)
        cubicTo(w * 0.52f, h * 0.32f, w * 0.47f, h * 0.38f, w * 0.44f, h * 0.43f)
    }, 0.5f, true)

    // Seat, upswept tail, rear subframe and monoshock.
    strokes += MotorcycleStroke(AndroidPath().apply {
        moveTo(w * 0.42f, h * 0.43f)
        cubicTo(w * 0.38f, h * 0.45f, w * 0.34f, h * 0.44f, w * 0.31f, h * 0.41f)
        cubicTo(w * 0.25f, h * 0.36f, w * 0.20f, h * 0.33f, w * 0.16f, h * 0.31f)
        lineTo(w * 0.15f, h * 0.33f)
        cubicTo(w * 0.19f, h * 0.37f, w * 0.25f, h * 0.43f, w * 0.32f, h * 0.47f)
    })
    strokes += line(w * 0.32f, h * 0.47f, w * 0.38f, h * 0.56f, 0.65f)
    strokes += line(w * 0.35f, h * 0.52f, w * 0.42f, h * 0.44f)

    // Trellis frame, cross brace, engine case and clutch cover.
    strokes += MotorcycleStroke(AndroidPath().apply {
        moveTo(lowerTriple.x, lowerTriple.y)
        lineTo(w * 0.51f, h * 0.43f)
        lineTo(w * 0.38f, h * 0.56f)
    })
    strokes += line(w * 0.58f, h * 0.40f, w * 0.48f, h * 0.56f, 0.6f, true)
    strokes += MotorcycleStroke(AndroidPath().apply {
        moveTo(w * 0.60f, h * 0.43f)
        lineTo(w * 0.62f, h * 0.54f)
        lineTo(w * 0.58f, h * 0.67f)
        lineTo(w * 0.44f, h * 0.67f)
        lineTo(w * 0.40f, h * 0.57f)
        lineTo(w * 0.48f, h * 0.46f)
        close()
    }, 0.55f, true)
    strokes += circle(w * 0.49f, h * 0.59f, h * 0.055f)
    strokes += circle(w * 0.49f, h * 0.59f, h * 0.025f, 0.6f, true)
    strokes += line(w * 0.46f, h * 0.53f, w * 0.52f, h * 0.53f, 0.65f, true)
    strokes += line(w * 0.45f, h * 0.64f, w * 0.53f, h * 0.64f, 0.65f, true)

    // Swingarm, triangular bracing, exhaust header and slash-cut tip.
    strokes += line(w * 0.38f, h * 0.56f, rearX, wheelY)
    strokes += MotorcycleStroke(AndroidPath().apply {
        moveTo(w * 0.38f, h * 0.56f)
        lineTo(w * 0.33f, h * 0.51f)
        lineTo(rearX, wheelY)
    }, 0.7f, true)
    strokes += MotorcycleStroke(AndroidPath().apply {
        moveTo(w * 0.61f, h * 0.47f)
        cubicTo(w * 0.65f, h * 0.55f, w * 0.62f, h * 0.69f, w * 0.55f, h * 0.71f)
        lineTo(w * 0.42f, h * 0.71f)
        lineTo(w * 0.35f, h * 0.65f)
    })
    strokes += line(w * 0.35f, h * 0.65f, w * 0.34f, h * 0.62f)
    return strokes
}
