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
        val markdown =
            """
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
        val markdown =
            """
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
        assertTrue(
            blocks[2] is MarkdownBlock.BulletItem && (blocks[2] as MarkdownBlock.BulletItem).text == "Cleaned up ViewRootImpl detach",
        )
        assertTrue(
            blocks[3] is MarkdownBlock.NumberedItem &&
                (blocks[3] as MarkdownBlock.NumberedItem).number == "1" &&
                (blocks[3] as MarkdownBlock.NumberedItem).text == "First step",
        )
        assertTrue(
            blocks[4] is MarkdownBlock.NumberedItem &&
                (blocks[4] as MarkdownBlock.NumberedItem).number == "2" &&
                (blocks[4] as MarkdownBlock.NumberedItem).text == "Second step",
        )
    }

    @Test
    fun `parses code blocks and blockquotes`() {
        val markdown =
            """
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
        val longChangelog =
            buildString {
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

    @Test
    fun `parses headings levels 1 through 6 accurately`() {
        val markdown =
            """
            # H1
            ## H2
            ### H3
            #### H4
            ##### H5
            ###### H6
            """.trimIndent()

        val blocks = MarkdownParser.parseBlocks(markdown)
        assertEquals(6, blocks.size)
        for (i in 1..6) {
            val block = blocks[i - 1]
            assertTrue("Block $i should be a Heading", block is MarkdownBlock.Heading)
            assertEquals(i, (block as MarkdownBlock.Heading).level)
            assertEquals("H$i", block.text)
        }
    }

    @Test
    fun `handles empty and whitespace markdown cleanly`() {
        assertTrue(MarkdownParser.parseBlocks("").isEmpty())
        assertTrue(MarkdownParser.parseBlocks("   \n\n  \t  ").isEmpty())
        assertTrue(MarkdownParser.parseInline("").isEmpty())
    }

    @Test
    fun `parses horizontal rules into Divider blocks`() {
        val markdown =
            """
            # Header
            ---
            Paragraph
            ****
            ___
            """.trimIndent()
        val blocks = MarkdownParser.parseBlocks(markdown)
        assertEquals(5, blocks.size)
        assertTrue(blocks[0] is MarkdownBlock.Heading)
        assertTrue("Block 1 should be Divider", blocks[1] is MarkdownBlock.Divider)
        assertTrue(blocks[2] is MarkdownBlock.Paragraph)
        assertTrue("Block 3 should be Divider", blocks[3] is MarkdownBlock.Divider)
        assertTrue("Block 4 should be Divider", blocks[4] is MarkdownBlock.Divider)
    }

    @Test
    fun `aggregates multiline blockquotes into unified block`() {
        val markdown =
            """
            > Line 1: notice
            > Line 2: with **bold** and `code`
            >
            > Line 3: after internal blank
            Other paragraph
            """.trimIndent()
        val blocks = MarkdownParser.parseBlocks(markdown)
        assertEquals(2, blocks.size)
        assertTrue(blocks[0] is MarkdownBlock.Quote)
        val quote = blocks[0] as MarkdownBlock.Quote
        assertTrue("Quote contains Line 1", quote.text.contains("Line 1: notice"))
        assertTrue("Quote contains Line 2", quote.text.contains("Line 2: with **bold** and `code`"))
        assertTrue("Quote contains Line 3", quote.text.contains("Line 3: after internal blank"))
        assertTrue(blocks[1] is MarkdownBlock.Paragraph)
    }

    @Test
    fun `parses nested backtick code and links inside bold`() {
        val text = "Support **global (`DiagnosticTimberTree`) feature** and __underscore bold__"
        val spans = MarkdownParser.parseInline(text)
        val boldSpan = spans.filterIsInstance<InlineSpan.Bold>().firstOrNull()
        assertTrue("Bold span exists", boldSpan != null)
        assertTrue("Bold span has children", boldSpan!!.children.isNotEmpty())
        assertTrue("Has inline code inside bold", boldSpan.children.any { it is InlineSpan.Code && it.text == "DiagnosticTimberTree" })
        assertTrue("Has underscore bold", spans.any { it is InlineSpan.Bold && it.text == "underscore bold" })
    }

    @Test
    fun `parses underscore italic and asterisk italic`() {
        val text = "Text with _italic one_ and *italic two*"
        val spans = MarkdownParser.parseInline(text)
        val italics = spans.filterIsInstance<InlineSpan.Italic>()
        assertEquals(2, italics.size)
        assertEquals("italic one", italics[0].text)
        assertEquals("italic two", italics[1].text)
    }
}
