package com.weave.core

public data class Node<S, U>(
    public val name: String,
    public val retryPolicy: RetryPolicy = RetryPolicy.Never,
    public val action: (S) -> U,
)
