package com.example.flipviewtest

import android.graphics.Canvas
import android.graphics.Rect

interface FoldShading {
    fun onPreDraw(canvas: Canvas?, bounds: Rect?, rotation: Float, gravity: Int)
    fun onPostDraw(canvas: Canvas?, bounds: Rect?, rotation: Float, gravity: Int)
}