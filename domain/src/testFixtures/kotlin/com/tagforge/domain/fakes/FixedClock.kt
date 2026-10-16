package com.tagforge.domain.fakes

import kotlin.time.Clock
import kotlin.time.Instant

/** A [Clock] that only moves when a test says so. */
class FixedClock(private var instant: Instant = Instant.fromEpochMilliseconds(0)) : Clock {
    override fun now(): Instant = instant

    fun advanceMillis(millis: Long) {
        instant = Instant.fromEpochMilliseconds(instant.toEpochMilliseconds() + millis)
    }
}
