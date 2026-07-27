package com.weave.core

import scala.annotation.tailrec
import scala.collection.immutable.Queue
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
  private def retryExecuteNode(
      node: Node[S, U],
      state: S,
      onEvent: GraphEvent => Unit,
      remainingAttempts: Int,
      previousResult: Try[S]
  ): Try[S] = {
    previousResult match {
      case success @ Success(_)                           => success
      case failure @ Failure(_) if remainingAttempts <= 1 => failure
      case Failure(_)                                     =>
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
          case RetryPolicy.Never                      => previousResult
          case RetryPolicy.FixedAttempts(maxAttempts) =>
            retryExecuteNode(node, state, onEvent, maxAttempts, previousResult)
        }
      })
      .fold(
        ex => {
          onEvent(GraphEvent.WorkflowFailed("workflow", ex))
          GraphError.RuntimeError(node.name, ex)
        },
        identity
      )
  }

  def run(
      initialState: S,
      onEvent: GraphEvent => Unit = _ => ()
  ): GraphError.ExecutionError | S = {

    // traversal BFS
    def execute(
        runtimeState: RuntimeState[S],
        stateAcc: GraphState[S],
        onEvent: GraphEvent => Unit
    ): GraphError.ExecutionError | S = {
      // 4. Pick a node from work-queue, execute,
      RuntimeState.fetchWorkItems(runtimeState) match {
        case (WorkItem[S](nodeName, state), newRuntimeState) => {
          val result = executeNode(graph.nodes(nodeName), state, onEvent)
          result match {
            case error @ GraphError.ExecutionError(_, _) => error
            case nextCase                                =>
              // Had to deal with type issues in scala 3 for abstract types in pattern match.
              val newState: S = nextCase.asInstanceOf[S]
              // get child(s). Only those whose edges pass condition.
              val nextNodes = graph.nextNodes(nodeName, newState)
              // 5. If child node is dependent on multiple node, move to pending-joins queue.
              // 6. If child node not dependent then add to work-queue.
              val (nodesForPendingJoins, nodesForWorkQueue) = graph
                .nextNodes(nodeName, newState)
                .partition(graph.isMultipleParentNode)

              val updatedRuntimeState = newRuntimeState.update(
                nodesForWorkQueue.map(n => WorkItem(n, newState)),
                nodesForPendingJoins.map(n => n -> List(newState)).toMap
              )
              // 7. Start the loop again. Continue till both pending-joins and work-queue are empty.
              execute(
                updatedRuntimeState,
                stateAcc.update(nodeName -> newState),
                onEvent
              )
          }
        }

        // Check if Pending Joins.
        case RuntimeState.EmptyWorkQueue if runtimeState.isJoinPending =>
          val finishedJoins =
            RuntimeState.finishedJoins(runtimeState, stateAcc, graph.edges)

          if (finishedJoins.isEmpty) {
            GraphError.RuntimeError(
              "empty work queue",
              new RuntimeException(
                "Empty work queue and pending joins unfinished, this must not happen as it passed validation."
              )
            )
          } else {
            // If all parent processed move to work-queue
            val newRuntimeState = finishedJoins.foldLeft(runtimeState) {
              case (runtimeStateAcc, nodeName) =>
                val head :: tail: List[S] =
                  runtimeState.pendingJoins(nodeName).runtimeChecked
                val combinedState = graph.reducer.merge(head, tail*)
                runtimeStateAcc
                  .copy(
                    workQueue = runtimeStateAcc.workQueue.enqueue(
                      WorkItem(nodeName, combinedState)
                    ),
                    pendingJoins =
                      runtimeStateAcc.pendingJoins.removed(nodeName)
                  )
            }
            execute(newRuntimeState, stateAcc, onEvent)
          }
        case RuntimeState.EmptyWorkQueue =>
          graph.end.flatMap(stateAcc.get) match {
            case Some(resultState) => resultState
            case None              =>
              GraphError.RuntimeError(
                "empty work queue",
                new RuntimeException(
                  "Empty work queue, this must not happen as it passed validation."
                )
              )
          }
      }
    }
    execute(
      RuntimeState(
        Queue.from(graph.start).map(WorkItem(_, initialState)),
        Map.empty
      ),
      GraphState.empty,
      onEvent
    )
      .tap {
        case _: GraphError.RuntimeError => ()
        case _ => onEvent(GraphEvent.WorkflowCompleted("workflow"))
      }
  }
}
