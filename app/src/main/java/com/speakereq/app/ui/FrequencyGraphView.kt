package com.speakereq.app.ui

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import com.speakereq.app.R
import com.speakereq.app.audio.FrequencyAnalyzer
import kotlin.math.log10

/**
 * Custom View that displays frequency response curves on a logarithmic frequency axis.
 * Shows measured response (red) and corrected/target response (green).
 */
class FrequencyGraphView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // Data
    private var measuredLevels: FloatArray? = null
    private var correctedLevels: FloatArray? = null

    // Drawing
    private val paintMeasured = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.graph_measured)
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }

    private val paintCorrected = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.graph_corrected)
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }

    private val paintGrid = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.graph_grid)
        style = Paint.Style.STROKE
        strokeWidth = 1f
    }

    private val paintGridText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.graph_text)
        textSize = 24f
        typeface = Typeface.MONOSPACE
    }

    private val paintZeroLine = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.graph_text)
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
        pathEffect = DashPathEffect(floatArrayOf(8f, 4f), 0f)
    }

    private val paintLegend = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 28f
    }

    private val pathMeasured = Path()
    private val pathCorrected = Path()

    // Graph bounds
    private val graphRect = RectF()
    private val padding = RectF(60f, 30f, 20f, 50f) // left, top, right, bottom

    // Y axis range
    private var yMinDb = -20f
    private var yMaxDb = 20f

    // Frequency range (logarithmic)
    private val freqMin = 20.0
    private val freqMax = 20000.0
    private val logFreqMin = log10(freqMin)
    private val logFreqMax = log10(freqMax)

    // Frequency grid lines
    private val freqGridLines = doubleArrayOf(
        20.0, 50.0, 100.0, 200.0, 500.0, 1000.0, 2000.0, 5000.0, 10000.0, 20000.0
    )
    private val freqGridLabels = arrayOf(
        "20", "50", "100", "200", "500", "1k", "2k", "5k", "10k", "20k"
    )

    // dB grid lines
    private val dbGridLines = floatArrayOf(-15f, -10f, -5f, 0f, 5f, 10f, 15f)

    fun setMeasuredResponse(levels: FloatArray) {
        measuredLevels = levels
        invalidate()
    }

    fun setCorrectedResponse(levels: FloatArray) {
        correctedLevels = levels
        invalidate()
    }

    fun clearData() {
        measuredLevels = null
        correctedLevels = null
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        graphRect.set(
            padding.left,
            padding.top,
            w - padding.right,
            h - padding.bottom
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        drawGrid(canvas)
        drawZeroLine(canvas)

        measuredLevels?.let { drawCurve(canvas, it, pathMeasured, paintMeasured) }
        correctedLevels?.let { drawCurve(canvas, it, pathCorrected, paintCorrected) }

        drawLegend(canvas)
    }

    private fun drawGrid(canvas: Canvas) {
        // Vertical lines (frequency)
        for (i in freqGridLines.indices) {
            val x = freqToX(freqGridLines[i])
            canvas.drawLine(x, graphRect.top, x, graphRect.bottom, paintGrid)

            // Label
            val label = freqGridLabels[i]
            val textWidth = paintGridText.measureText(label)
            canvas.drawText(label, x - textWidth / 2, graphRect.bottom + 35f, paintGridText)
        }

        // Horizontal lines (dB)
        for (db in dbGridLines) {
            val y = dbToY(db)
            canvas.drawLine(graphRect.left, y, graphRect.right, y, paintGrid)

            // Label
            val label = if (db >= 0) "+${db.toInt()}" else "${db.toInt()}"
            canvas.drawText(label, 5f, y + 8f, paintGridText)
        }
    }

    private fun drawZeroLine(canvas: Canvas) {
        val y = dbToY(0f)
        canvas.drawLine(graphRect.left, y, graphRect.right, y, paintZeroLine)
    }

    private fun drawCurve(canvas: Canvas, levels: FloatArray, path: Path, paint: Paint) {
        if (levels.isEmpty()) return

        path.reset()
        val frequencies = FrequencyAnalyzer.BAND_CENTER_FREQUENCIES

        var firstPoint = true
        for (i in levels.indices) {
            if (i >= frequencies.size) break
            if (levels[i] < -100f) continue // Skip invalid values

            val x = freqToX(frequencies[i])
            val y = dbToY(levels[i])

            if (firstPoint) {
                path.moveTo(x, y)
                firstPoint = false
            } else {
                path.lineTo(x, y)
            }
        }

        canvas.drawPath(path, paint)
    }

    private fun drawLegend(canvas: Canvas) {
        val legendX = graphRect.left + 10f
        var legendY = graphRect.top + 30f

        if (measuredLevels != null) {
            paintLegend.color = context.getColor(R.color.graph_measured)
            canvas.drawText("● ${context.getString(R.string.graph_measured)}", legendX, legendY, paintLegend)
            legendY += 35f
        }

        if (correctedLevels != null) {
            paintLegend.color = context.getColor(R.color.graph_corrected)
            canvas.drawText("● ${context.getString(R.string.graph_corrected)}", legendX, legendY, paintLegend)
        }
    }

    private fun freqToX(freq: Double): Float {
        val logFreq = log10(freq)
        val fraction = ((logFreq - logFreqMin) / (logFreqMax - logFreqMin)).toFloat()
        return graphRect.left + fraction * graphRect.width()
    }

    private fun dbToY(db: Float): Float {
        val fraction = (yMaxDb - db) / (yMaxDb - yMinDb)
        return graphRect.top + fraction * graphRect.height()
    }
}
