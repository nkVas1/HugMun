/*
 * Copyright 2026 HugMun Project Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.hugmun.core.common

import kotlinx.coroutines.CoroutineDispatcher

/**
 * Dispatchers as an injected dependency rather than a global.
 *
 * Nothing in this codebase references `Dispatchers.IO` directly. Tests substitute a
 * single deterministic dispatcher, which is what makes the scheduling and measurement
 * tests run in milliseconds instead of sleeping.
 */
public interface AppDispatchers {
    /** CPU-bound work: signal processing, threshold estimation, scoring. */
    public val default: CoroutineDispatcher

    /** Disk and database. */
    public val io: CoroutineDispatcher

    /** UI. */
    public val main: CoroutineDispatcher

    /**
     * The immediate main dispatcher.
     *
     * Used by the stimulus presenter, where dispatching to the next loop iteration would
     * cost a frame and a frame is the measurement unit.
     */
    public val mainImmediate: CoroutineDispatcher
}
