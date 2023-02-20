package com.example.flipviewtest

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.view.Gravity


class SimpleFoldShading : FoldShading {
    private val solidShadow: Paint

    init {
        solidShadow = Paint()
        solidShadow.color = SHADOW_COLOR
    }

    override fun onPreDraw(canvas: Canvas?, bounds: Rect?, rotation: Float, gravity: Int) {
        // No-op
    }

    override fun onPostDraw(canvas: Canvas?, bounds: Rect?, rotation: Float, gravity: Int) {
        val intensity = getShadowIntensity(rotation, gravity)
        if (intensity > 0f) {
            val alpha = (SHADOW_MAX_ALPHA * intensity).toInt()
            solidShadow.alpha = alpha
            canvas!!.drawRect(bounds!!, solidShadow)
        }
    }

    private fun getShadowIntensity(rotation: Float, gravity: Int): Float {
        var intensity = 0f
        if (gravity == Gravity.TOP) {
            if (rotation > -90f && rotation < 0f) { // (-90; 0) - Rotation is applied
                intensity = -rotation / 90f
            }
        } else {
            if (rotation > 0f && rotation < 90f) { // (0; 90) - Rotation is applied
                intensity = rotation / 90f
            }
        }
        return intensity
    }

    companion object {
        private const val SHADOW_COLOR = Color.BLACK
        private const val SHADOW_MAX_ALPHA = 192
    }
}
