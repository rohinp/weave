package com.weave.core

case class JoinInput[S](
                         fromNode: String,
                         state: S
                       )