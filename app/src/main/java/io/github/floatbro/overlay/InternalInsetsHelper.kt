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

import android.graphics.Region
import android.view.View
import android.view.ViewTreeObserver
import timber.log.Timber
import java.lang.reflect.Proxy

/**
 * Safe reflection helper to dynamically configure non-rectangular touchable regions
 * on a host WindowManager View via [ViewTreeObserver.OnComputeInternalInsetsListener].
 *
 * In fullscreen or composite overlay hosts, touches falling outside the union of child
 * window bounds pass straight through to underlying apps with zero blocking.
 */
object InternalInsetsHelper {
    private const val TOUCHABLE_INSETS_REGION = 3

    /**
     * Attempts to register a dynamic touchable region computer on [view]'s [ViewTreeObserver].
     * Returns true if reflection and proxy registration succeeded, false if restricted.
     */
    fun setupTouchableRegion(
        view: View,
        regionProvider: (Region) -> Unit,
    ): Boolean =
        try {
            val vto = view.viewTreeObserver
            val listenerClass = Class.forName("android.view.ViewTreeObserver\$OnComputeInternalInsetsListener")
            val insetsInfoClass = Class.forName("android.view.ViewTreeObserver\$InternalInsetsInfo")
            val setTouchableInsetsMethod = insetsInfoClass.getMethod("setTouchableInsets", Int::class.javaPrimitiveType)
            val touchableRegionField = insetsInfoClass.getField("touchableRegion")

            val proxy =
                Proxy.newProxyInstance(
                    view.context.classLoader,
                    arrayOf(listenerClass),
                ) { _, method, args ->
                    if (method.name == "onComputeInternalInsets" && args != null && args.isNotEmpty()) {
                        val info = args[0]
                        setTouchableInsetsMethod.invoke(info, TOUCHABLE_INSETS_REGION)
                        val region = touchableRegionField.get(info) as? Region
                        if (region != null) {
                            region.setEmpty()
                            regionProvider(region)
                        }
                    }
                    null
                }

            val addListenerMethod = vto.javaClass.getMethod("addOnComputeInternalInsetsListener", listenerClass)
            addListenerMethod.invoke(vto, proxy)
            Timber.i("InternalInsetsHelper: Successfully attached OnComputeInternalInsetsListener proxy")
            true
        } catch (e: Throwable) {
            Timber.w(e, "InternalInsetsHelper: OnComputeInternalInsetsListener unavailable on this ROM")
            false
        }
}
