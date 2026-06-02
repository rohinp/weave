package com.weave.core

import scala.annotation.tailrec

case class Graph[S] private(
                             nodes: Map[String, Node[S]],
                             edges: Map[String, String],
                             start: Option[String],
                             end: Option[String]
                           ) {
  override def toString: String = {
    val rendered =
      nodes.keys.toList.sorted.map { node =>
        edges.get(node) match {
          case Some(next) => s"  $node ──▶ $next"
          case None => s"  $node"
        }
      }

    s"""Graph
       |Start: ${start.getOrElse("?")}
       |End:   ${end.getOrElse("?")}
       |
       |${rendered.mkString("\n")}
       |""".stripMargin
  }
}

object Graph {
  def apply[S](): Graph[S] = new Graph(Map.empty, Map.empty, None, None)

  extension [S](graph: Graph[S])
    def addNode(node: Node[S]): Graph[S] =
      graph.copy(nodes = graph.nodes + (node.name -> node))

  extension [S](graph: Graph[S])
    def addEdge(from: String, to: String): Graph[S] =
      graph.copy(edges = graph.edges + (from -> to))

  extension [S](graph: Graph[S])
    def setStart(nodeName: String): Graph[S] =
      graph.copy(start = Some(nodeName))

  extension [S](graph: Graph[S])
    def setEnd(nodeName: String): Graph[S] =
      graph.copy(end = Some(nodeName))

  private[core] class GraphRunner[S](graph: Graph[S]) {
    def run(initialState: S): S = {
      @tailrec
      def execute(nodeName: String, state: S): S = {
        val optNode = graph.nodes.get(nodeName)
        optNode match {
          case None =>
            throw new IllegalArgumentException(
              s"Node '$nodeName' not found in graph"
            )
          case Some(node) if graph.end.contains(node) =>
            node.f(state)
          case Some(node) =>
            val newState = node.f(state)
            graph.edges.get(nodeName) match {
              case Some(nextNode) => execute(nextNode, newState)
              case None           => newState
            }
        }
      }

      graph.start match {
        case Some(startNode) => execute(startNode, initialState)
        case None => throw new IllegalStateException("Start node not defined")
      }
    }
  }

  extension [S](graph: Graph[S])
    /*TODO: Check mixin types*/
    def validate(): Either[GraphError, GraphRunner[S]] = {
      import graph.*
      // TODO: Refactor this validation logic to be more concise and readable
      if (nodes.isEmpty) {
        Left(GraphError.EmptyGraph())
      } else if (start.isEmpty) {
        Left(GraphError.StartNodeNotDefined())
      } else if (!nodes.contains(start.get)) {
        Left(GraphError.NodeNotFound(start.get))
      } else if (end.isEmpty) {
        Left(GraphError.EndNodeNotDefined())
      } else if (!nodes.contains(end.get)) {
        Left(GraphError.NodeNotFound(end.get))
      } else {
        edges.find { case (from, to) =>
          !nodes.contains(from) || !nodes.contains(to)
        } match {
          case Some((from, to)) =>
            if (!nodes.contains(from)) {
              Left(GraphError.NodeNotFound(from))
            } else {
              Left(GraphError.NodeNotFound(to))
            }
          case None => Right(new GraphRunner[S](graph)) // graph
        }
      }
    }
}
