package com.xingin.widgets

import android.annotation.SuppressLint
import android.app.Activity
import android.text.InputType
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.View.OnKeyListener
import android.view.ViewGroup
import android.widget.FrameLayout
import kotlinx.android.synthetic.main.widgets_view_global_loading.view.*

@SuppressLint("ViewConstructor")
/**
 * Created by susion on 2018/12/15.
 *
 * @param activity view的context
 * @param autoAttachHost 默认在展示loading时会添加到activity的 android.R.id.content上。如果为false，则可以自定义loading展示的位置
 */
class GlobalLoadingView(private val activity: Activity, private val autoAttachHost: Boolean = true) : FrameLayout(activity) {

    private var isShowing = false

    init {
        LayoutInflater.from(context).inflate(R.layout.widgets_view_global_loading, this)
        mGlobalLoadingListenBackKeyPressEditText.inputType = InputType.TYPE_NULL
        mGlobalLoadingListenBackKeyPressEditText.setOnKeyListener(OnKeyListener { v, keyCode, event ->
            if (event?.keyCode == KeyEvent.KEYCODE_BACK && isShowing) {
                hide()
            }
            return@OnKeyListener true
        })
    }

    /**
     * 展示全局 loading,
     *
     * autoAttachHost 设置为true 会尝试 attach在 android.R.content 上。 否则会设置为可见，并播放动画
     * */
    fun show() {
        if (activity.isFinishing || activity.isDestroyed) return

        if (autoAttachHost) {
            attachLoadingViewAndShowAnimation()
        } else {
            if (parent != null) {    //这个 view 已经被加入到当前 view tree中
                visibility = View.VISIBLE
                innerShow()
            }
        }
    }

    /**
     * 隐藏全局 loading,
     *
     * autoAttachHost 设置为true 会尝试 detach from  android.R.content 上。 否则会设置为GONE，并停止动画
     * */
    fun hide() {
        if (activity.isFinishing || activity.isDestroyed) return

        if (autoAttachHost) {
            detachLoadingViewAndHideAnimation()
        } else {
            if (parent != null) {
                visibility = View.GONE
                innerHide()
            }
        }
    }

    private fun detachLoadingViewAndHideAnimation() {
        val activityContent = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
        if (parent == null) return
        activityContent.removeView(this)
        innerHide()
    }

    private fun attachLoadingViewAndShowAnimation() {
        val activityContent = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
        if (parent != null) return
        activityContent.addView(this)
        innerShow()
    }

    private fun innerShow() {
        isShowing = true
        mGlobalLoadingListenBackKeyPressEditText.requestFocus()
        try {
            mGlobalLoadingAnimationView.playAnimation()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun innerHide() {
        try {
            mGlobalLoadingAnimationView.pauseAnimation()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        isShowing = false
    }
}