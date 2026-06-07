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
                         ): Either[GraphError, S] = {
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
          Left(GraphError.RuntimeError(node.name, ex))
        },
        Right(_)
      )
  }

  def run(
           initialState: S,
           onEvent: GraphEvent => Unit = _ => ()
         ): Either[GraphError, S] = {

    //traversal BFS
    def execute(
                 remainingNode: List[String],
                 state: S
               ): Either[GraphError, S] = {
      remainingNode match {
        //TODO: execution completion needs a single owner.
        case List() => Right(state)
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
            } yield result

          }
      }
    }

    execute(List.from(graph.start), initialState)
      .tap(result => if (result.isRight) onEvent(GraphEvent.WorkflowCompleted("workflow")) else ())
  }
}