package com.example.flipviewtest

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout


/**
 * Provides basic functionality for fold animation: splitting view into 2 parts,
 * synchronous rotation of both parts and so on.
 */
class FoldableItemLayout(context: Context?) :
    FrameLayout(context!!) {
    private var isAutoScaleEnabled = false
    private val baseLayout: BaseLayout
    private val topPart: PartView
    private val bottomPart: PartView
    private var width = 0
    private var height = 0
    private var cacheBitmap: Bitmap? = null
    private var isInTransformation = false
    private var foldRotation = 0f
    private var scale = 1f
    private var scaleFactor = 1f
    private var scaleFactorY = 1f

    init {
        baseLayout = BaseLayout(this)
        topPart = PartView(this, Gravity.TOP)
        bottomPart = PartView(this, Gravity.BOTTOM)
        setInTransformation(false)
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        return !isInTransformation && super.dispatchTouchEvent(ev)
    }

    override fun dispatchDraw(canvas: Canvas) {
        if (foldRotation != 0f) {
            ensureCacheBitmap()
        }
        super.dispatchDraw(canvas)
    }

    private fun ensureCacheBitmap() {
        width = getWidth()
        height = getHeight()

        // Check if correct cache bitmap is already created
        if (cacheBitmap != null && (cacheBitmap!!.width == width) && (cacheBitmap!!.height == height)) {
            return
        }
        if (cacheBitmap != null) {
            cacheBitmap!!.recycle()
            cacheBitmap = null
        }
        if (width != 0 && height != 0) {
            cacheBitmap = try {
                Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            } catch (outOfMemoryError: OutOfMemoryError) {
                null
            }
        }
        applyCacheBitmap(cacheBitmap)
    }

    private fun applyCacheBitmap(bitmap: Bitmap?) {
        baseLayout.setCacheCanvas(if (bitmap == null) null else Canvas(bitmap))
        topPart.setCacheBitmap(bitmap)
        bottomPart.setCacheBitmap(bitmap)
    }

    /**
     * Fold rotation value in degrees.
     */
    fun setFoldRotation(rotation: Float) {
        foldRotation = rotation
        topPart.applyFoldRotation(rotation)
        bottomPart.applyFoldRotation(rotation)
        setInTransformation(rotation != 0f)
        scaleFactor = 1f
        if (isAutoScaleEnabled && width > 0) {
            val sin = Math.abs(Math.sin(Math.toRadians(rotation.toDouble())))
            val dw = (height * sin).toFloat() * CAMERA_DISTANCE_MAGIC_FACTOR
            scaleFactor = width / (width + dw)
            setScale(scale)
        }
    }

    fun setScale(scale: Float) {
        this.scale = scale
        val scaleX = scale * scaleFactor
        val scaleY = scale * scaleFactor * scaleFactorY
        baseLayout.scaleY = scaleFactorY
        topPart.scaleX = scaleX
        topPart.scaleY = scaleY
        bottomPart.scaleX = scaleX
        bottomPart.scaleY = scaleY
    }

    fun setScaleFactorY(scaleFactorY: Float) {
        this.scaleFactorY = scaleFactorY
        setScale(scale)
    }

    /**
     * Translation preserving middle line splitting.
     */
    fun setRollingDistance(distance: Float) {
        val scaleY = scale * scaleFactor * scaleFactorY
        topPart.applyRollingDistance(distance, scaleY)
        bottomPart.applyRollingDistance(distance, scaleY)
    }

    private fun setInTransformation(isInTransformation: Boolean) {
        if (this.isInTransformation == isInTransformation) {
            return
        }
        this.isInTransformation = isInTransformation
        baseLayout.setDrawToCache(isInTransformation)
        topPart.visibility = if (isInTransformation) VISIBLE else INVISIBLE
        bottomPart.visibility = if (isInTransformation) VISIBLE else INVISIBLE
    }

    fun setAutoScaleEnabled(isAutoScaleEnabled: Boolean) {
        this.isAutoScaleEnabled = isAutoScaleEnabled
    }

    fun getBaseLayout(): FrameLayout {
        return baseLayout
    }

    fun setLayoutVisibleBounds(visibleBounds: Rect?) {
        topPart.setVisibleBounds(visibleBounds)
        bottomPart.setVisibleBounds(visibleBounds)
    }

    fun setFoldShading(shading: FoldShading?) {
        topPart.setFoldShading(shading)
        bottomPart.setFoldShading(shading)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()

        // Helping GC to faster clean up bitmap memory.
        // See issue #10: https://github.com/alexvasilkov/FoldableLayout/issues/10.
        if (cacheBitmap != null) {
            cacheBitmap!!.recycle()
            applyCacheBitmap(null.also { cacheBitmap = it })
        }
    }

    /**
     * View holder layout that can draw itself into given canvas.
     */
    @SuppressLint("ViewConstructor")
    private class BaseLayout internal constructor(layout: FoldableItemLayout) :
        FrameLayout(layout.context) {
        private var cacheCanvas: Canvas? = null
        private var isDrawToCache = false

        init {
            val matchParent = ViewGroup.LayoutParams.MATCH_PARENT
            val params = LayoutParams(matchParent, matchParent)
            layout.addView(this, params)
            setWillNotDraw(false)
        }

        override fun draw(canvas: Canvas) {
            if (isDrawToCache) {
                if (cacheCanvas != null) {
                    cacheCanvas!!.drawColor(0, PorterDuff.Mode.CLEAR)
                    super.draw(cacheCanvas)
                }
            } else {
                super.draw(canvas)
            }
        }

        fun setCacheCanvas(cacheCanvas: Canvas?) {
            this.cacheCanvas = cacheCanvas
        }

        fun setDrawToCache(drawToCache: Boolean) {
            if (isDrawToCache != drawToCache) {
                isDrawToCache = drawToCache
                invalidate()
            }
        }
    }

    /**
     * Splat part view. It will draw top or bottom part of cached bitmap and overlay shadows.
     * Also it contains main logic for all transformations (fold rotation, scale, "rolling
     * distance").
     */
    @SuppressLint("ViewConstructor")
    private class PartView internal constructor(
        parent: FoldableItemLayout,
        private val gravity: Int
    ) :
        View(parent.context) {
        private var bitmap: Bitmap? = null
        private val bitmapBounds = Rect()
        private var clippingFactor = 0.5f
        private val bitmapPaint: Paint
        private var visibleBounds: Rect? = null
        private var intVisibility = 0
        private var extVisibility = 0
        private var localFoldRotation = 0f
        private var shading: FoldShading? = null

        init {
            val matchParent = LayoutParams.MATCH_PARENT
            parent.addView(this, LayoutParams(matchParent, matchParent))
            cameraDistance = (CAMERA_DISTANCE * resources.displayMetrics.densityDpi).toFloat()
            bitmapPaint = Paint()
            bitmapPaint.isDither = true
            bitmapPaint.isFilterBitmap = true
            setWillNotDraw(false)
        }

        fun setCacheBitmap(bitmap: Bitmap?) {
            this.bitmap = bitmap
            calculateBitmapBounds()
        }

        fun setVisibleBounds(visibleBounds: Rect?) {
            this.visibleBounds = visibleBounds
            calculateBitmapBounds()
        }

        fun setFoldShading(shading: FoldShading?) {
            this.shading = shading
        }

        private fun calculateBitmapBounds() {
            if (bitmap == null) {
                bitmapBounds[0, 0, 0] = 0
            } else {
                val bh = bitmap!!.height
                val bw = bitmap!!.width
                val top =
                    if (gravity == Gravity.TOP) 0 else (bh * (1f - clippingFactor) - 0.5f).toInt()
                val bottom =
                    if (gravity == Gravity.TOP) (bh * clippingFactor + 0.5f).toInt() else bh
                bitmapBounds[0, top, bw] = bottom
                if (visibleBounds != null) {
                    if (!bitmapBounds.intersect(visibleBounds!!)) {
                        bitmapBounds[0, 0, 0] = 0 // No intersection
                    }
                }
            }
            invalidate()
        }

        fun applyFoldRotation(rotation: Float) {
            var position = rotation
            while (position < 0f) {
                position += 360f
            }
            position %= 360f
            if (position > 180f) {
                position -= 360f // Now position is within (-180; 180]
            }
            var rotationX = 0f
            var isVisible = true
            if (gravity == Gravity.TOP) {
                if (position <= -90f || position == 180f) { // (-180; -90] || {180} - Will not show
                    isVisible = false
                } else if (position < 0f) { // (-90; 0) - Applying rotation
                    rotationX = position
                }
                // [0; 180) - Holding still
            } else {
                if (position >= 90f) { // [90; 180] - Will not show
                    isVisible = false
                } else if (position > 0f) { // (0; 90) - Applying rotation
                    rotationX = position
                }
                // (-180; 0] - Holding still
            }
            setRotationX(rotationX)
            intVisibility = if (isVisible) VISIBLE else INVISIBLE
            applyVisibility()
            localFoldRotation = position
            invalidate() // Needed to draw shadow overlay
        }

        fun applyRollingDistance(distance: Float, scaleY: Float) {
            // Applying translation
            translationY = (distance * scaleY + 0.5f).toInt().toFloat()

            // Computing clipping for top view (bottom clipping will be 1 - topClipping)
            val h = height / 2
            val topClipping = if (h == 0) 0.5f else 0.5f * (h - distance) / h
            clippingFactor = if (gravity == Gravity.TOP) topClipping else 1f - topClipping
            calculateBitmapBounds()
        }

        override fun setVisibility(visibility: Int) {
            extVisibility = visibility
            applyVisibility()
        }

        @SuppressLint("WrongConstant")
        private fun applyVisibility() {
            super.setVisibility(if (extVisibility == VISIBLE) intVisibility else extVisibility)
        }

        @SuppressLint("MissingSuperCall")
        override fun draw(canvas: Canvas) {
            if (shading != null) {
                shading!!.onPreDraw(canvas, bitmapBounds, localFoldRotation, gravity)
            }
            if (bitmap != null) {
                canvas.drawBitmap(bitmap!!, bitmapBounds, bitmapBounds, bitmapPaint)
            }
            if (shading != null) {
                shading!!.onPostDraw(canvas, bitmapBounds, localFoldRotation, gravity)
            }
        }
    }

    companion object {
        private const val CAMERA_DISTANCE = 48
        private const val CAMERA_DISTANCE_MAGIC_FACTOR = 8f / CAMERA_DISTANCE
    }
}