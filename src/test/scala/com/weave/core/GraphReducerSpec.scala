package com.weave.core

import org.scalatest.EitherValues
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

class GraphReducerSpec extends AnyWordSpecLike with Matchers with EitherValues {

  "Graph Reducer" must {
    "applies reducer after node execution" in {
      sealed trait Message
      case class HumanMessage(text: String) extends Message
      case class AIMessage(text: String) extends Message

      case class ChatState(messages: List[Message])

      sealed trait ChatUpdate
      case class AppendMessage(message: Message) extends ChatUpdate

      val reducer =
        new Reducer[ChatState, ChatUpdate] {
          override def reduce(
                               state: ChatState,
                               update: ChatUpdate
                             ): ChatState =
            update match {
              case AppendMessage(message) =>
                state.copy(messages = state.messages :+ message)
            }
        }

      val graph =
        Graph[ChatState, ChatUpdate](reducer)
          .addNode(
            Node(
              "user",
              _ => AppendMessage(HumanMessage("hello"))
            )
          )
          .addNode(
            Node(
              "assistant",
              _ => AppendMessage(AIMessage("hi"))
            )
          )
          .setStart("user")
          .setEnd("assistant")
          .addEdge(Edge("user", "assistant"))
          .validate()
          .value

      val result =
        graph.run(ChatState(Nil))

      result.value.messages shouldBe List(
        HumanMessage("hello"),
        AIMessage("hi")
      )
    }
  }

  "routes using reduced state" in {
    case class CounterState(value: Int)

    sealed trait CounterUpdate
    case class Increment(by: Int) extends CounterUpdate
    case class Multiply(by: Int) extends CounterUpdate

    val reducer =
      new Reducer[CounterState, CounterUpdate] {
        override def reduce(
                             state: CounterState,
                             update: CounterUpdate
                           ): CounterState =
          update match {
            case Increment(by) => state.copy(value = state.value + by)
            case Multiply(by) => state.copy(value = state.value * by)
          }
      }

    val graph =
      Graph[CounterState, CounterUpdate](reducer)
        .addNode(Node("increment", _ => Increment(5)))
        .addNode(Node("small", _ => Multiply(2)))
        .addNode(Node("large", _ => Multiply(10)))
        .setStart("increment")
        .setEnd("large")
        .addEdge(Edge("increment", "large", _.value >= 10))
        .addEdge(Edge("increment", "small", _.value < 10))
        .validate()
        .value

    graph.run(CounterState(5)).value shouldBe CounterState(100)
  }

}
