package com.weave.core

public data class Edge<S>(
    public val from: String,
    public val to: String,
    public val condition: (S) -> Boolean = { true },
)
