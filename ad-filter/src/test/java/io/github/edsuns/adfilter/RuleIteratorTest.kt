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

package io.github.edsuns.adfilter

import io.github.edsuns.adfilter.util.RuleIterator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure JVM Unit Tests for RuleIterator rule management.
 * Verifies rule iteration, append deduplication, comment/uncomment,
 * and boundary/negative assertions.
 */
class RuleIteratorTest {

    @Test
    fun `scenario - empty RuleIterator has zero size and no next element`() {
        val iterator = RuleIterator()
        assertEquals(0, iterator.size())
        assertFalse(iterator.hasNext())
        assertEquals("", iterator.get(0))
    }

    @Test
    fun `scenario - append valid rules increases size and preserves order`() {
        val iterator = RuleIterator()
        iterator.append("||example.com^")
        iterator.append("||tracker.ad^")

        assertEquals(2, iterator.size())
        assertTrue(iterator.contains("||example.com^"))
        assertTrue(iterator.contains("||tracker.ad^"))
        assertFalse(iterator.contains("||notfound.com^"))

        assertEquals("||example.com^", iterator.get(0))
        assertEquals("||tracker.ad^", iterator.get(1))
    }

    @Test
    fun `scenario - append ignores blank strings and duplicates (negative assertions)`() {
        val iterator = RuleIterator()
        iterator.append("||adservice.com^")
        assertEquals(1, iterator.size())

        // NEGATIVE TEST: Blank string must not be added
        iterator.append("")
        iterator.append("   ")
        assertEquals(1, iterator.size())

        // NEGATIVE TEST: Duplicate rule must not be duplicated
        iterator.append("||adservice.com^")
        assertEquals(1, iterator.size())
    }

    @Test
    fun `scenario - comment and uncomment transforms rule correctly`() {
        val iterator = RuleIterator()
        val rule = "||badbanner.com^"
        iterator.append(rule)

        assertFalse(iterator.isComment(rule))
        iterator.comment(rule)

        val commented = "! $rule"
        assertTrue(iterator.isComment(commented))
        assertTrue(iterator.contains(commented))
        assertFalse(iterator.contains(rule))

        iterator.uncomment(commented)
        assertTrue(iterator.contains(rule))
        assertFalse(iterator.contains(commented))
    }

    @Test
    fun `scenario - remove deletes rule from dataset`() {
        val iterator = RuleIterator()
        iterator.append("rule1")
        iterator.append("rule2")
        iterator.append("rule3")
        assertEquals(3, iterator.size())

        iterator.remove("rule2")
        assertEquals(2, iterator.size())
        assertFalse(iterator.contains("rule2"))
        assertTrue(iterator.contains("rule1"))
        assertTrue(iterator.contains("rule3"))
    }
}
