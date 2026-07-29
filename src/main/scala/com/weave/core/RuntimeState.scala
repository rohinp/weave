package com.weave.core

import scala.collection.immutable.Queue

case class RuntimeState[S](
                            workQueue: Queue[WorkItem[S]],
                            pendingJoins: Map[String, List[JoinInput[S]]]
                          ) {

  def isJoinPending: Boolean = pendingJoins.nonEmpty
  
  def update(newWorkItems: List[WorkItem[S]], newPendingJoins:Map[String, List[JoinInput[S]]]): RuntimeState[S] = 
    copy(workQueue = workQueue ++ newWorkItems, pendingJoins = updatePendingJoins(newPendingJoins))
    
  private def updatePendingJoins(newPendingJoins: Map[String, List[JoinInput[S]]]): Map[String, List[JoinInput[S]]] = {
    newPendingJoins.foldLeft(pendingJoins){
      case (acc, (nodeName, states)) => 
        if acc.contains(nodeName) then acc + (nodeName -> (acc(nodeName) ++ states)) else acc + (nodeName -> states)
    }
  }
}

object RuntimeState {

  case object EmptyWorkQueue
  case object NoneCompletedJoins
  
  def fetchWorkItems[S](state: RuntimeState[S]): (WorkItem[S], RuntimeState[S]) | EmptyWorkQueue.type = {
    state.workQueue.dequeueOption match {
      case Some((workItem, queue)) => (workItem, state.copy(workQueue = queue))
      case None => EmptyWorkQueue
    }
  }

  def finishedJoins[S](runtimeState: RuntimeState[S], graphState: GraphState[S], edges: List[Edge[S]]): List[String] = {
    runtimeState.pendingJoins.toList.foldLeft(List.empty[String]) {
      case (acc, (nodeName, states)) =>
        val allEdges = edges.filter(_.to == nodeName)
        val allParentNodes = allEdges.filter(e => graphState.state.contains(e.from))
        val areAllNodesProcessed = allParentNodes.length == allEdges.length
        if (areAllNodesProcessed) {
          nodeName :: acc
        } else {
          acc
        }
    }
  }
  
}