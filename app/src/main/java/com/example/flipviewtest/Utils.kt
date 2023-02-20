package com.example.flipviewtest

import android.os.Build
import android.view.View


internal object Utils {
    private const val FRAME_TIME = 10L
    fun postOnAnimation(view: View, action: Runnable?) {
        view.removeCallbacks(action)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            view.postOnAnimationDelayed(action, FRAME_TIME)
        } else {
            view.postDelayed(action, FRAME_TIME)
        }
    }
}