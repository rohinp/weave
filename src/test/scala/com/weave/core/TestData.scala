package com.weave.core

import com.weave.core.TestData.ChatUpdate

object TestData {
  extension [S, U](result: GraphError.ValidationError | GraphRunner[S, U])
    def runner: GraphRunner[S, U] = result match {
      case value: GraphRunner[S, U] => value
      case error                    => fail(error.toString)
    }

  extension [S](result: GraphError.ExecutionError | S)
    def state: S = result match {
      case error: GraphError.RuntimeError => fail(error.toString)
      case value                          => value.asInstanceOf[S]
    }

  private def fail(message: String): Nothing =
    throw AssertionError(message)

  sealed trait Message[M]

  case class HumanMessage[M](text: M) extends Message[M]

  case class AIMessage[M](text: M) extends Message[M]

  case class ChatState[M](messages: List[Message[M]])

  sealed trait ChatUpdate[M]

  case class AppendMessage[M](message: M) extends ChatUpdate[M]

  def reducer[T]: Reducer[ChatState[T], ChatUpdate[T]] =
    new Reducer[ChatState[T], ChatUpdate[T]] {
      override def reduce(
          state: ChatState[T],
          update: ChatUpdate[T]
      ): ChatState[T] =
        update match {
          case AppendMessage(message) =>
            state.copy(messages = state.messages :+ HumanMessage(message))
        }

      override def merge(
          left: ChatState[T],
          right: ChatState[T]
      ): ChatState[T] = {
        println(
          s"Merging states: left=${left.messages}, right=${right.messages}"
        )
        ChatState(left.messages ++ right.messages)
      }
    }

  def testGraph[T]: Graph[ChatState[T], ChatUpdate[T]] =
    Graph[ChatState[T], ChatUpdate[T]](reducer)

  def convertToStateFunc[T](f: T => T): ChatState[T] => ChatUpdate[T] = state =>
    state.messages.last match {
      case HumanMessage(text) => AppendMessage(f(text))
      case AIMessage(text)    => AppendMessage(f(text))
    }

  def createNode[T](
      name: String,
      f: T => T,
      retryPolicy: RetryPolicy = RetryPolicy.Never
  ): Node[ChatState[T], ChatUpdate[T]] = {
    Node[ChatState[T], ChatUpdate[T]](name, convertToStateFunc(f), retryPolicy)
  }

  def createState[T](number: T): ChatState[T] = ChatState(
    List(HumanMessage(number))
  )

  def createEdge[T](
      from: String,
      to: String,
      f: T => Boolean
  ): Edge[ChatState[T]] =
    Edge[ChatState[T]](
      from = from,
      to = to,
      s =>
        s.messages.last match {
          case HumanMessage(text) => f(text)
          case AIMessage(text)    => f(text)
        }
    )
}
