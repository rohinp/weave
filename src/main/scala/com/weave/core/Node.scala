package com.weave.core

final case class Node[S, U](
    name: String,
    f: S => U,
    retryPolicy: RetryPolicy = RetryPolicy.Never
)
