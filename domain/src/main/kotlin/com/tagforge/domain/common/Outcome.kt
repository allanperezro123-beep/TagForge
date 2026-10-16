package com.tagforge.domain.common

import com.tagforge.domain.tag.TagError

/** Typed success/failure container. The domain never throws for expected failures. */
sealed interface Outcome<out T, out E> {
    data class Ok<out T>(val value: T) : Outcome<T, Nothing>
    data class Err<out E>(val error: E) : Outcome<Nothing, E>
}

typealias TagResult<T> = Outcome<T, TagError>

inline fun <T, E, R> Outcome<T, E>.map(transform: (T) -> R): Outcome<R, E> = when (this) {
    is Outcome.Ok -> Outcome.Ok(transform(value))
    is Outcome.Err -> this
}

inline fun <T, E, R> Outcome<T, E>.flatMap(transform: (T) -> Outcome<R, E>): Outcome<R, E> = when (this) {
    is Outcome.Ok -> transform(value)
    is Outcome.Err -> this
}

fun <T, E> Outcome<T, E>.getOrNull(): T? = (this as? Outcome.Ok)?.value

fun <T, E> Outcome<T, E>.errorOrNull(): E? = (this as? Outcome.Err)?.error
