package com.weave.core

/** Controls the total number of times a failed node execution may be attempted. */
public sealed interface RetryPolicy {
    /** Execute the node exactly once. */
    public data object Never : RetryPolicy

    /**
     * Execute the node at most [maxAttempts] times, including the initial attempt.
     * Execution stops immediately after the first successful attempt.
     */
    public data class FixedAttempts(public val maxAttempts: Int) : RetryPolicy {
        init {
            require(maxAttempts > 0) { "maxAttempts must be greater than zero" }
        }
    }
}
