package com.tagforge.domain.security

import com.tagforge.domain.common.Bytes
import com.tagforge.domain.common.Outcome
import com.tagforge.domain.common.TagResult
import com.tagforge.domain.fakes.FakeNtag21x
import com.tagforge.domain.fakes.FakeTagSession
import com.tagforge.domain.fakes.hex
import com.tagforge.domain.ntag.NtagModel
import com.tagforge.domain.scan.TagOperation
import com.tagforge.domain.tag.TagError

/** UID of the examples in the spec (NS-01): its last 4 hex characters are E5F6. */
val SPEC_UID: Bytes = "04 A1 B2 C3 D4 E5 F6".hex()

/** A blank NTAG with the factory CFG0 `04 00 00 FF` of the spec fixtures. */
fun specTag(model: NtagModel = NtagModel.NTAG215, uid: Bytes = SPEC_UID): FakeNtag21x =
    FakeNtag21x(model, uid).also { it.setPage(model.cfg0, "04 00 00 FF".hex()) }

fun sessionOf(tag: FakeNtag21x): FakeTagSession = FakeTagSession.ntag(tag)

suspend fun <T> TagOperation<T>.runOn(session: FakeTagSession): TagResult<T> = execute(session) { }

/** Reads the security state of [tag] or fails the test. */
suspend fun stateOf(tag: FakeNtag21x, password: Password? = null): SecurityState {
    val result = ReadSecurityStateUseCase(password).runOn(sessionOf(tag))
    check(result is Outcome.Ok) { "could not read the security state: $result" }
    return result.value
}

/** Asserts the result is a security failure and returns it. */
fun <T> TagResult<T>.securityFailure(): SecurityFailure {
    check(this is Outcome.Err) { "expected a failure but got $this" }
    val error = this.error
    check(error is TagError.Security) { "expected a security failure but got $error" }
    return error.failure
}

/** Plans and confirms with the correct typed text, or fails the test. */
fun SecurityPlan.confirmed(): ConfirmedPlan = confirm(UidConfirmation.expected(uid)) ?: error("confirmation refused")

fun <T, E> Outcome<T, E>.value(): T {
    check(this is Outcome.Ok) { "expected success but got $this" }
    return value
}

fun <T> Outcome<T, SecurityFailure>.failure(): SecurityFailure {
    check(this is Outcome.Err) { "expected a refusal but got $this" }
    return error
}
