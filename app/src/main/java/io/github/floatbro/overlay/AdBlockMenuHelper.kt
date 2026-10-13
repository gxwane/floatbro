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

/**
 * 悬浮窗广告拦截菜单状态机解析器
 * 严格区分“全局总开关关闭”与“单站点白名单”，杜绝语义混淆与无效点击循环
 */
object AdBlockMenuHelper {
    const val TITLE_GLOBAL_DISABLED = "广告拦截: 全局已关闭 (去设置开启)"
    const val TITLE_SITE_WHITELISTED = "广告拦截: 已加白名单 (点击恢复拦截)"
    const val TITLE_SITE_ENABLED = "广告拦截: 已开启 (点击加白名单)"

    fun resolveMenuTitle(isGlobalEnabled: Boolean, isSiteWhitelisted: Boolean): String {
        return when {
            !isGlobalEnabled -> TITLE_GLOBAL_DISABLED
            isSiteWhitelisted -> TITLE_SITE_WHITELISTED
            else -> TITLE_SITE_ENABLED
        }
    }
}
