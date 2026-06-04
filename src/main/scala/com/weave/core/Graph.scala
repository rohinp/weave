package com.weave.core

import scala.annotation.tailrec
import scala.util.Try
import scala.util.chaining.*

case class Graph[S] private (
    nodes: Map[String, Node[S]],
    edges: List[Edge[S]],
    start: Option[String],
    end: Option[String]
) {
  override def toString: String = {
    val rendered =
      nodes.keys.toList.sorted.map { node =>
        edges.find(_.from == node) match {
          case Some(next) => s"  $node ──${next.condition}──▶ ${next.to}"
          case None       => s"  $node"
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
  def apply[S](): Graph[S] = new Graph(Map.empty, List.empty, None, None)

  extension[S](graph: Graph[S])
    def addNode(node: Node[S]): Graph[S] =
      graph.copy(nodes = graph.nodes + (node.name -> node))

  extension[S](graph: Graph[S])
    def addEdge(edge: Edge[S]): Graph[S] =
      graph.copy(edges = edge :: graph.edges)

  extension[S](graph: Graph[S])
    def setStart(nodeName: String): Graph[S] =
      graph.copy(start = Some(nodeName))

  extension[S](graph: Graph[S])
    def setEnd(nodeName: String): Graph[S] =
      graph.copy(end = Some(nodeName))

  private[core] class GraphRunner[S](graph: Graph[S]) {

    private def executeNode(
        node: Node[S],
        state: S,
        onEvent: GraphEvent => Unit
    ): Either[GraphError, S] = {
      onEvent(GraphEvent.NodeStarted(node.name))
      Try(node.f(state)).fold(
        ex => {
          onEvent(GraphEvent.NodeFailed(node.name, ex))
          onEvent(GraphEvent.WorkflowFailed(ex))
          Left(GraphError.RuntimeError(node.name, ex))
        },
        Right(_).tap(_ => onEvent(GraphEvent.NodeCompleted(node.name)))
      )
    }

    def run(
        initialState: S,
        onEvent: GraphEvent => Unit = _ => ()
    ): Either[GraphError, S] = {

      def execute(
          remainingNode: List[String],
          state: S
      ): Either[GraphError, S] = {
        remainingNode match {
          //TODO: execution completion needs a single owner.
          case List()         => Right(state)
          case nodeName :: xs =>
            val node = graph.nodes(nodeName)
            if (graph.end.contains(nodeName)) {
              executeNode(node, state, onEvent)
            } else {
              for {
                newState <- executeNode(node, state, onEvent)
                nextNodes = graph.edges
                  .filter(edge =>
                    edge.from == nodeName && edge.condition(newState)
                  )
                  .map(_.to)
                result <- execute(xs ++ nextNodes, newState)
                _ = onEvent(GraphEvent.WorkflowCompleted())
              } yield result

            }
        }
      }

      execute(List.from(graph.start), initialState)
    }
  }

  extension[S](graph: Graph[S])
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
        edges.find { case Edge(from, to, condition) =>
          !nodes.contains(from) || !nodes.contains(to)
        } match {
          case Some(Edge(from, to, condition)) =>
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
