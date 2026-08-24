package com.weave.core

object GraphError {
  case class NodeNotFound(nodeName: String) {
    def message: String = s"Node '$nodeName' not found in graph"
  }

  case class StartNodeNotDefined() {
    def message: String = "Start node not defined"
  }

  case class EmptyGraph() {
    def message: String = "Graph is empty"
  }

  case class EndNodeNotDefined() {
    def message: String = "End node not defined"
  }

  sealed trait ExecutionError {
    def cause: Throwable
    def message: String
  }

  case class RuntimeError(nodeName: String, cause: Throwable)
      extends ExecutionError {
    def message: String =
      s"Runtime error in node '$nodeName': ${cause.getMessage}"
  }

  case class JoinDeadlock(pendingJoins: Set[String]) extends ExecutionError {
    override val cause: Throwable = new RuntimeException(message)
    override def message: String =
      s"Workflow cannot progress; pending joins: ${pendingJoins.toList.sorted.mkString(", ")}"
  }

  type ValidationError =
    NodeNotFound | StartNodeNotDefined | EmptyGraph | EndNodeNotDefined

  type Error = ValidationError | ExecutionError
}
