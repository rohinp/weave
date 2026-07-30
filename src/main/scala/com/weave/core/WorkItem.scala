package com.weave.core

case class WorkItem[S](
    nodeName: String,
    state: S
)
