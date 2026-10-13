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

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.edsuns.adfilter.AdFilter
import io.github.floatbro.ui.theme.*

/**
 * 广告过滤规则管理面板
 * 纯粹收敛：规则启用列表 (基础/中文增强/隐私防追踪) + 底部折叠自定义规则
 */
@Composable
fun AdBlockRulesDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val adFilter = remember {
        try {
            AdFilter.get(context)
        } catch (_: Exception) {
            null
        }
    }

    var isEasyListEnabled by remember {
        mutableStateOf(adFilter?.isRulePackEnabled(AdFilter.PACK_EASYLIST) ?: true)
    }
    var isChinaEnabled by remember {
        mutableStateOf(adFilter?.isRulePackEnabled(AdFilter.PACK_CHINA) ?: true)
    }
    var isPrivacyEnabled by remember {
        mutableStateOf(adFilter?.isRulePackEnabled(AdFilter.PACK_PRIVACY) ?: true)
    }

    val customFilter = remember {
        try {
            adFilter?.customFilter
        } catch (_: Exception) {
            null
        }
    }

    var customRules by remember {
        val list = mutableListOf<String>()
        if (customFilter != null) {
            for (i in 0 until customFilter.size()) {
                val r = customFilter.get(i).trim()
                if (r.isNotEmpty() && !customFilter.isComment(r)) {
                    list.add(r)
                }
            }
        }
        mutableStateOf<List<String>>(list)
    }
    var isCustomExpanded by remember { mutableStateOf(false) }
    var newRuleInput by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "规则管理",
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
                color = Slate900
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // 1. 基础过滤规则 (EasyList)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "基础过滤规则",
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.5.sp,
                        color = Slate900
                    )
                    Switch(
                        checked = isEasyListEnabled,
                        onCheckedChange = {
                            isEasyListEnabled = it
                            adFilter?.setRulePackEnabled(AdFilter.PACK_EASYLIST, it)
                        }
                    )
                }

                HorizontalDivider(color = Slate100)

                // 2. 中文增强规则 (EasyList China)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "中文增强规则",
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.5.sp,
                        color = Slate900
                    )
                    Switch(
                        checked = isChinaEnabled,
                        onCheckedChange = {
                            isChinaEnabled = it
                            adFilter?.setRulePackEnabled(AdFilter.PACK_CHINA, it)
                        }
                    )
                }

                HorizontalDivider(color = Slate100)

                // 3. 隐私防追踪 (EasyPrivacy)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "隐私防追踪",
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.5.sp,
                        color = Slate900
                    )
                    Switch(
                        checked = isPrivacyEnabled,
                        onCheckedChange = {
                            isPrivacyEnabled = it
                            adFilter?.setRulePackEnabled(AdFilter.PACK_PRIVACY, it)
                        }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = Slate200)

                // 4. 自定义规则 (收敛在底部，可展开)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isCustomExpanded = !isCustomExpanded }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "自定义规则 (${customRules.size})",
                        fontWeight = FontWeight.Medium,
                        fontSize = 13.5.sp,
                        color = Slate700
                    )
                    Text(
                        text = if (isCustomExpanded) "收起" else "展开",
                        fontSize = 12.sp,
                        color = BrandBlue,
                        fontWeight = FontWeight.Medium
                    )
                }

                if (isCustomExpanded) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = newRuleInput,
                                onValueChange = { newRuleInput = it },
                                placeholder = { Text("输入规则，如 ||example.com^", fontSize = 12.sp) },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp)
                            )
                            Button(
                                onClick = {
                                    val trimmed = newRuleInput.trim()
                                    if (trimmed.isNotBlank() && customFilter != null) {
                                        if (!customFilter.contains(trimmed)) {
                                            customFilter.append(trimmed)
                                            customFilter.flush()
                                            customRules = customRules + trimmed
                                            newRuleInput = ""
                                            Toast.makeText(context, "已添加规则", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, "规则已存在", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("添加", fontSize = 12.sp)
                            }
                        }

                        if (customRules.isNotEmpty()) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                customRules.forEach { rule ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(Slate50, RoundedCornerShape(6.dp))
                                            .padding(horizontal = 8.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = rule,
                                            fontSize = 11.5.sp,
                                            fontFamily = FontFamily.Monospace,
                                            color = Slate800,
                                            modifier = Modifier.weight(1f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        IconButton(
                                            onClick = {
                                                if (customFilter != null) {
                                                    customFilter.remove(rule)
                                                    customFilter.flush()
                                                    customRules = customRules.filter { it != rule }
                                                    Toast.makeText(context, "已移除规则", Toast.LENGTH_SHORT).show()
                                                }
                                            },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "删除",
                                                tint = Slate400,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            TextButton(
                                onClick = {
                                    if (customFilter != null) {
                                        customRules.forEach { customFilter.remove(it) }
                                        customFilter.flush()
                                        customRules = emptyList()
                                        Toast.makeText(context, "已清空自定义规则", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.align(Alignment.End)
                            ) {
                                Text("清空自定义规则", color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("完成", color = BrandBlue, fontWeight = FontWeight.Bold)
            }
        },
        containerColor = Color.White,
        shape = RoundedCornerShape(16.dp)
    )
}
