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

package io.github.floatbro.core.common.util

import android.content.Context
import android.content.res.Resources
import android.util.TypedValue

object DisplayUtils {
    fun dpToPx(
        dp: Float,
        context: Context,
    ): Int =
        TypedValue
            .applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                dp,
                context.resources.displayMetrics,
            ).toInt()

    fun pxToDp(px: Int): Float = px / Resources.getSystem().displayMetrics.density
}
