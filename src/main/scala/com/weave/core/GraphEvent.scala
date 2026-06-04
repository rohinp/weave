package com.weave.core

sealed trait GraphEvent

object GraphEvent {

  case class NodeStarted(
      nodeName: String
  ) extends GraphEvent

  case class NodeCompleted(
      nodeName: String
  ) extends GraphEvent

  case class WorkflowCompleted() extends GraphEvent

  case class NodeFailed(
      nodeName: String,
      cause: Throwable
  ) extends GraphEvent

  case class WorkflowFailed(
      cause: Throwable
  ) extends GraphEvent
}
