/*
 * Copyright 2026 HugMun Project Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.hugmun.core.common

/**
 * A result that can fail in a way the user will be told about.
 *
 * Deliberately not `kotlin.Result`: failures here carry a [Problem] the UI can translate
 * into a specific Russian sentence, rather than a `Throwable` that would end up rendered
 * as "что-то пошло не так".
 */
public sealed interface Outcome<out T> {
    public data class Success<T>(public val value: T) : Outcome<T>
    public data class Failure(public val problem: Problem) : Outcome<Nothing>
}

/** Things that can go wrong, enumerated so each one gets its own honest message. */
public sealed interface Problem {
    /** Local storage failed. */
    public data class Storage(public val cause: Throwable?) : Problem

    /** The camera could not be opened or produced no usable signal. */
    public data class Camera(public val reason: CameraReason) : Problem

    /** Audio output could not be configured. */
    public data class Audio(public val cause: Throwable?) : Problem

    /** The device cannot do what a practice requires. */
    public data class Unsupported(public val requirement: String) : Problem

    /** A safety rule refused the action. Never surfaced as an error, only as a reason. */
    public data class BlockedBySafety(public val rule: String) : Problem

    public enum class CameraReason {
        PERMISSION_DENIED,
        UNAVAILABLE,
        NO_TORCH,
        SIGNAL_TOO_WEAK,
    }
}

public inline fun <T, R> Outcome<T>.map(transform: (T) -> R): Outcome<R> = when (this) {
    is Outcome.Success -> Outcome.Success(transform(value))
    is Outcome.Failure -> this
}

public fun <T> Outcome<T>.valueOrNull(): T? = (this as? Outcome.Success)?.value
