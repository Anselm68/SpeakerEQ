package com.speakereq.app.ui

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import com.speakereq.app.R
import com.speakereq.app.audio.FrequencyAnalyzer

/**
 * Custom View that displays EQ correction as vertical bars for each frequency band.
 * Green bars = boost, Red bars = cut.
 */
class EqBandView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var correctionValues: FloatArray? = null
    private val maxDb = 15f

    private val paintPositive = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.graph_corrected)
        style = Paint.Style.FILL
    }

    private val paintNegative = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.graph_measured)
        style = Paint.Style.FILL
    }

    private val paintGrid = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.graph_grid)
        style = Paint.Style.STROKE
        strokeWidth = 1f
    }

    private val paintText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.graph_text)
        textSize = 18f
        textAlign = Paint.Align.CENTER
    }

    fun setCorrectionValues(values: FloatArray) {
        correctionValues = values
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val values = correctionValues ?: return
        val bandCount = values.size
        if (bandCount == 0) return

        val barWidth = width.toFloat() / bandCount
        val centerY = height / 2f

        // Draw center line (0 dB)
        canvas.drawLine(0f, centerY, width.toFloat(), centerY, paintGrid)

        // Draw bars
        for (i in values.indices) {
            val x = i * barWidth
            val barHeight = (values[i] / maxDb) * (height / 2f)

            val paint = if (values[i] >= 0) paintPositive else paintNegative
            canvas.drawRect(
                x + 1f,
                centerY - barHeight,
                x + barWidth - 1f,
                centerY,
                paint
            )

            // Draw frequency label for selected bands
            if (i % 4 == 0 && i < FrequencyAnalyzer.BAND_LABELS.size) {
                canvas.drawText(
                    FrequencyAnalyzer.BAND_LABELS[i],
                    x + barWidth / 2,
                    height.toFloat() - 4f,
                    paintText
                )
            }
        }
    }
}
