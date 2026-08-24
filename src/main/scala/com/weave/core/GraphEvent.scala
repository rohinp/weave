package com.weave.core

sealed trait GraphEvent {
  val name: String
}

object GraphEvent {

  case class NodeStarted(
      name: String
  ) extends GraphEvent

  case class NodeCompleted(
      name: String
  ) extends GraphEvent

  case class WorkflowCompleted(name: String) extends GraphEvent

  case class NodeFailed(
      name: String,
      cause: Throwable
  ) extends GraphEvent

  case class WorkflowFailed(
      name: String,
      cause: Throwable
  ) extends GraphEvent

  case class CheckpointCreated[S](
      name: String,
      state: S
  ) extends GraphEvent
}
