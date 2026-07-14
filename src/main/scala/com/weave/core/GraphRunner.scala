package com.weave.core

import scala.annotation.tailrec
import scala.util.{Failure, Success, Try}
import scala.util.chaining.*

private[core] class GraphRunner[S, U](graph: Graph[S, U]) {

  private def attemptExecuteNode(
                                  node: Node[S, U],
                                  state: S,
                                  onEvent: GraphEvent => Unit
                                ): Try[S] = {
    onEvent(GraphEvent.NodeStarted(node.name))
    Try({
      val update = node.f(state)
      graph.reducer.reduce(state, update)
    }).tap({
      case Failure(ex) =>
        onEvent(GraphEvent.NodeFailed(node.name, ex))
      case Success(nextState) =>
        onEvent(GraphEvent.NodeCompleted(node.name))
        onEvent(GraphEvent.CheckpointCreated(node.name, nextState))
    })
  }

  @tailrec
  private def retryExecuteNode(node: Node[S, U],
                               state: S,
                               onEvent: GraphEvent => Unit,
                               remainingAttempts:Int,
                               previousResult: Try[S]
                              ): Try[S] = {
    previousResult match {
      case success@Success(_) => success
      case failure@Failure(_) if remainingAttempts <= 1 => failure
      case Failure(_) =>
        val result = attemptExecuteNode(node, state, onEvent)
        retryExecuteNode(node, state, onEvent, remainingAttempts - 1, result)
    }
  }

  private def executeNode(
                           node: Node[S, U],
                           state: S,
                           onEvent: GraphEvent => Unit
                         ): GraphError.ExecutionError | S = {
    attemptExecuteNode(node, state, onEvent)
      .pipe(previousResult => {
        node.retryPolicy match {
          case RetryPolicy.Never => previousResult
          case RetryPolicy.FixedAttempts(maxAttempts) =>
            retryExecuteNode(node, state, onEvent, maxAttempts, previousResult)
        }
      }).fold(
        ex => {
          onEvent(GraphEvent.WorkflowFailed("workflow",ex))
          GraphError.RuntimeError(node.name, ex)
        },
        identity
      )
  }

  def run(
           initialState: S,
           onEvent: GraphEvent => Unit = _ => ()
         ): GraphError.ExecutionError | S = {

    //traversal BFS
    def execute(
                 workQueue: List[WorkItem[S]]
               ): GraphError.ExecutionError | S = {
      println(s"Work queue: ${workQueue.map(_.nodeName).mkString(", ")}")
      workQueue match {
        case Nil => initialState
        case head :: tail =>
          val WorkItem(nodeName, state) = head
          val node = graph.nodes(nodeName)
          executeNode(node, state, onEvent) match {
            case error: GraphError.RuntimeError => error
            case newState if workQueue.length == 1 && graph.end.contains(nodeName) => newState
            case newState =>
              // The error branch has been removed; the remaining union member is S.
              val state = newState.asInstanceOf[S]
              val nextNodes = graph.edges
                .filter(edge => edge.from == nodeName && edge.condition(state))
                .map(_.to)
              val nextWork = tail ++ nextNodes.map(n => WorkItem(n, state))
              if (nextWork.isEmpty) state else execute(nextWork)
          }
      }
    }
    execute(List.from(graph.start).map(WorkItem(_,initialState)))
      .tap {
        case _: GraphError.RuntimeError => ()
        case _ => onEvent(GraphEvent.WorkflowCompleted("workflow"))
      }
  }
}
