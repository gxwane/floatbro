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
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import io.github.floatbro.core.webview.EBWebView

/**
 * Symmetric Viewport Container wrapping an EBWebView with an integrated
 * mini toolbar, address input, and progress indicator.
 */
@SuppressLint("SetTextI18n")
class PaneContainer @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    val webView: EBWebView = EBWebView(context)
    val urlInput: EditText = EditText(context)
    val progressBar: ProgressBar = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal)
    
    val btnBack: ImageButton = ImageButton(context)
    val btnForward: ImageButton = ImageButton(context)
    val btnSendToOther: ImageButton = ImageButton(context)
    val btnClosePane: ImageButton = ImageButton(context)

    var onSendUrlToOtherPaneRequested: ((url: String) -> Unit)? = null
    var onClosePaneRequested: (() -> Unit)? = null
    var onInputFocusChanged: ((Boolean) -> Unit)? = null

    init {
        orientation = VERTICAL
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        setBackgroundColor(Color.WHITE)

        initToolbar()
        initProgressBar()
        initWebView()
    }

    private fun dpToPx(dp: Float): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp,
            context.resources.displayMetrics
        ).toInt()
    }

    private fun initToolbar() {
        val toolbar = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dpToPx(4f), dpToPx(4f), dpToPx(4f), dpToPx(4f))
            setBackgroundColor(Color.parseColor("#F5F5F5"))
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dpToPx(42f))
        }

        // Back Button
        btnBack.apply {
            layoutParams = LayoutParams(dpToPx(32f), dpToPx(32f))
            setBackgroundColor(Color.TRANSPARENT)
            setImageResource(android.R.drawable.ic_media_previous)
            setOnClickListener { if (webView.canGoBack()) webView.goBack() }
        }

        // Forward Button
        btnForward.apply {
            layoutParams = LayoutParams(dpToPx(32f), dpToPx(32f))
            setBackgroundColor(Color.TRANSPARENT)
            setImageResource(android.R.drawable.ic_media_next)
            setOnClickListener { if (webView.canGoForward()) webView.goForward() }
        }

        // URL Input
        urlInput.apply {
            layoutParams = LayoutParams(0, dpToPx(34f), 1f).apply {
                marginStart = dpToPx(4f)
                marginEnd = dpToPx(4f)
            }
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = dpToPx(16f).toFloat()
                setStroke(dpToPx(1f), Color.parseColor("#E0E0E0"))
            }
            setPadding(dpToPx(10f), 0, dpToPx(10f), 0)
            textSize = 12f
            setSingleLine(true)
            imeOptions = EditorInfo.IME_ACTION_GO
            hint = "输入网址或搜索内容"
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_SEARCH) {
                    hideKeyboard()
                    loadUrl(text.toString())
                    true
                } else {
                    false
                }
            }
            setOnFocusChangeListener { _, hasFocus ->
                onInputFocusChanged?.invoke(hasFocus)
            }
        }

        // Send to Other Pane Button (Cross-pane Dispatch)
        btnSendToOther.apply {
            layoutParams = LayoutParams(dpToPx(32f), dpToPx(32f))
            setBackgroundColor(Color.TRANSPARENT)
            setImageResource(android.R.drawable.ic_menu_share)
            tooltipText = "在对侧视口打开"
            setOnClickListener {
                val currentUrl = webView.url ?: urlInput.text.toString()
                if (currentUrl.isNotEmpty()) {
                    onSendUrlToOtherPaneRequested?.invoke(currentUrl)
                }
            }
        }

        // Close Pane Button
        btnClosePane.apply {
            layoutParams = LayoutParams(dpToPx(32f), dpToPx(32f))
            setBackgroundColor(Color.TRANSPARENT)
            setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            tooltipText = "关闭此分屏"
            visibility = View.GONE
            setOnClickListener { onClosePaneRequested?.invoke() }
        }

        toolbar.addView(btnBack)
        toolbar.addView(btnForward)
        toolbar.addView(urlInput)
        toolbar.addView(btnSendToOther)
        toolbar.addView(btnClosePane)

        addView(toolbar)
    }

    private fun initProgressBar() {
        progressBar.apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dpToPx(2f))
            max = 100
            progress = 0
            visibility = GONE
        }
        addView(progressBar)
    }

    private fun initWebView() {
        webView.apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f)

            onProgressChangedListener = { progress ->
                if (progress in 1..99) {
                    progressBar.visibility = VISIBLE
                    progressBar.progress = progress
                } else {
                    progressBar.visibility = GONE
                }
            }

            onPageStartedListener = { url ->
                urlInput.setText(url)
            }

            onPageFinishedListener = { url ->
                urlInput.setText(url)
            }

            onInputFocusListener = { isFocused ->
                onInputFocusChanged?.invoke(isFocused)
            }
        }
        addView(webView)
    }

    fun loadUrl(url: String) {
        webView.loadUrlSafely(url)
    }

    private fun hideKeyboard() {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(urlInput.windowToken, 0)
    }
}
