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

package io.github.floatbro.core.webview.twopane

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.util.TypedValue
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout

/**
 * High-performance Symmetric Dual-Pane Layout.
 * Supports Windows Snap-style draggable splitter bar, horizontal/vertical switching,
 * clamp boundaries (15% ~ 85%), double-tap 50:50 reset, and cross-pane dispatch.
 */
class TwoPaneLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    enum class Orientation {
        HORIZONTAL, // Side-by-side
        VERTICAL    // Top-and-bottom
    }

    var splitOrientation: Orientation = Orientation.HORIZONTAL
        set(value) {
            if (field != value) {
                field = value
                updateSplitterAppearance()
                requestLayout()
            }
        }

    var splitRatio: Float = 0.5f
        set(value) {
            field = value.coerceIn(0.15f, 0.85f)
            requestLayout()
        }

    var isSecondPaneVisible: Boolean = false
        set(value) {
            field = value
            pane2.btnClosePane.visibility = if (value) View.VISIBLE else View.GONE
            requestLayout()
        }

    val pane1: PaneContainer = PaneContainer(context)
    val pane2: PaneContainer = PaneContainer(context)

    private val separator: View = View(context).apply {
        setBackgroundColor(Color.parseColor("#BDBDBD"))
    }

    private val floatingGuideLine: View = View(context).apply {
        setBackgroundColor(Color.parseColor("#2196F3"))
        visibility = GONE
    }

    private val dragHandle: View = View(context).apply {
        background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dpToPx(8f).toFloat()
            setColor(Color.parseColor("#757575"))
        }
        alpha = 0.6f
    }

    var onInputFocusChanged: ((Boolean) -> Unit)? = null

    private val doubleTapDetector: GestureDetector

    init {
        addView(pane1)
        addView(pane2)
        addView(separator)
        addView(floatingGuideLine)
        addView(dragHandle)

        setupPaneCallbacks()
        doubleTapDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                // Double tap reset to 50:50
                splitRatio = 0.5f
                return true
            }
        })

        initDragHandleTouchListener()
        updateSplitterAppearance()
    }

    private fun dpToPx(dp: Float): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp,
            context.resources.displayMetrics
        ).toInt()
    }

    private fun setupPaneCallbacks() {
        pane1.onSendUrlToOtherPaneRequested = { url ->
            if (!isSecondPaneVisible) {
                isSecondPaneVisible = true
            }
            pane2.loadUrl(url)
        }

        pane2.onSendUrlToOtherPaneRequested = { url ->
            pane1.loadUrl(url)
        }

        pane2.onClosePaneRequested = {
            isSecondPaneVisible = false
        }

        pane1.onInputFocusChanged = { isFocused -> onInputFocusChanged?.invoke(isFocused) }
        pane2.onInputFocusChanged = { isFocused -> onInputFocusChanged?.invoke(isFocused) }
    }

    private fun updateSplitterAppearance() {
        if (splitOrientation == Orientation.HORIZONTAL) {
            separator.layoutParams = LayoutParams(dpToPx(2f), LayoutParams.MATCH_PARENT)
            floatingGuideLine.layoutParams = LayoutParams(dpToPx(3f), LayoutParams.MATCH_PARENT)
            dragHandle.layoutParams = LayoutParams(dpToPx(16f), dpToPx(48f))
        } else {
            separator.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dpToPx(2f))
            floatingGuideLine.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dpToPx(3f))
            dragHandle.layoutParams = LayoutParams(dpToPx(48f), dpToPx(16f))
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun initDragHandleTouchListener() {
        var startTouch = 0f
        var initialRatio = 0.5f

        dragHandle.setOnTouchListener { v, event ->
            doubleTapDetector.onTouchEvent(event)

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    dragHandle.alpha = 1.0f
                    floatingGuideLine.visibility = VISIBLE
                    floatingGuideLine.bringToFront()
                    dragHandle.bringToFront()

                    if (splitOrientation == Orientation.HORIZONTAL) {
                        startTouch = event.rawX
                    } else {
                        startTouch = event.rawY
                    }
                    initialRatio = splitRatio
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (splitOrientation == Orientation.HORIZONTAL) {
                        val deltaX = event.rawX - startTouch
                        val newRatio = initialRatio + (deltaX / width.toFloat())
                        splitRatio = newRatio
                    } else {
                        val deltaY = event.rawY - startTouch
                        val newRatio = initialRatio + (deltaY / height.toFloat())
                        splitRatio = newRatio
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    dragHandle.alpha = 0.6f
                    floatingGuideLine.visibility = GONE
                    true
                }
                else -> false
            }
        }
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val w = right - left
        val h = bottom - top

        if (w <= 0 || h <= 0) return

        if (!isSecondPaneVisible) {
            pane1.visibility = VISIBLE
            pane1.layout(0, 0, w, h)
            pane2.visibility = GONE
            separator.visibility = GONE
            dragHandle.visibility = GONE
            floatingGuideLine.visibility = GONE
            return
        }

        pane1.visibility = VISIBLE
        pane2.visibility = VISIBLE
        separator.visibility = VISIBLE
        dragHandle.visibility = VISIBLE

        if (splitOrientation == Orientation.HORIZONTAL) {
            val splitX = (w * splitRatio).toInt()
            val sepWidth = dpToPx(2f)

            pane1.layout(0, 0, splitX, h)
            pane2.layout(splitX + sepWidth, 0, w, h)

            separator.layout(splitX, 0, splitX + sepWidth, h)

            val handleW = dragHandle.measuredWidth
            val handleH = dragHandle.measuredHeight
            val handleLeft = splitX + (sepWidth - handleW) / 2
            val handleTop = (h - handleH) / 2
            dragHandle.layout(handleLeft, handleTop, handleLeft + handleW, handleTop + handleH)

            floatingGuideLine.layout(splitX, 0, splitX + dpToPx(3f), h)
        } else {
            val splitY = (h * splitRatio).toInt()
            val sepHeight = dpToPx(2f)

            pane1.layout(0, 0, w, splitY)
            pane2.layout(0, splitY + sepHeight, w, h)

            separator.layout(0, splitY, w, splitY + sepHeight)

            val handleW = dragHandle.measuredWidth
            val handleH = dragHandle.measuredHeight
            val handleLeft = (w - handleW) / 2
            val handleTop = splitY + (sepHeight - handleH) / 2
            dragHandle.layout(handleLeft, handleTop, handleLeft + handleW, handleTop + handleH)

            floatingGuideLine.layout(0, splitY, w, splitY + dpToPx(3f))
        }

        separator.bringToFront()
        dragHandle.bringToFront()
    }

    fun swapPanes() {
        val url1 = pane1.webView.url ?: pane1.urlInput.text.toString()
        val url2 = pane2.webView.url ?: pane2.urlInput.text.toString()

        if (url1.isNotEmpty()) pane2.loadUrl(url1)
        if (url2.isNotEmpty()) pane1.loadUrl(url2)
    }

    fun resetSplitRatio() {
        splitRatio = 0.5f
    }
}
