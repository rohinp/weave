package com.weave.core

import scala.collection.concurrent.TrieMap

case class GraphState[S](state: TrieMap[String, S]) {
  def update(nodeState: (String, S)): GraphState[S] = copy(state + nodeState)
  def get(nodeName: String): Option[S] = state.get(nodeName)
}

object GraphState {
  def empty[S]: GraphState[S] = GraphState(TrieMap.empty)
}
