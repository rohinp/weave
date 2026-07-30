package com.weave.core

import scala.collection.immutable.Queue

case class RuntimeState[S](
    workQueue: Queue[WorkItem[S]],
    pendingJoins: Map[String, List[JoinInput[S]]]
) {

  def isJoinPending: Boolean = pendingJoins.nonEmpty

  def update(
      newWorkItems: List[WorkItem[S]],
      newPendingJoins: Map[String, List[JoinInput[S]]]
  ): RuntimeState[S] =
    copy(
      workQueue = workQueue ++ newWorkItems,
      pendingJoins = updatePendingJoins(newPendingJoins)
    )

  private def updatePendingJoins(
      newPendingJoins: Map[String, List[JoinInput[S]]]
  ): Map[String, List[JoinInput[S]]] = {
    newPendingJoins.foldLeft(pendingJoins) { case (acc, (nodeName, states)) =>
      if acc.contains(nodeName) then
        acc + (nodeName -> (acc(nodeName) ++ states))
      else acc + (nodeName -> states)
    }
  }
}

object RuntimeState {

  case object EmptyWorkQueue
  case object NoneCompletedJoins

  case class PendingJoinInput[S](
      joinNode: String,
      fromNode: String,
      state: S
  )

  def fetchWorkItems[S](
      state: RuntimeState[S]
  ): (WorkItem[S], RuntimeState[S]) | EmptyWorkQueue.type = {
    state.workQueue.dequeueOption match {
      case Some((workItem, queue)) => (workItem, state.copy(workQueue = queue))
      case None                    => EmptyWorkQueue
    }
  }

  // join completion depends on arrivals
  def finishedJoins[S](
      runtimeState: RuntimeState[S],
      incomingEdgeCount: String => Int
  ): List[String] =
    runtimeState.pendingJoins.collect {
      case (nodeName, states) if states.size == incomingEdgeCount(nodeName) =>
        nodeName
    }.toList

}
