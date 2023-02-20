package com.example.flipviewtest

import android.animation.ObjectAnimator
import android.content.Context
import android.database.DataSetObserver
import android.graphics.Canvas
import android.util.AttributeSet
import android.util.SparseArray
import android.view.GestureDetector
import android.view.GestureDetector.SimpleOnGestureListener
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.Adapter
import android.widget.BaseAdapter
import android.widget.FrameLayout
import java.util.*


/**
 * Foldable items list layout.
 *
 *
 * It wraps views created by given BaseAdapter into FoldableItemLayouts and provides functionality
 * to scroll among them.
 */
class FoldableListLayout : FrameLayout {
    private var foldRotationListener: OnFoldRotationListener? = null
    private var adapter: BaseAdapter? = null
    private var foldRotation = 0f
    private var minRotation = 0f
    private var maxRotation = 0f
    private var backLayout: FoldableItemLayout? = null
    private var frontLayout: FoldableItemLayout? = null
    private var foldShading: FoldShading? = null
    private var isAutoScaleEnabled = false
    private val foldableItemsMap = SparseArray<FoldableItemLayout>()
    private val foldableItemsCache: Queue<FoldableItemLayout> = LinkedList()
    private val recycledViews = SparseArray<Queue<View>>()
    private val viewsTypesMap: MutableMap<View, Int> = HashMap()
    private var isGesturesEnabled = true
    private var animator: ObjectAnimator? = null
    private var lastTouchEventTime: Long = 0
    private var lastTouchEventAction = 0
    private var lastTouchEventResult = false
    private var gestureDetector: GestureDetector? = null
    private var flingAnimation: FlingAnimation? = null
    private var minDistanceBeforeScroll = 0f
    private var isScrollDetected = false
    private var scrollFactor = DEFAULT_SCROLL_FACTOR
    private var scrollStartRotation = 0f
    private var scrollStartY = 0f
    private val dataObserver: DataSetObserver = object : DataSetObserver() {
        override fun onChanged() {
            super.onChanged()
            updateAdapterData()
        }

        override fun onInvalidated() {
            super.onInvalidated()
            updateAdapterData()
        }
    }

    constructor(context: Context) : super(context) {
        init(context)
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        init(context)
    }

    constructor(context: Context, attrs: AttributeSet?, defStyle: Int) : super(
        context,
        attrs,
        defStyle
    ) {
        init(context)
    }

    private fun init(context: Context) {
        gestureDetector = GestureDetector(context, object : SimpleOnGestureListener() {
            override fun onDown(event: MotionEvent): Boolean {
                return this@FoldableListLayout.onDown()
            }

            override fun onScroll(
                e1: MotionEvent,
                e2: MotionEvent,
                distX: Float,
                distY: Float
            ): Boolean {
                return this@FoldableListLayout.onScroll(e1, e2)
            }

            override fun onFling(
                e1: MotionEvent,
                e2: MotionEvent,
                velX: Float,
                velY: Float
            ): Boolean {
                return this@FoldableListLayout.onFling(velY)
            }
        })
        gestureDetector!!.setIsLongpressEnabled(false)
        animator = ObjectAnimator.ofFloat(this, "foldRotation", 0f)
        minDistanceBeforeScroll = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
        flingAnimation = FlingAnimation()
        foldShading = SimpleFoldShading()
        isChildrenDrawingOrderEnabled = true
    }

    /**
     * Internal parameter. Defines scroll velocity when user scrolls list.
     */
    protected fun setScrollFactor(scrollFactor: Float) {
        this.scrollFactor = scrollFactor
    }

    override fun dispatchDraw(canvas: Canvas) {
        // We want to manually draw selected children
        if (backLayout != null) {
            backLayout!!.draw(canvas)
        }
        if (frontLayout != null) {
            frontLayout!!.draw(canvas)
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        super.dispatchTouchEvent(ev)
        return count > 0 // No touches for underlying views if we have items
    }

    override fun getChildDrawingOrder(childCount: Int, index: Int): Int {
        if (frontLayout == null) {
            return index // Default order
        }

        // We need to return front view as last item for correct touches handling
        val front = indexOfChild(frontLayout)
        return if (index == childCount - 1) front else if (index >= front) index + 1 else index
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        // Listening for events but propagates them to children if no own gestures are detected
        return isGesturesEnabled && processTouch(event)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // We will be here if no children wants to handle touches or if own gesture detected
        return isGesturesEnabled && processTouch(event)
    }

    // Public API
    fun setOnFoldRotationListener(listener: OnFoldRotationListener?) {
        foldRotationListener = listener
    }

    /**
     * Sets shading to use during fold rotation. Should be called before
     * [.setAdapter]
     */
    fun setFoldShading(shading: FoldShading?) {
        foldShading = shading
    }

    /**
     * Sets whether gestures are enabled or not. Useful when layout content is scrollable.
     */
    // Public API
    fun setGesturesEnabled(isGesturesEnabled: Boolean) {
        this.isGesturesEnabled = isGesturesEnabled
    }

    /**
     * Sets whether view should scale down to fit moving part into screen.
     */
    // Public API
    fun setAutoScaleEnabled(isAutoScaleEnabled: Boolean) {
        this.isAutoScaleEnabled = isAutoScaleEnabled
        var i = 0
        val size = foldableItemsMap.size()
        while (i < size) {
            foldableItemsMap.valueAt(i).setAutoScaleEnabled(isAutoScaleEnabled)
            i++
        }
    }

    fun setAdapter(adapter: BaseAdapter) {
        if (this.adapter != null) {
            this.adapter!!.unregisterDataSetObserver(dataObserver)
        }
        this.adapter = adapter
        if (this.adapter != null) {
            this.adapter!!.registerDataSetObserver(dataObserver)
        }
        updateAdapterData()
    }

    // Public API
    fun getAdapter(): BaseAdapter? {
        return adapter
    }

    val count: Int
        get() = if (adapter == null) 0 else adapter!!.count

    private fun updateAdapterData() {
        val count = count
        minRotation = 0f
        maxRotation = if (count == 0) 0f else 180f * (count - 1)
        freeAllLayouts() // Clearing old bindings
        recycledViews.clear()
        viewsTypesMap.clear()

        // Recalculating items
        setFoldRotation(foldRotation)
    }

    fun getFoldRotation(): Float {
        return foldRotation
    }

    fun setFoldRotation(rotation: Float) {
        setFoldRotation(rotation, false)
    }

    protected fun setFoldRotation(rotation: Float, isFromUser: Boolean) {
        var rotation = rotation
        if (isFromUser) {
            animator!!.cancel()
            flingAnimation!!.stop()
        }
        rotation = Math.min(Math.max(minRotation, rotation), maxRotation)
        foldRotation = rotation
        val firstVisiblePosition = (rotation / 180f).toInt()
        val localRotation = rotation % 180f
        val totalCount = count
        var firstLayout: FoldableItemLayout? = null
        var secondLayout: FoldableItemLayout? = null
        if (firstVisiblePosition < totalCount) {
            firstLayout = getLayoutForItem(firstVisiblePosition)
            firstLayout.setFoldRotation(localRotation)
            onFoldRotationChanged(firstLayout, firstVisiblePosition)
        }
        if (firstVisiblePosition + 1 < totalCount) {
            secondLayout = getLayoutForItem(firstVisiblePosition + 1)
            secondLayout.setFoldRotation(localRotation - 180f)
            onFoldRotationChanged(secondLayout, firstVisiblePosition + 1)
        }
        val isReversedOrder = localRotation <= 90f
        if (isReversedOrder) {
            backLayout = secondLayout
            frontLayout = firstLayout
        } else {
            backLayout = firstLayout
            frontLayout = secondLayout
        }
        if (foldRotationListener != null) {
            foldRotationListener!!.onFoldRotation(rotation, isFromUser)
        }

        // When hardware acceleration is enabled view may not be invalidated and redrawn,
        // but we need it to properly draw animation
        invalidate()
    }

    protected fun onFoldRotationChanged(layout: FoldableItemLayout?, position: Int) {
        // Subclasses can apply their transformations here
    }

    private fun getLayoutForItem(position: Int): FoldableItemLayout {
        var layout = foldableItemsMap[position]
        if (layout != null) {
            return layout // We already have layout for this position
        }

        // Trying to free used layout (far enough from currently requested)
        var farthestItem = position
        val size = foldableItemsMap.size()
        for (i in 0 until size) {
            val pos = foldableItemsMap.keyAt(i)
            if (Math.abs(position - pos) > Math.abs(position - farthestItem)) {
                farthestItem = pos
            }
        }
        if (Math.abs(farthestItem - position) >= MAX_CHILDREN_COUNT) {
            layout = foldableItemsMap[farthestItem]
            foldableItemsMap.remove(farthestItem)
            recycleAdapterView(layout)
        }
        if (layout == null) {
            // Trying to find cached layout
            layout = foldableItemsCache.poll()
        }
        if (layout == null) {
            // If still no suited layout - create it
            layout = FoldableItemLayout(context)
            layout.setFoldShading(foldShading)
            addView(layout, PARAMS)
        }
        layout.setAutoScaleEnabled(isAutoScaleEnabled)
        setupAdapterView(layout, position)
        foldableItemsMap.put(position, layout)
        return layout
    }

    private fun setupAdapterView(layout: FoldableItemLayout, position: Int) {
        // Binding layout to new data
        val type = adapter!!.getItemViewType(position)
        var recycledView: View? = null
        if (type != Adapter.IGNORE_ITEM_VIEW_TYPE) {
            val cache = recycledViews[type]
            recycledView = cache?.poll()
        }
        val view = adapter!!.getView(position, recycledView, layout.getBaseLayout())
        if (type != Adapter.IGNORE_ITEM_VIEW_TYPE) {
            viewsTypesMap[view] = type
        }
        layout.getBaseLayout().addView(view, PARAMS)
    }

    private fun recycleAdapterView(layout: FoldableItemLayout?) {
        if (layout!!.getBaseLayout().childCount == 0) {
            return  // Nothing to recycle
        }
        val view = layout.getBaseLayout().getChildAt(0)
        layout.getBaseLayout().removeAllViews()
        val type = viewsTypesMap.remove(view)
        if (type != null) {
            var cache = recycledViews[type]
            if (cache == null) {
                recycledViews.put(type, LinkedList<View>().also {
                    cache = it
                })
            }
            cache!!.offer(view)
        }
    }

    private fun freeAllLayouts() {
        val size = foldableItemsMap.size()
        for (i in 0 until size) {
            val layout = foldableItemsMap.valueAt(i)
            layout.getBaseLayout().removeAllViews() // Clearing old data
            foldableItemsCache.offer(layout)
        }
        foldableItemsMap.clear()
    }

    /**
     * Returns position of the main visible item.
     */
    // Public API
    val position: Int
        get() = Math.round(foldRotation / 180f)

    fun scrollToPosition(index: Int) {
        var index = index
        index = Math.max(0, Math.min(index, count - 1))
        animateFold(index * 180f)
    }

    protected fun scrollToNearestPosition() {
        scrollToPosition(((getFoldRotation() + 90f) / 180f).toInt())
    }

    protected fun animateFold(to: Float) {
        val from = getFoldRotation()
        val duration = Math.abs(ANIMATION_DURATION_PER_ITEM * (to - from) / 180f).toLong()
        flingAnimation!!.stop()
        animator!!.cancel()
        animator!!.setFloatValues(from, to)
        animator!!.duration = duration
        animator!!.start()
    }

    private fun processTouch(event: MotionEvent): Boolean {
        // Checking if that event was already processed
        // (by onInterceptTouchEvent prior to onTouchEvent)
        val eventTime = event.eventTime
        val action = event.actionMasked
        if (lastTouchEventTime == eventTime && lastTouchEventAction == action) {
            return lastTouchEventResult
        }
        lastTouchEventTime = eventTime
        lastTouchEventAction = action
        if (count > 0) {
            // Fixing event's Y position due to performed translation
            val eventCopy = MotionEvent.obtain(event)
            eventCopy.offsetLocation(0f, translationY)
            lastTouchEventResult = gestureDetector!!.onTouchEvent(eventCopy)
            eventCopy.recycle()
        } else {
            lastTouchEventResult = false
        }
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            onUpOrCancel()
        }
        return lastTouchEventResult
    }

    private fun onDown(): Boolean {
        isScrollDetected = false
        animator!!.cancel()
        flingAnimation!!.stop()
        return false
    }

    private fun onUpOrCancel() {
        if (!flingAnimation!!.isAnimating) {
            scrollToNearestPosition()
        }
    }

    private fun onScroll(firstEvent: MotionEvent, moveEvent: MotionEvent): Boolean {
        if (!isScrollDetected && (height) != 0 && Math.abs(firstEvent.y - moveEvent.y) > minDistanceBeforeScroll) {
            isScrollDetected = true
            scrollStartRotation = getFoldRotation()
            scrollStartY = moveEvent.y
        }
        if (isScrollDetected) {
            val distance = scrollStartY - moveEvent.y
            val rotation = 180f * scrollFactor * distance / height
            setFoldRotation(scrollStartRotation + rotation, true)
        }
        return isScrollDetected
    }

    private fun onFling(velocityY: Float): Boolean {
        if (height == 0) {
            return false
        }
        var velocity = -velocityY / height * 180f
        velocity = Math.max(MIN_FLING_VELOCITY, Math.abs(velocity)) * Math.signum(velocity)
        return flingAnimation!!.fling(velocity)
    }

    private inner class FlingAnimation : Runnable {
        var isAnimating = false
            private set
        private var lastTime: Long = 0
        private var velocity = 0f
        private var min = 0f
        private var max = 0f
        override fun run() {
            val now = System.currentTimeMillis()
            val delta = velocity / 1000f * (now - lastTime)
            lastTime = now
            var rotation = getFoldRotation()
            rotation = Math.max(min, Math.min(rotation + delta, max))
            setFoldRotation(rotation)
            if (rotation != min && rotation != max) {
                startInternal()
            } else {
                stop()
            }
        }

        private fun startInternal() {
            Utils.postOnAnimation(this@FoldableListLayout, this)
            isAnimating = true
        }

        fun stop() {
            removeCallbacks(this)
            isAnimating = false
        }

        fun fling(velocity: Float): Boolean {
            val rotation = getFoldRotation()
            if (rotation % 180f == 0f) {
                return false
            }
            val position = (rotation / 180f).toInt()
            lastTime = System.currentTimeMillis()
            this.velocity = velocity
            min = position * 180f
            max = min + 180f
            startInternal()
            return true
        }
    }

    interface OnFoldRotationListener {
        fun onFoldRotation(rotation: Float, isFromUser: Boolean)
    }

    companion object {
        private const val ANIMATION_DURATION_PER_ITEM = 600L
        private const val MIN_FLING_VELOCITY = 600f
        private const val DEFAULT_SCROLL_FACTOR = 1.33f
        private val PARAMS = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        private const val MAX_CHILDREN_COUNT = 3
    }
}