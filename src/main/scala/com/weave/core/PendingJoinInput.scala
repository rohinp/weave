package com.weave.core

case class PendingJoinInput[S](
    joinNode: String,
    parentNode: String,
    state: S
)
