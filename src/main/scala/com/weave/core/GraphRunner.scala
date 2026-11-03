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
  ): Either[GraphError.ExecutionError, S] = {
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
          Left(GraphError.RuntimeError(node.name, ex))
        },
        Right(_)
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
      def fail(error: GraphError.ExecutionError): GraphError.ExecutionError = {
        onEvent(GraphEvent.WorkflowFailed("workflow", error.cause))
        error
      }

      // 4. Pick a node from work-queue, execute,
      runtimeState.dequeue match {
        case Some(workItem, newRuntimeState) =>
          val result =
            executeNode(graph.nodes(workItem.nodeName), workItem.state, onEvent)
          result match {
            case Left(error)     => error
            case Right(newState) =>
              // get child(s). Only those whose edges pass condition.
              // 5. If child node is dependent on multiple node, move to pending-joins queue.
              // 6. If child node not dependent then add to work-queue.
              val nextNodes =
                if (graph.end.contains(workItem.nodeName)) List.empty
                else graph.nextNodes(workItem.nodeName, newState)

              val (nodesForPendingJoins, nodesForWorkQueue) = nextNodes
                .partition(graph.isMultipleParentNode)

              val updatedRuntimeState = newRuntimeState
                .enqueue(nodesForWorkQueue.map(n => WorkItem(n, newState)))
                .addMultipleJoinArrival(
                  nodesForPendingJoins
                    .map(n => PendingJoinInput(n, workItem.nodeName, newState))
                )

              // 7. Start the loop again. Continue till both pending-joins and work-queue are empty.
              execute(
                updatedRuntimeState,
                stateAcc.update(workItem.nodeName -> newState),
                onEvent
              )
          }

        // Check if Pending Joins.
        case None if runtimeState.isJoinPending =>
          val finishedJoins =
            RuntimeState.finishedJoins(
              runtimeState,
              nodeName => graph.parentNodes(nodeName).toSet
            )

          if (finishedJoins.isEmpty) {
            fail(GraphError.JoinDeadlock(runtimeState.pendingJoins.keySet))
          } else {
            // If all parent processed move to work-queue
            val newRuntimeState = finishedJoins.foldLeft(runtimeState) {
              case (runtimeStateAcc, nodeName) =>
                val arrivals = runtimeState.pendingJoins(nodeName)
                val joinInputs = graph.parentNodes(nodeName).map(arrivals)
                val combinedState = graph.reducer.merge(
                  joinInputs.head,
                  joinInputs.tail*
                )
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
        case None =>
          graph.end.flatMap(stateAcc.get) match {
            case Some(resultState) => resultState
            case None              =>
              fail(
                GraphError.RuntimeError(
                  "empty work queue",
                  new RuntimeException(
                    "Workflow completed without executing the configured end node."
                  )
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
        case _: GraphError.ExecutionError => ()
        case _ => onEvent(GraphEvent.WorkflowCompleted("workflow"))
      }
  }
}
