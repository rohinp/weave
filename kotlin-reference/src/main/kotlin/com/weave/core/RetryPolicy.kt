package com.weave.core

public sealed interface RetryPolicy {
    public data object Never : RetryPolicy

    public data class FixedAttempts(public val maxAttempts: Int) : RetryPolicy {
        init {
            require(maxAttempts > 0) { "maxAttempts must be greater than zero" }
        }
    }
}
