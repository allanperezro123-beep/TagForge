package com.tagforge.domain.fakes

import com.tagforge.domain.common.Bytes
import com.tagforge.domain.common.Hex

/** Test helper: `"D1 01 05".hex()` -> Bytes. Fails loudly on malformed hex so typos never hide. */
fun String.hex(): Bytes = Hex.parse(this) ?: error("Malformed hex literal in test: '$this'")
