package com.tagforge.domain.common

import com.tagforge.domain.tag.TagError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class OutcomeTest {

    @Test
    fun mapTransformsOnlyOk() {
        val ok: Outcome<Int, String> = Outcome.Ok(2)
        val err: Outcome<Int, String> = Outcome.Err("boom")
        assertEquals(Outcome.Ok(4), ok.map { it * 2 })
        assertEquals(Outcome.Err("boom"), err.map { it * 2 })
    }

    @Test
    fun flatMapChainsAndShortCircuits() {
        val ok: Outcome<Int, String> = Outcome.Ok(2)
        val err: Outcome<Int, String> = Outcome.Err("boom")
        assertEquals(Outcome.Ok(3), ok.flatMap { Outcome.Ok(it + 1) })
        assertEquals(Outcome.Err("late"), ok.flatMap { Outcome.Err("late") })
        assertEquals(Outcome.Err("boom"), err.flatMap { Outcome.Ok(it + 1) })
    }

    @Test
    fun getOrNullAndErrorOrNull() {
        val ok: Outcome<Int, String> = Outcome.Ok(2)
        val err: Outcome<Int, String> = Outcome.Err("boom")
        assertEquals(2, ok.getOrNull())
        assertNull(err.getOrNull())
        assertEquals("boom", err.errorOrNull())
        assertNull(ok.errorOrNull())
    }

    @Test
    fun tagResultCarriesTypedTagErrors() {
        val result: TagResult<Int> = Outcome.Err(TagError.TagLost)
        assertEquals(TagError.TagLost, result.errorOrNull())
    }
}
