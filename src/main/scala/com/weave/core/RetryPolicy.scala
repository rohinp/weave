package com.weave.core

sealed trait RetryPolicy

object RetryPolicy {
  case object Never
  case class FixedAttempts(
      maxAttempts: Int
  )
}
