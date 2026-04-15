package com.example.bazadanych.data.model

import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.renderer.XAxisRenderer
import com.github.mikephil.charting.utils.MPPointF
import com.github.mikephil.charting.utils.Transformer
import com.github.mikephil.charting.utils.Utils
import com.github.mikephil.charting.utils.ViewPortHandler
import android.graphics.Canvas

class MultilineXAxisRenderer(
    viewPortHandler: ViewPortHandler,
    xAxis: XAxis,
    trans: Transformer
) : XAxisRenderer(viewPortHandler, xAxis, trans) {
    override fun drawLabel(c: Canvas?, formattedLabel: String?, x: Float, y: Float, anchor: MPPointF?, angleDegrees: Float) {
        val lines = formattedLabel?.split("\n") ?: return
        var drawY = y
        for (line in lines) {
            Utils.drawXAxisValue(c, line, x, drawY, mAxisLabelPaint, anchor, angleDegrees)
            drawY += mAxisLabelPaint.descent() - mAxisLabelPaint.ascent()
        }
    }
}