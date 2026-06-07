package com.weave.core

import com.weave.core.TestData.ChatUpdate

object TestData {
  sealed trait Message[M]

  case class HumanMessage[M](text: M) extends Message[M]

  case class AIMessage[M](text: M) extends Message[M]

  case class ChatState[M](messages: List[Message[M]])

  sealed trait ChatUpdate[M]

  case class AppendMessage[M](message: M) extends ChatUpdate[M]

  val reducer: Reducer[ChatState[Int], ChatUpdate[Int]] = {
    (state: ChatState[Int], update: ChatUpdate[Int]) =>
      update match {
        case AppendMessage(message) =>
          state.copy(messages = state.messages :+ HumanMessage(message))
      }
  }
  val testGraph: Graph[ChatState[Int], ChatUpdate[Int]] =
    Graph[ChatState[Int], ChatUpdate[Int]](reducer)

  def convertToStateFunc(f: Int => Int): ChatState[Int] => ChatUpdate[Int] = state => state.messages.last match {
    case HumanMessage(text) => AppendMessage(f(text))
    case AIMessage(text) => AppendMessage(f(text))
  }

  def createIntNode(name: String, f: Int => Int, retryPolicy: RetryPolicy = RetryPolicy.Never): Node[ChatState[Int], ChatUpdate[Int]] = {
    Node[ChatState[Int], ChatUpdate[Int]](name, convertToStateFunc(f), retryPolicy)
  }

  def createIntState(number:Int): ChatState[Int] = ChatState(List(HumanMessage(number)))

  def createIntEdge(from: String, to:String, f: Int => Boolean):Edge[ChatState[Int]] =
    Edge[ChatState[Int]](from = from, to = to, s => s.messages.last match {
      case HumanMessage(text) => f(text)
      case AIMessage(text) => f(text)
    })
}
