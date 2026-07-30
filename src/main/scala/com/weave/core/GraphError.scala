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

  case class RuntimeError(nodeName: String, cause: Throwable) {
    def message: String =
      s"Runtime error in node '$nodeName': ${cause.getMessage}"
  }

  type ValidationError =
    NodeNotFound | StartNodeNotDefined | EmptyGraph | EndNodeNotDefined

  type ExecutionError = RuntimeError

  type Error = ValidationError | ExecutionError
}
