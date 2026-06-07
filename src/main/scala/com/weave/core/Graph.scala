package com.weave.core

case class Graph[S, U] private(
                                nodes: Map[String, Node[S, U]],
                                edges: List[Edge[S]],
                                start: Option[String],
                                end: Option[String],
                                reducer: Reducer[S, U]
                              ) {
  override def toString: String = {
    val rendered =
      nodes.keys.toList.sorted.map { node =>
        edges.find(_.from == node) match {
          case Some(next) => s"  $node ──${next.condition}──▶ ${next.to}"
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
  def apply[S, U](reducer:Reducer[S,U]): Graph[S, U] = new Graph(
    nodes = Map.empty,
    edges = List.empty,
    start = None,
    end = None,
    reducer = reducer
  )

  extension [S, U](graph: Graph[S, U])
    def addNode(node: Node[S, U]): Graph[S, U] =
      graph.copy(nodes = graph.nodes + (node.name -> node))

  extension [S, U](graph: Graph[S, U])
    def addEdge(edge: Edge[S]): Graph[S, U] =
      graph.copy(edges = edge :: graph.edges)

  extension [S,U](graph: Graph[S, U])
    def setStart(nodeName: String): Graph[S, U] =
      graph.copy(start = Some(nodeName))

  extension [S, U](graph: Graph[S, U])
    def setEnd(nodeName: String): Graph[S, U] =
      graph.copy(end = Some(nodeName))

  extension [S, U](graph: Graph[S, U])
    /*TODO: Check mixin types*/
    def validate(): Either[GraphError, GraphRunner[S, U]] = {
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
          case None => Right(new GraphRunner[S,U](graph)) // graph
        }
      }
    }
}
