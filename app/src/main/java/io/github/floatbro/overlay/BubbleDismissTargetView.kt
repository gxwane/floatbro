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

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.WindowManager
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import io.github.floatbro.R

/**
 * 64dp Circular dismiss target view positioned at bottom-center of the screen
 * when a floating bubble is dragged. Provides visual hover and magnetic feedback.
 */
@SuppressLint("ViewConstructor")
class BubbleDismissTargetView(
    context: Context,
    private val windowManager: WindowManager,
) : FrameLayout(context) {
    val layoutParams: WindowManager.LayoutParams
    private val backgroundDrawable: GradientDrawable
    private val iconView: ImageView

    private val targetSizePx: Int = dpToPx(64f)
    private val iconSizePx: Int = dpToPx(28f)
    private var isTargetHovered = false
    private var isTargetVisible = false

    init {
        layoutParams =
            WindowManager
                .LayoutParams(
                    targetSizePx,
                    targetSizePx,
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    } else {
                        @Suppress("DEPRECATION")
                        WindowManager.LayoutParams.TYPE_PHONE
                    },
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.TRANSLUCENT,
                ).apply {
                    gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                    y = dpToPx(48f)
                }

        backgroundDrawable =
            GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#40EF4444")) // Semi-transparent red
                setStroke(dpToPx(1.5f), Color.parseColor("#EF4444"))
            }
        background = backgroundDrawable
        elevation = dpToPx(16f).toFloat()

        iconView =
            ImageView(context).apply {
                layoutParams = LayoutParams(iconSizePx, iconSizePx, Gravity.CENTER)
                setImageResource(R.drawable.ic_trash_bin)
                scaleType = ImageView.ScaleType.FIT_CENTER
            }
        addView(iconView)

        alpha = 0f
        scaleX = 0.7f
        scaleY = 0.7f
    }

    private fun dpToPx(dp: Float): Int =
        TypedValue
            .applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                dp,
                context.resources.displayMetrics,
            ).toInt()

    fun showTarget() {
        if (isTargetVisible) return
        isTargetVisible = true
        animate().cancel()
        setHovered(false)
        animate()
            .alpha(1.0f)
            .scaleX(1.0f)
            .scaleY(1.0f)
            .setDuration(180)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    fun hideTarget() {
        if (!isTargetVisible) return
        isTargetVisible = false
        animate().cancel()
        animate()
            .alpha(0f)
            .scaleX(0.7f)
            .scaleY(0.7f)
            .setDuration(150)
            .setInterpolator(AccelerateInterpolator())
            .start()
    }

    override fun setHovered(hovered: Boolean) {
        if (isTargetHovered == hovered) return
        isTargetHovered = hovered
        super.setHovered(hovered)

        if (hovered) {
            backgroundDrawable.setColor(Color.parseColor("#DC2626")) // Solid deep red
            backgroundDrawable.setStroke(dpToPx(2.5f), Color.WHITE)
            animate()
                .scaleX(1.25f)
                .scaleY(1.25f)
                .setDuration(120)
                .setInterpolator(DecelerateInterpolator())
                .start()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                performHapticFeedback(HapticFeedbackConstants.CONFIRM)
            } else {
                performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            }
        } else {
            backgroundDrawable.setColor(Color.parseColor("#40EF4444"))
            backgroundDrawable.setStroke(dpToPx(1.5f), Color.parseColor("#EF4444"))
            animate()
                .scaleX(1.0f)
                .scaleY(1.0f)
                .setDuration(120)
                .setInterpolator(DecelerateInterpolator())
                .start()
        }
    }

    fun getTargetCenter(): Pair<Float, Float> {
        val displayMetrics = context.resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels.toFloat()
        val screenHeight = displayMetrics.heightPixels.toFloat()
        val centerX = screenWidth / 2f
        val centerY = screenHeight - layoutParams.y.toFloat() - (targetSizePx / 2f)
        return Pair(centerX, centerY)
    }
}
