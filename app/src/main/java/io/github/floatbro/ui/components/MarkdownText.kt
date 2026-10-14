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

package io.github.floatbro.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

sealed interface MarkdownBlock {
    data class Heading(val level: Int, val text: String) : MarkdownBlock
    data class Paragraph(val text: String) : MarkdownBlock
    data class BulletItem(val level: Int, val text: String) : MarkdownBlock
    data class NumberedItem(val number: String, val text: String) : MarkdownBlock
    data class Quote(val text: String) : MarkdownBlock
    data class CodeBlock(val language: String, val code: String) : MarkdownBlock
}

sealed interface InlineSpan {
    data class Plain(val text: String) : InlineSpan
    data class Bold(val text: String) : InlineSpan
    data class Italic(val text: String) : InlineSpan
    data class Code(val text: String) : InlineSpan
    data class Link(val text: String, val url: String) : InlineSpan
}

object MarkdownParser {

    fun parseBlocks(markdown: String): List<MarkdownBlock> {
        val lines = markdown.lines()
        val blocks = mutableListOf<MarkdownBlock>()
        var inCodeBlock = false
        var codeLanguage = ""
        val codeBuffer = StringBuilder()

        for (line in lines) {
            val trimmed = line.trimEnd()
            if (trimmed.startsWith("```")) {
                if (inCodeBlock) {
                    blocks.add(MarkdownBlock.CodeBlock(codeLanguage, codeBuffer.toString()))
                    codeBuffer.clear()
                    codeLanguage = ""
                    inCodeBlock = false
                } else {
                    inCodeBlock = true
                    codeLanguage = trimmed.removePrefix("```").trim()
                }
                continue
            }

            if (inCodeBlock) {
                codeBuffer.append(line).append("\n")
                continue
            }

            val stripped = line.trim()
            if (stripped.isEmpty()) continue

            when {
                stripped.startsWith("### ") -> blocks.add(MarkdownBlock.Heading(3, stripped.removePrefix("### ").trim()))
                stripped.startsWith("## ") -> blocks.add(MarkdownBlock.Heading(2, stripped.removePrefix("## ").trim()))
                stripped.startsWith("# ") -> blocks.add(MarkdownBlock.Heading(1, stripped.removePrefix("# ").trim()))
                stripped.startsWith("- ") || stripped.startsWith("* ") || stripped.startsWith("+ ") -> {
                    val indent = (line.length - line.trimStart().length) / 2
                    blocks.add(MarkdownBlock.BulletItem(indent, stripped.substring(2).trim()))
                }
                stripped.matches(Regex("""^\d+\.\s+.*""")) -> {
                    val num = stripped.substringBefore(".")
                    val content = stripped.substringAfter(".").trim()
                    blocks.add(MarkdownBlock.NumberedItem(num, content))
                }
                stripped.startsWith("> ") -> blocks.add(MarkdownBlock.Quote(stripped.removePrefix("> ").trim()))
                else -> blocks.add(MarkdownBlock.Paragraph(stripped))
            }
        }

        if (inCodeBlock && codeBuffer.isNotEmpty()) {
            blocks.add(MarkdownBlock.CodeBlock(codeLanguage, codeBuffer.toString()))
        }

        return blocks
    }

    fun parseInline(text: String): List<InlineSpan> {
        val result = mutableListOf<InlineSpan>()
        val regex = Regex("""(\*\*([^*]+)\*\*|\*([^*]+)\*|`([^`]+)`|\[([^\]]+)\]\(([^)]+)\))""")
        var currentIndex = 0

        for (match in regex.findAll(text)) {
            val matchRange = match.range
            if (matchRange.first > currentIndex) {
                result.add(InlineSpan.Plain(text.substring(currentIndex, matchRange.first)))
            }

            when {
                match.value.startsWith("**") && match.value.endsWith("**") -> {
                    result.add(InlineSpan.Bold(match.groupValues[2]))
                }
                match.value.startsWith("*") && match.value.endsWith("*") -> {
                    result.add(InlineSpan.Italic(match.groupValues[3]))
                }
                match.value.startsWith("`") && match.value.endsWith("`") -> {
                    result.add(InlineSpan.Code(match.groupValues[4]))
                }
                match.value.startsWith("[") && match.value.contains("](") -> {
                    result.add(InlineSpan.Link(text = match.groupValues[5], url = match.groupValues[6]))
                }
            }
            currentIndex = matchRange.last + 1
        }

        if (currentIndex < text.length) {
            result.add(InlineSpan.Plain(text.substring(currentIndex)))
        }

        return result
    }
}

private val Slate800 = Color(0xFF1E293B)
private val Slate700 = Color(0xFF334155)
private val Slate500 = Color(0xFF64748B)
private val BrandBlue = Color(0xFF2563EB)
private val CodeBackground = Color(0xFFF1F5F9)
private val CodeBorder = Color(0xFFCBD5E1)

@Composable
fun MarkdownContent(
    markdown: String,
    modifier: Modifier = Modifier,
    initialExpanded: Boolean = false
) {
    val blocks = remember(markdown) { MarkdownParser.parseBlocks(markdown) }
    var isExpanded by remember(markdown) { androidx.compose.runtime.mutableStateOf(initialExpanded) }
    val isLong = blocks.size > 5
    val visibleBlocks = if (isLong && !isExpanded) blocks.take(4) else blocks

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = Color(0xFFF8FAFC),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            for (block in visibleBlocks) {
                RenderBlock(block)
            }

            if (isLong) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isExpanded = !isExpanded }
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isExpanded) "收起日志 ↑" else "展开完整更新说明 (${blocks.size} 项) ↓",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = BrandBlue
                    )
                }
            }
        }
    }
}

@Composable
private fun RenderBlock(block: MarkdownBlock) {
    when (block) {
        is MarkdownBlock.Heading -> {
            val (fontSize, topPadding) = when (block.level) {
                1 -> 15.sp to 6.dp
                2 -> 13.5.sp to 4.dp
                else -> 12.5.sp to 2.dp
            }
            Spacer(modifier = Modifier.height(topPadding))
            Text(
                text = block.text,
                fontSize = fontSize,
                fontWeight = FontWeight.Bold,
                color = Slate800
            )
        }
        is MarkdownBlock.Paragraph -> {
            RenderInlineText(block.text)
        }
        is MarkdownBlock.BulletItem -> {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = (block.level * 12).dp),
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    modifier = Modifier
                        .padding(top = 7.dp, end = 8.dp)
                        .size(5.dp)
                        .background(BrandBlue, CircleShape)
                )
                Box(modifier = Modifier.weight(1f)) {
                    RenderInlineText(block.text)
                }
            }
        }
        is MarkdownBlock.NumberedItem -> {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = "${block.number}.",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = BrandBlue,
                    modifier = Modifier.padding(end = 6.dp)
                )
                Box(modifier = Modifier.weight(1f)) {
                    RenderInlineText(block.text)
                }
            }
        }
        is MarkdownBlock.Quote -> {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFF1F5F9), RoundedCornerShape(4.dp))
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(20.dp)
                        .background(Slate500, RoundedCornerShape(2.dp))
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = block.text,
                    fontSize = 11.5.sp,
                    fontStyle = FontStyle.Italic,
                    color = Slate700
                )
            }
        }
        is MarkdownBlock.CodeBlock -> {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(6.dp),
                color = Color(0xFF0F172A)
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    if (block.language.isNotBlank()) {
                        Text(
                            text = block.language.uppercase(),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF94A3B8),
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }
                    Text(
                        text = block.code.trimEnd(),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = Color(0xFFE2E8F0)
                    )
                }
            }
        }
    }
}

@Composable
private fun RenderInlineText(text: String) {
    val context = LocalContext.current
    val spans = remember(text) { MarkdownParser.parseInline(text) }

    val annotatedString = buildAnnotatedString {
        for (span in spans) {
            when (span) {
                is InlineSpan.Plain -> append(span.text)
                is InlineSpan.Bold -> {
                    pushStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Slate800))
                    append(span.text)
                    pop()
                }
                is InlineSpan.Italic -> {
                    pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                    append(span.text)
                    pop()
                }
                is InlineSpan.Code -> {
                    pushStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            background = CodeBackground,
                            color = Color(0xFF0F172A),
                            fontSize = 11.sp
                        )
                    )
                    append(" ${span.text} ")
                    pop()
                }
                is InlineSpan.Link -> {
                    pushStringAnnotation(tag = "URL", annotation = span.url)
                    pushStyle(
                        SpanStyle(
                            color = BrandBlue,
                            textDecoration = TextDecoration.Underline,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                    append(span.text)
                    pop()
                    pop()
                }
            }
        }
    }

    ClickableText(
        text = annotatedString,
        style = androidx.compose.ui.text.TextStyle(
            fontSize = 12.sp,
            color = Slate700,
            lineHeight = 18.sp
        ),
        onClick = { offset ->
            annotatedString.getStringAnnotations(tag = "URL", start = offset, end = offset)
                .firstOrNull()?.let { range ->
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(range.item)).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                    } catch (_: Exception) {}
                }
        }
    )
}
