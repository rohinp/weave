package com.weave.core

sealed trait RetryPolicy

object RetryPolicy {
  case object Never extends RetryPolicy
  case class FixedAttempts(
      maxAttempts: Int
  ) extends RetryPolicy
}
