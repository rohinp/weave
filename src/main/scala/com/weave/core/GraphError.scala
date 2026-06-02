package com.weave.core

trait GraphError {
  def message: String
}

object GraphError {
  case class NodeNotFound(nodeName: String) extends GraphError {
    override def message: String = s"Node '$nodeName' not found in graph"
  }

  case class StartNodeNotDefined() extends GraphError {
    override def message: String = "Start node not defined"
  }

  case class EmptyGraph() extends GraphError {
    override def message: String = "Graph is empty"
  }

  case class EndNodeNotDefined() extends GraphError {
    override def message: String = "End node not defined"
  }
}
