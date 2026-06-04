package com.weave.core

final case class Node[A](
                          name: String,
                          f: A => A,
                          retryPolicy:RetryPolicy = RetryPolicy.Never
                        )
