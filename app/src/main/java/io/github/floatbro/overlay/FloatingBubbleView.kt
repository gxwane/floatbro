/*
 * Copyright (c) 2026 FloatBro Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.floatbro.overlay

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import io.github.floatbro.R
import kotlin.math.hypot

/**
 * 56dp circular floating bubble (chat-head style) with drag-to-move
 * and smooth magnetic spring edge docking.
 */
@SuppressLint("ClickableViewAccessibility")
class FloatingBubbleView(
    context: Context,
    private val windowManager: WindowManager,
    private val layoutParams: WindowManager.LayoutParams
) : FrameLayout(context) {

    var onBubbleClicked: (() -> Unit)? = null
    var onPositionChanged: ((x: Int, y: Int) -> Unit)? = null
    var onDismissRequested: (() -> Unit)? = null

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var isDragging = false

    private val iconView: ImageView

    init {
        val sizePx = dpToPx(52f)
        val iconSizePx = dpToPx(30f)

        // Pure circular container: #FFFFFF background, 2dp #2563EB border (Matching Prototype .edge-bubble)
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.WHITE)
            setStroke(dpToPx(2f), Color.parseColor("#2563EB"))
        }
        elevation = dpToPx(12f).toFloat()

        iconView = ImageView(context).apply {
            layoutParams = LayoutParams(iconSizePx, iconSizePx, Gravity.CENTER)
            setImageResource(R.drawable.ic_bubble_logo)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        addView(iconView)
        alpha = 0.85f

        setOnTouchListener { _, event ->
            handleTouchEvent(event)
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val sizePx = dpToPx(52f)
        super.onMeasure(
            MeasureSpec.makeMeasureSpec(sizePx, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(sizePx, MeasureSpec.EXACTLY)
        )
    }

    fun updateFavicon(favicon: Bitmap?) {
        if (favicon != null) {
            iconView.setImageBitmap(favicon)
        } else {
            iconView.setImageResource(R.drawable.ic_bubble_logo)
        }
    }

    private fun dpToPx(dp: Float): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp,
            context.resources.displayMetrics
        ).toInt()
    }

    private fun handleTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                initialX = layoutParams.x
                initialY = layoutParams.y
                initialTouchX = event.rawX
                initialTouchY = event.rawY
                isDragging = false
                animate().scaleX(0.92f).scaleY(0.92f).alpha(1.0f).setDuration(100).start()
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - initialTouchX
                val dy = event.rawY - initialTouchY

                if (!isDragging && hypot(dx.toDouble(), dy.toDouble()) > touchSlop) {
                    isDragging = true
                }

                if (isDragging) {
                    layoutParams.x = (initialX + dx).toInt()
                    layoutParams.y = (initialY + dy).toInt()
                    windowManager.updateViewLayout(this, layoutParams)
                    onPositionChanged?.invoke(layoutParams.x, layoutParams.y)
                }
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).start()
                if (!isDragging) {
                    // Tap -> expand window
                    onBubbleClicked?.invoke()
                } else {
                    val displayMetrics = context.resources.displayMetrics
                    val dismissThreshold = displayMetrics.heightPixels - dpToPx(100f)
                    if (layoutParams.y >= dismissThreshold) {
                        onDismissRequested?.invoke()
                    } else {
                        // Release -> magnetic snap to nearest screen edge
                        snapToEdge()
                    }
                }
                return true
            }
        }
        return false
    }

    private fun snapToEdge() {
        val displayMetrics = context.resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val bubbleWidth = width.takeIf { it > 0 } ?: dpToPx(52f)

        val currentX = layoutParams.x
        val centerX = currentX + bubbleWidth / 2
        val tuckDepth = dpToPx(18f)

        val targetX = if (centerX < screenWidth / 2) {
            -tuckDepth
        } else {
            screenWidth - bubbleWidth + tuckDepth
        }

        // Clamp Y to safe screen bounds (avoid status bar and gesture navigation bar)
        val minY = dpToPx(36f)
        val maxY = displayMetrics.heightPixels - dpToPx(72f) - bubbleWidth
        layoutParams.y = layoutParams.y.coerceIn(minY, maxY)

        val animator = ValueAnimator.ofInt(currentX, targetX)
        animator.duration = 240
        animator.interpolator = DecelerateInterpolator()
        animator.addUpdateListener { animation ->
            layoutParams.x = animation.animatedValue as Int
            if (isAttachedToWindow) {
                windowManager.updateViewLayout(this, layoutParams)
                onPositionChanged?.invoke(layoutParams.x, layoutParams.y)
            }
        }
        animator.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                animate().alpha(0.85f).setDuration(200).start()
            }
        })
        animator.start()
    }
}
