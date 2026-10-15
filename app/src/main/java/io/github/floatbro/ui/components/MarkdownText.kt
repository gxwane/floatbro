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

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

sealed interface MarkdownBlock {
    data class Heading(
        val level: Int,
        val text: String,
    ) : MarkdownBlock

    data class Paragraph(
        val text: String,
    ) : MarkdownBlock

    data class BulletItem(
        val level: Int,
        val text: String,
    ) : MarkdownBlock

    data class NumberedItem(
        val number: String,
        val text: String,
    ) : MarkdownBlock

    data class Quote(
        val text: String,
    ) : MarkdownBlock

    data class CodeBlock(
        val language: String,
        val code: String,
    ) : MarkdownBlock

    data object Divider : MarkdownBlock
}

sealed interface InlineSpan {
    val text: String

    data class Plain(
        override val text: String,
    ) : InlineSpan

    data class Bold(
        override val text: String,
        val children: List<InlineSpan> = emptyList(),
    ) : InlineSpan

    data class Italic(
        override val text: String,
    ) : InlineSpan

    data class Code(
        override val text: String,
    ) : InlineSpan

    data class Link(
        override val text: String,
        val url: String,
    ) : InlineSpan
}

object MarkdownParser {
    private val HEADING_REGEX = Regex("""^(#{1,6})\s+(.*)""")
    private val NUMBERED_ITEM_REGEX = Regex("""^(\d+)\.\s+(.*)""")
    private val DIVIDER_REGEX =
        Regex("""^(?:-{3,}|\*{3,}|_{3,}|\s*(?:-\s*){3,}|\s*(?:\*\s*){3,}|\s*(?:_\s*){3,})$""")
    private val INLINE_REGEX =
        Regex("""(\*\*([^*]+)\*\*|__([^_]+)__|\*([^*]+)\*|_([^_]+)_|`([^`]+)`|\[([^\]]+)\]\(([^)]+)\))""")

    fun parseBlocks(markdown: String): List<MarkdownBlock> {
        if (markdown.isBlank()) return emptyList()
        val lines = markdown.lines()
        val blocks = mutableListOf<MarkdownBlock>()
        var inCodeBlock = false
        var codeLanguage = ""
        val codeBuffer = StringBuilder()

        var inQuote = false
        val quoteBuffer = StringBuilder()

        fun flushQuote() {
            if (inQuote && quoteBuffer.isNotEmpty()) {
                blocks.add(MarkdownBlock.Quote(quoteBuffer.toString().trimEnd()))
                quoteBuffer.clear()
                inQuote = false
            }
        }

        for (line in lines) {
            val trimmed = line.trimEnd()
            if (trimmed.startsWith("```")) {
                flushQuote()
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
            if (stripped.isEmpty()) {
                flushQuote()
                continue
            }

            if (stripped.startsWith(">")) {
                inQuote = true
                val content = stripped.removePrefix(">").trim()
                if (quoteBuffer.isNotEmpty()) {
                    quoteBuffer.append("\n")
                }
                quoteBuffer.append(content)
                continue
            }

            flushQuote()

            if (DIVIDER_REGEX.matches(stripped)) {
                blocks.add(MarkdownBlock.Divider)
                continue
            }

            val headingMatch = HEADING_REGEX.matchEntire(stripped)
            val numberedMatch = NUMBERED_ITEM_REGEX.matchEntire(stripped)

            when {
                headingMatch != null -> {
                    val level = headingMatch.groupValues[1].length
                    val text = headingMatch.groupValues[2].trim()
                    blocks.add(MarkdownBlock.Heading(level, text))
                }
                stripped.startsWith("- ") || stripped.startsWith("* ") || stripped.startsWith("+ ") -> {
                    val indent = (line.length - line.trimStart().length) / 2
                    blocks.add(MarkdownBlock.BulletItem(indent, stripped.substring(2).trim()))
                }
                numberedMatch != null -> {
                    val num = numberedMatch.groupValues[1]
                    val content = numberedMatch.groupValues[2].trim()
                    blocks.add(MarkdownBlock.NumberedItem(num, content))
                }
                else -> blocks.add(MarkdownBlock.Paragraph(stripped))
            }
        }

        flushQuote()

        if (inCodeBlock && codeBuffer.isNotEmpty()) {
            blocks.add(MarkdownBlock.CodeBlock(codeLanguage, codeBuffer.toString()))
        }

        return blocks
    }

    fun parseInline(text: String): List<InlineSpan> {
        if (text.isEmpty()) return emptyList()
        val result = mutableListOf<InlineSpan>()
        var currentIndex = 0

        for (match in INLINE_REGEX.findAll(text)) {
            val matchRange = match.range
            if (matchRange.first > currentIndex) {
                result.add(InlineSpan.Plain(text.substring(currentIndex, matchRange.first)))
            }
            mapMatchToSpan(match)?.let { result.add(it) }
            currentIndex = matchRange.last + 1
        }

        if (currentIndex < text.length) {
            result.add(InlineSpan.Plain(text.substring(currentIndex)))
        }

        return result
    }

    private fun mapMatchToSpan(match: MatchResult): InlineSpan? {
        val v = match.value
        return when {
            v.startsWith("**") && v.endsWith("**") ->
                InlineSpan.Bold(match.groupValues[2], parseInnerInline(match.groupValues[2]))
            v.startsWith("__") && v.endsWith("__") ->
                InlineSpan.Bold(match.groupValues[3], parseInnerInline(match.groupValues[3]))
            v.startsWith("*") && v.endsWith("*") ->
                InlineSpan.Italic(match.groupValues[4])
            v.startsWith("_") && v.endsWith("_") ->
                InlineSpan.Italic(match.groupValues[5])
            v.startsWith("`") && v.endsWith("`") ->
                InlineSpan.Code(match.groupValues[6])
            v.startsWith("[") && v.contains("](") ->
                InlineSpan.Link(text = match.groupValues[7], url = match.groupValues[8])
            else -> null
        }
    }

    private fun parseInnerInline(inner: String): List<InlineSpan> =
        if (inner.contains("`") || inner.contains("[")) {
            parseInline(inner)
        } else {
            emptyList()
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
    initialExpanded: Boolean = true,
) {
    val isDark = isSystemInDarkTheme()
    val blocks = remember(markdown) { MarkdownParser.parseBlocks(markdown) }
    var isExpanded by remember(markdown) { androidx.compose.runtime.mutableStateOf(initialExpanded) }
    val isVeryLong = blocks.size > 20
    val visibleBlocks = if (isVeryLong && !isExpanded) blocks.take(15) else blocks

    val containerColor = if (isDark) Color(0xFF1E293B) else Color(0xFFF8FAFC)
    val borderColor = if (isDark) Color(0xFF334155) else Color(0xFFE2E8F0)

    Surface(
        modifier =
            modifier
                .fillMaxWidth()
                .animateContentSize(),
        shape = RoundedCornerShape(10.dp),
        color = containerColor,
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for (block in visibleBlocks) {
                RenderBlock(block)
            }

            if (isVeryLong) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable { isExpanded = !isExpanded }
                            .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (isExpanded) "收起日志 ↑" else "展开完整更新说明 (${blocks.size} 项) ↓",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = BrandBlue,
                    )
                }
            }
        }
    }
}

@Composable
private fun RenderBlock(block: MarkdownBlock) {
    val isDark = isSystemInDarkTheme()
    when (block) {
        is MarkdownBlock.Divider -> {
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 4.dp),
                thickness = 1.dp,
                color = if (isDark) Color(0xFF334155) else Color(0xFFE2E8F0),
            )
        }
        is MarkdownBlock.Heading -> {
            val (fontSize, topPadding) =
                when (block.level) {
                    1 -> 15.sp to 6.dp
                    2 -> 14.sp to 5.dp
                    3 -> 13.sp to 4.dp
                    4 -> 12.5.sp to 3.dp
                    5 -> 12.sp to 2.dp
                    else -> 11.5.sp to 2.dp
                }
            Spacer(modifier = Modifier.height(topPadding))
            RenderInlineText(
                text = block.text,
                baseStyle =
                    androidx.compose.ui.text.TextStyle(
                        fontSize = fontSize,
                        fontWeight = FontWeight.Bold,
                        color = if (isDark) Color(0xFFF8FAFC) else Slate800,
                    ),
            )
        }
        is MarkdownBlock.Paragraph -> {
            RenderInlineText(block.text)
        }
        is MarkdownBlock.BulletItem -> {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(start = (block.level * 12).dp),
                verticalAlignment = Alignment.Top,
            ) {
                Box(
                    modifier =
                        Modifier
                            .padding(top = 7.dp, end = 8.dp)
                            .size(5.dp)
                            .background(BrandBlue, CircleShape),
                )
                Box(modifier = Modifier.weight(1f)) {
                    RenderInlineText(block.text)
                }
            }
        }
        is MarkdownBlock.NumberedItem -> {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    text = "${block.number}.",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = BrandBlue,
                    modifier = Modifier.padding(end = 6.dp),
                )
                Box(modifier = Modifier.weight(1f)) {
                    RenderInlineText(block.text)
                }
            }
        }
        is MarkdownBlock.Quote -> {
            val quoteBg = if (isDark) Color(0xFF0F172A) else Color(0xFFF1F5F9)
            val quoteTextColor = if (isDark) Color(0xFFCBD5E1) else Slate700
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(quoteBg, RoundedCornerShape(4.dp))
                        .padding(8.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Box(
                    modifier =
                        Modifier
                            .width(3.dp)
                            .height(20.dp)
                            .background(Slate500, RoundedCornerShape(2.dp)),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Box(modifier = Modifier.weight(1f)) {
                    RenderInlineText(
                        text = block.text,
                        baseStyle =
                            androidx.compose.ui.text.TextStyle(
                                fontSize = 11.5.sp,
                                fontStyle = FontStyle.Italic,
                                color = quoteTextColor,
                                lineHeight = 17.sp,
                            ),
                    )
                }
            }
        }

        is MarkdownBlock.CodeBlock -> {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(6.dp),
                color = Color(0xFF0F172A),
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    if (block.language.isNotBlank()) {
                        Text(
                            text = block.language.uppercase(),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF94A3B8),
                            modifier = Modifier.padding(bottom = 4.dp),
                        )
                    }
                    Text(
                        text = block.code.trimEnd(),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = Color(0xFFE2E8F0),
                    )
                }
            }
        }
    }
}

@Composable
private fun RenderInlineText(
    text: String,
    baseStyle: androidx.compose.ui.text.TextStyle? = null,
) {
    val isDark = isSystemInDarkTheme()
    val uriHandler = LocalUriHandler.current
    val spans = remember(text) { MarkdownParser.parseInline(text) }

    val colors =
        remember(isDark) {
            MarkdownColors(
                normal = if (isDark) Color(0xFFE2E8F0) else Slate700,
                bold = if (isDark) Color(0xFFF8FAFC) else Slate800,
                codeBg = if (isDark) Color(0xFF0F172A) else CodeBackground,
                codeText = if (isDark) Color(0xFF93C5FD) else Color(0xFF0F172A),
            )
        }

    val defaultStyle =
        baseStyle ?: androidx.compose.ui.text.TextStyle(
            fontSize = 12.sp,
            color = colors.normal,
            lineHeight = 18.sp,
        )

    val annotatedString =
        remember(spans, colors) {
            buildAnnotatedString {
                for (span in spans) {
                    appendSpan(span, colors, uriHandler)
                }
            }
        }

    Text(
        text = annotatedString,
        style = defaultStyle,
    )
}

private data class MarkdownColors(
    val normal: Color,
    val bold: Color,
    val codeBg: Color,
    val codeText: Color,
)

private fun androidx.compose.ui.text.AnnotatedString.Builder.appendSpan(
    span: InlineSpan,
    colors: MarkdownColors,
    uriHandler: androidx.compose.ui.platform.UriHandler,
) {
    when (span) {
        is InlineSpan.Plain -> append(span.text)
        is InlineSpan.Bold -> appendBoldSpan(span, colors, uriHandler)
        is InlineSpan.Italic -> {
            pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
            append(span.text)
            pop()
        }
        is InlineSpan.Code -> appendCodeSpan(span.text, colors)
        is InlineSpan.Link -> appendLinkSpan(span.text, span.url, uriHandler)
    }
}

private fun androidx.compose.ui.text.AnnotatedString.Builder.appendCodeSpan(
    text: String,
    colors: MarkdownColors,
) {
    pushStyle(
        SpanStyle(
            fontFamily = FontFamily.Monospace,
            background = colors.codeBg,
            color = colors.codeText,
            fontSize = 11.sp,
        ),
    )
    append(" $text ")
    pop()
}

private fun androidx.compose.ui.text.AnnotatedString.Builder.appendLinkSpan(
    text: String,
    url: String,
    uriHandler: androidx.compose.ui.platform.UriHandler,
) {
    val linkAnnotation =
        LinkAnnotation.Url(
            url = url,
            styles =
                TextLinkStyles(
                    style =
                        SpanStyle(
                            color = BrandBlue,
                            textDecoration = TextDecoration.Underline,
                            fontWeight = FontWeight.SemiBold,
                        ),
                ),
            linkInteractionListener = { uriHandler.openUri(url) },
        )
    pushLink(linkAnnotation)
    append(text)
    pop()
}

private fun androidx.compose.ui.text.AnnotatedString.Builder.appendBoldSpan(
    span: InlineSpan.Bold,
    colors: MarkdownColors,
    uriHandler: androidx.compose.ui.platform.UriHandler,
) {
    pushStyle(SpanStyle(fontWeight = FontWeight.Bold, color = colors.bold))
    if (span.children.isNotEmpty()) {
        for (child in span.children) {
            when (child) {
                is InlineSpan.Code -> appendCodeSpan(child.text, colors)
                is InlineSpan.Link -> appendLinkSpan(child.text, child.url, uriHandler)
                else -> append(child.text)
            }
        }
    } else {
        append(span.text)
    }
    pop()
}
