package com.weave.core

public data class Node<S, U>(
    public val name: String,
    public val action: (S) -> U,
)
