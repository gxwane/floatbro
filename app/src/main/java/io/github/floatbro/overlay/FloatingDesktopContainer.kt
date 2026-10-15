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

import android.content.Context
import android.graphics.Rect
import android.view.View
import android.widget.FrameLayout
import timber.log.Timber

/**
 * Single-Host-Window Desktop Container for Multi-Floating-Window Architecture (Solution B).
 *
 * Hosts all active [FloatingWindowView] instances inside a single WindowManager Surface.
 * - Changing Z-order (focus switching) executes via pure View-tree [bringToFront] (0ms, ZERO flicker).
 * - Empty non-window regions pass through 100% to underlying apps via [InternalInsetsHelper].
 */
class FloatingDesktopContainer(
    context: Context,
) : FrameLayout(context) {
    var isInsetsProxyActive: Boolean = false
        private set

    init {
        clipChildren = false
        clipToPadding = false
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInsetsProxyActive =
            InternalInsetsHelper.setupTouchableRegion(this) { region ->
                for (i in 0 until childCount) {
                    val child = getChildAt(i)
                    if (child.visibility != VISIBLE) continue
                    val lp = child.layoutParams as? LayoutParams
                    val x = lp?.leftMargin ?: child.left
                    val y = lp?.topMargin ?: child.top
                    val w = if (lp != null && lp.width > 0) lp.width else child.width
                    val h = if (lp != null && lp.height > 0) lp.height else child.height
                    if (w > 0 && h > 0) {
                        region.union(Rect(x, y, x + w, y + h))
                    }
                }
            }
        Timber.i("FloatingDesktopContainer: attached to window (insetsProxyActive=$isInsetsProxyActive)")
    }

    /**
     * Adds a floating window child with initial absolute coordinates and dimensions.
     */
    fun attachChildWindow(
        child: View,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
    ) {
        if (child.parent === this) {
            updateChildBounds(child, x, y, width, height)
            return
        }
        (child.parent as? android.view.ViewGroup)?.removeView(child)
        val lp =
            LayoutParams(width, height).apply {
                leftMargin = x
                topMargin = y
            }
        addView(child, lp)
        requestLayout()
        invalidate()
    }

    /**
     * Updates layout parameters for an existing child window.
     */
    fun updateChildBounds(
        child: View,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
    ) {
        val lp = child.layoutParams as? LayoutParams ?: LayoutParams(width, height)
        lp.leftMargin = x
        lp.topMargin = y
        lp.width = width
        lp.height = height
        child.layoutParams = lp
        child.requestLayout()
        requestLayout()
        invalidate()
    }

    /**
     * Brings the child window to the top of the container's Z-order hierarchy.
     * Pure in-memory View-tree elevation: ZERO calls to WindowManager, ZERO surface destruction!
     */
    fun bringChildWindowToFront(child: View) {
        if (indexOfChild(child) != -1 && getChildAt(childCount - 1) !== child) {
            child.bringToFront()
            requestLayout()
            invalidate()
            Timber.d("FloatingDesktopContainer: Elevated child window via pure in-memory bringToFront")
        }
    }

    override fun onTouchEvent(event: android.view.MotionEvent): Boolean {
        if (event.action == android.view.MotionEvent.ACTION_OUTSIDE) {
            for (i in 0 until childCount) {
                val child = getChildAt(i)
                if (child is FloatingWindowView && child.isEditMode) {
                    child.exitEditMode()
                }
            }
        }
        return super.onTouchEvent(event)
    }

    /**
     * Detaches a floating window child from the desktop container.
     */
    fun detachChildWindow(child: View) {
        removeView(child)
        requestLayout()
        invalidate()
    }

    override fun onMeasure(
        widthMeasureSpec: Int,
        heightMeasureSpec: Int,
    ) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child.visibility == View.GONE) continue
            val lp = child.layoutParams as? LayoutParams
            if (lp != null && lp.width > 0 && lp.height > 0) {
                child.measure(
                    MeasureSpec.makeMeasureSpec(lp.width, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(lp.height, MeasureSpec.EXACTLY),
                )
            } else {
                measureChild(child, widthMeasureSpec, heightMeasureSpec)
            }
        }
    }

    override fun onLayout(
        changed: Boolean,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
    ) {
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child.visibility == View.GONE) continue
            val lp = child.layoutParams as? LayoutParams
            val x = lp?.leftMargin ?: 0
            val y = lp?.topMargin ?: 0
            val w = if (lp != null && lp.width > 0) lp.width else child.measuredWidth
            val h = if (lp != null && lp.height > 0) lp.height else child.measuredHeight
            child.layout(x, y, x + w, y + h)
        }
    }

    /**
     * Computes the bounding box covering all currently visible child windows.
     */
    fun computeChildrenBoundingBox(): Rect? {
        var minX = Int.MAX_VALUE
        var minY = Int.MAX_VALUE
        var maxX = Int.MIN_VALUE
        var maxY = Int.MIN_VALUE
        var hasVisibleChildren = false

        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child.visibility != VISIBLE) continue
            val lp = child.layoutParams as? LayoutParams
            val x = lp?.leftMargin ?: child.left
            val y = lp?.topMargin ?: child.top
            val w = if (lp != null && lp.width > 0) lp.width else child.width
            val h = if (lp != null && lp.height > 0) lp.height else child.height
            if (w <= 0 || h <= 0) continue

            hasVisibleChildren = true
            minX = minOf(minX, x)
            minY = minOf(minY, y)
            maxX = maxOf(maxX, x + w)
            maxY = maxOf(maxY, y + h)
        }

        return if (hasVisibleChildren) Rect(minX, minY, maxX, maxY) else null
    }
}
