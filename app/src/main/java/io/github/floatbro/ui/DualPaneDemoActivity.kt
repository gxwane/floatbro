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

package io.github.floatbro.ui

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import androidx.activity.ComponentActivity
import io.github.floatbro.core.webview.controller.PaneController
import io.github.floatbro.core.webview.twopane.TwoPaneLayout

class DualPaneDemoActivity : ComponentActivity() {

    private lateinit var twoPaneLayout: TwoPaneLayout
    private lateinit var controller: PaneController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#EEEEEE"))
        }

        // Top Control Bar
        val controlBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.parseColor("#E0E0E0"))
            setPadding(16, 12, 16, 12)
        }

        val btnToggleOrientation = Button(this).apply {
            text = "横/竖切分"
            setOnClickListener { controller.toggleOrientation() }
        }

        val btnToggleSecond = Button(this).apply {
            text = "开/关副屏"
            setOnClickListener { controller.toggleSecondPane() }
        }

        val btnSwap = Button(this).apply {
            text = "对调两屏"
            setOnClickListener { controller.swapPanes() }
        }

        val btnReset = Button(this).apply {
            text = "50:50复位"
            setOnClickListener { controller.resetRatio() }
        }

        controlBar.addView(btnToggleOrientation)
        controlBar.addView(btnToggleSecond)
        controlBar.addView(btnSwap)
        controlBar.addView(btnReset)

        rootLayout.addView(controlBar)

        // TwoPaneLayout Container
        twoPaneLayout = TwoPaneLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }
        controller = PaneController(twoPaneLayout)

        rootLayout.addView(twoPaneLayout)
        setContentView(rootLayout)

        // Load initial URLs
        controller.openUrlInPane1("https://bing.com")
        twoPaneLayout.isSecondPaneVisible = true
        controller.openUrlInPane2("https://github.com/plateaukao/einkbro")
    }
}
