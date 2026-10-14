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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit Tests for MarkdownParser ensuring GitHub release notes are parsed accurately without truncation.
 */
class MarkdownParserTest {

    @Test
    fun `parses headings accurately`() {
        val markdown = """
            # Version 1.2.0
            ## What's Changed
            ### Bug Fixes
        """.trimIndent()

        val blocks = MarkdownParser.parseBlocks(markdown)
        assertEquals(3, blocks.size)
        assertTrue(blocks[0] is MarkdownBlock.Heading && (blocks[0] as MarkdownBlock.Heading).level == 1)
        assertEquals("Version 1.2.0", (blocks[0] as MarkdownBlock.Heading).text)

        assertTrue(blocks[1] is MarkdownBlock.Heading && (blocks[1] as MarkdownBlock.Heading).level == 2)
        assertEquals("What's Changed", (blocks[1] as MarkdownBlock.Heading).text)

        assertTrue(blocks[2] is MarkdownBlock.Heading && (blocks[2] as MarkdownBlock.Heading).level == 3)
        assertEquals("Bug Fixes", (blocks[2] as MarkdownBlock.Heading).text)
    }

    @Test
    fun `parses unordered and ordered lists`() {
        val markdown = """
            * Fixed floating window flicker
            - Added click-to-focus support
            + Cleaned up ViewRootImpl detach
            1. First step
            2. Second step
        """.trimIndent()

        val blocks = MarkdownParser.parseBlocks(markdown)
        assertEquals(5, blocks.size)
        assertTrue(blocks[0] is MarkdownBlock.BulletItem && (blocks[0] as MarkdownBlock.BulletItem).text == "Fixed floating window flicker")
        assertTrue(blocks[1] is MarkdownBlock.BulletItem && (blocks[1] as MarkdownBlock.BulletItem).text == "Added click-to-focus support")
        assertTrue(blocks[2] is MarkdownBlock.BulletItem && (blocks[2] as MarkdownBlock.BulletItem).text == "Cleaned up ViewRootImpl detach")
        assertTrue(blocks[3] is MarkdownBlock.NumberedItem && (blocks[3] as MarkdownBlock.NumberedItem).number == "1" && (blocks[3] as MarkdownBlock.NumberedItem).text == "First step")
        assertTrue(blocks[4] is MarkdownBlock.NumberedItem && (blocks[4] as MarkdownBlock.NumberedItem).number == "2" && (blocks[4] as MarkdownBlock.NumberedItem).text == "Second step")
    }

    @Test
    fun `parses code blocks and blockquotes`() {
        val markdown = """
            > Important upgrade notice
            ```kotlin
            val app = "FloatBro"
            ```
        """.trimIndent()

        val blocks = MarkdownParser.parseBlocks(markdown)
        assertEquals(2, blocks.size)
        assertTrue(blocks[0] is MarkdownBlock.Quote && (blocks[0] as MarkdownBlock.Quote).text == "Important upgrade notice")
        assertTrue(blocks[1] is MarkdownBlock.CodeBlock && (blocks[1] as MarkdownBlock.CodeBlock).language == "kotlin")
        assertEquals("val app = \"FloatBro\"", (blocks[1] as MarkdownBlock.CodeBlock).code.trim())
    }

    @Test
    fun `parses inline formatting including bold, inline code, and links`() {
        val text = "Check out **FloatBro** with `click-to-focus` at [GitHub](https://github.com/floatbro)"
        val spans = MarkdownParser.parseInline(text)

        assertTrue(spans.any { it is InlineSpan.Bold && it.text == "FloatBro" })
        assertTrue(spans.any { it is InlineSpan.Code && it.text == "click-to-focus" })
        assertTrue(spans.any { it is InlineSpan.Link && it.text == "GitHub" && it.url == "https://github.com/floatbro" })
    }

    @Test
    fun `preserves long release text beyond 160 characters without truncation`() {
        val longChangelog = buildString {
            append("## Full Release Notes\n")
            repeat(20) { idx ->
                append("* Feature item #$idx: Detailed description of the feature and fixes.\n")
            }
        }
        assertTrue("Input is longer than 500 chars", longChangelog.length > 500)

        val blocks = MarkdownParser.parseBlocks(longChangelog)
        assertEquals(21, blocks.size)
        val allBulletText = blocks.filterIsInstance<MarkdownBlock.BulletItem>().joinToString(" ") { it.text }
        assertTrue("Contains all 20 items", allBulletText.contains("Feature item #19"))
    }
}
