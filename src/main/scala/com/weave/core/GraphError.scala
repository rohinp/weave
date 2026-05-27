package com.weave.core

trait GraphError extends RuntimeException

object GraphError {
  case class NodeNotFound(nodeName: String) extends GraphError {
    override def getMessage: String = s"Node '$nodeName' not found in graph"
  }

  case class StartNodeNotDefined() extends GraphError {
    override def getMessage: String = "Start node not defined"
  }

  case class EmptyGraph() extends GraphError {
    override def getMessage: String = "Graph is empty"
  }
  
  case class EndNodeNotDefined() extends GraphError {
    override def getMessage: String = "End node not defined"
  }
}
