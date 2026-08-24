package com.weave.core

import scala.collection.immutable.Queue

case class RuntimeState[S](
    workQueue: Queue[WorkItem[S]],
    pendingJoins: Map[String, Map[String, S]]
) {

  def isJoinPending: Boolean = pendingJoins.nonEmpty

  def dequeue: Option[(WorkItem[S], RuntimeState[S])] =
    workQueue.dequeueOption.map { case (item, remaining) =>
      item -> copy(workQueue = remaining)
    }

  def enqueue(items: Iterable[WorkItem[S]]): RuntimeState[S] =
    copy(workQueue = workQueue.enqueueAll(items))

  private def addJoinArrival(
      joinInput: PendingJoinInput[S]
  ): RuntimeState[S] =
    copy(
      pendingJoins = pendingJoins.updatedWith(joinInput.joinNode) {
        case Some(arrivals) =>
          Some(arrivals.updated(joinInput.parentNode, joinInput.state))

        case None =>
          Some(Map(joinInput.parentNode -> joinInput.state))
      }
    )

  def addMultipleJoinArrival(
      joinInputs: List[PendingJoinInput[S]]
  ): RuntimeState[S] = joinInputs.foldLeft(this) { case (acc, pji) =>
    acc.addJoinArrival(pji)
  }

  def removeJoin(nodeName: String): RuntimeState[S] =
    copy(pendingJoins = pendingJoins.removed(nodeName))

}

object RuntimeState {
  // join completion depends on arrivals
  def finishedJoins[S](
      runtimeState: RuntimeState[S],
      expectedParents: String => Set[String]
  ): List[String] =
    runtimeState.pendingJoins.collect {
      case (nodeName, states) if states.keySet == expectedParents(nodeName) =>
        nodeName
    }.toList

}
