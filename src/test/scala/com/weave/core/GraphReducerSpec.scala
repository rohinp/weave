package com.weave.core

import com.weave.core.GraphEvent.{CheckpointCreated, NodeCompleted, NodeStarted, WorkflowCompleted}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import TestData.*
import org.scalatest.OptionValues.convertOptionToValuable

class GraphReducerSpec extends AnyWordSpecLike with Matchers {

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
          ): ChatState = {
            update match {
              case AppendMessage(message) =>
                state.copy(messages = state.messages :+ message)
            }
          }

          override def merge(left: ChatState, right: ChatState): ChatState =
            ChatState(left.messages ++ right.messages)
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
          .runner

      val result =
        graph.run(ChatState(Nil))

      result.state.messages shouldBe List(
        HumanMessage("hello"),
        AIMessage("hi")
      )
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
              case Multiply(by)  => state.copy(value = state.value * by)
            }

          override def merge(
              left: CounterState,
              right: CounterState
          ): CounterState =
            CounterState(left.value + right.value)
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
          .runner

      graph.run(CounterState(5)).state shouldBe CounterState(100)
    }

    "branches receive independent copies of state" in {
      val events = collection.mutable.ListBuffer.empty[GraphEvent]

      case class CounterState(value: List[Int])

      sealed trait CounterUpdate
      case class Increment(by: Int) extends CounterUpdate

      val reducer =
        new Reducer[CounterState, CounterUpdate] {
          override def reduce(
              state: CounterState,
              update: CounterUpdate
          ): CounterState =
            update match {
              case Increment(by) =>
                state.value match {
                  case Nil          => CounterState(List(by))
                  case head :: tail => CounterState((head + by) :: tail)
                }
            }

          override def merge(
              left: CounterState,
              right: CounterState
          ): CounterState =
            CounterState(left.value ++ right.value)
        }

      val graph =
        Graph[CounterState, CounterUpdate](reducer)
          .addNode(Node("start", _ => Increment(5)))
          .addNode(Node("branch1", _ => Increment(10)))
          .addNode(Node("branch2", _ => Increment(20)))
          .setStart("start")
          .setEnd("branch1")
          .addEdge(Edge("start", "branch1"))
          .addEdge(Edge("start", "branch2"))
          .validate()
          .runner

      val result = graph
        .run(
          CounterState(0 :: Nil),
          onEvent = events += _
        )
        .state

      events.toList shouldBe List(
        NodeStarted("start"),
        NodeCompleted("start"),
        CheckpointCreated("start", CounterState(5 :: Nil)),
        NodeStarted("branch2"),
        NodeCompleted("branch2"),
        CheckpointCreated("branch2", CounterState(List(5, 25))),
        NodeStarted("branch1"),
        NodeCompleted("branch1"),
        CheckpointCreated("branch1", CounterState(List(5, 15))),
        WorkflowCompleted("workflow")
      )
    }

    "merge node executes exactly once" in {
      val events = collection.mutable.ListBuffer.empty[GraphEvent]

      case class CounterState(value: List[Int])

      sealed trait CounterUpdate
      case class Increment(by: Int) extends CounterUpdate

      val reducer =
        new Reducer[CounterState, CounterUpdate] {
          override def reduce(
              state: CounterState,
              update: CounterUpdate
          ): CounterState =
            update match {
              case Increment(by) =>
                state.value match {
                  case Nil          => CounterState(List(by))
                  case head :: tail => CounterState((head + by) :: tail)
                }
            }

          override def merge(
              left: CounterState,
              right: CounterState
          ): CounterState =
            CounterState(left.value ++ right.value)
        }

      val graph =
        Graph[CounterState, CounterUpdate](reducer)
          .addNode(Node("start", _ => Increment(5)))
          .addNode(Node("branch1", _ => Increment(10)))
          .addNode(Node("branch2", _ => Increment(20)))
          .addNode(Node("merge", _ => Increment(0)))
          .setStart("start")
          .setEnd("merge")
          .addEdge(Edge("start", "branch1"))
          .addEdge(Edge("start", "branch2"))
          .addEdge(Edge("branch1", "merge"))
          .addEdge(Edge("branch2", "merge"))
          .validate()
          .runner

      graph
        .run(
          CounterState(0 :: Nil),
          onEvent = events += _
        )
        .state

      val mergeCount = events.count {
        case NodeStarted("merge") => true
        case _                    => false
      }
      println(s"Merge node executed $mergeCount times")
      println(s"Events: ${events.toList}")
      mergeCount shouldBe 1
    }

    "reducer merges multiple updates" in {

      case class ChatState(messages: List[String])

      sealed trait ChatUpdate
      case class AppendMessage(message: String) extends ChatUpdate

      val reducer =
        new Reducer[ChatState, ChatUpdate] {

          override def reduce(
              state: ChatState,
              update: ChatUpdate
          ): ChatState =
            update match {
              case AppendMessage(msg) =>
                state.copy(messages = state.messages :+ msg)
            }

          override def merge(left: ChatState, right: ChatState): ChatState =
            ChatState(left.messages ++ right.messages)
        }

      val initial = ChatState(List("question"))

      val afterDocs = reducer.reduce(
        initial,
        AppendMessage("doc answer")
      )

      val finalState = reducer.reduce(
        afterDocs,
        AppendMessage("web answer")
      )

      finalState.messages shouldBe List(
        "question",
        "doc answer",
        "web answer"
      )
    }

    "merges branch states" in {
      case class ChatState(messages: List[String])

      sealed trait ChatUpdate
      case class AppendMessage(message: String) extends ChatUpdate

      val left =
        ChatState(
          List(
            "question",
            "doc answer"
          )
        )

      val right =
        ChatState(
          List(
            "question",
            "web answer"
          )
        )

      val reducer =
        new Reducer[ChatState, ChatUpdate] {

          override def reduce(
              state: ChatState,
              update: ChatUpdate
          ): ChatState =
            update match {
              case AppendMessage(msg) =>
                state.copy(messages = state.messages :+ msg)
            }

          override def merge(left: ChatState, right: ChatState): ChatState =
            ChatState(left.messages ++ right.messages.drop(1))
        }

      reducer
        .merge(
          left,
          right
        )
        .messages shouldBe List(
        "question",
        "doc answer",
        "web answer"
      )
    }

    "all queued work items are processed" in {
      val events = collection.mutable.ListBuffer.empty[GraphEvent]

      case class CounterState(value: List[Int])

      sealed trait CounterUpdate
      case class Increment(by: Int) extends CounterUpdate

      val reducer =
        new Reducer[CounterState, CounterUpdate] {
          override def reduce(
              state: CounterState,
              update: CounterUpdate
          ): CounterState =
            update match {
              case Increment(by) =>
                state.value match {
                  case Nil          => CounterState(List(by))
                  case head :: tail => CounterState((head + by) :: tail)
                }
            }

          override def merge(
              left: CounterState,
              right: CounterState
          ): CounterState =
            CounterState(left.value ++ right.value)
        }

      val graph =
        Graph[CounterState, CounterUpdate](reducer)
          .addNode(Node("start", _ => Increment(5)))
          .addNode(Node("branch1", _ => Increment(10)))
          .addNode(Node("branch2", _ => Increment(20)))
          .addNode(Node("merge", _ => Increment(4)))
          .setStart("start")
          .setEnd("merge")
          .addEdge(Edge("start", "branch1"))
          .addEdge(Edge("start", "branch2"))
          .addEdge(Edge("branch1", "merge"))
          .addEdge(Edge("branch2", "merge"))
          .validate()
          .runner

      graph
        .run(
          CounterState(0 :: Nil),
          onEvent = events += _
        )
        .state

      val startedNodes = events.collect { case NodeStarted(name) =>
        name
      }

      startedNodes.toSet shouldBe Set(
        "start",
        "branch1",
        "branch2",
        "merge"
      )
    }

    "join starts only after all parents complete" in {
      val events = collection.mutable.ListBuffer.empty[GraphEvent]

      case class CounterState(value: List[Int])

      sealed trait CounterUpdate
      case class Increment(by: Int) extends CounterUpdate

      val reducer =
        new Reducer[CounterState, CounterUpdate] {
          override def reduce(
              state: CounterState,
              update: CounterUpdate
          ): CounterState =
            update match {
              case Increment(by) =>
                state.value match {
                  case Nil          => CounterState(List(by))
                  case head :: tail => CounterState((head + by) :: tail)
                }
            }

          override def merge(
              left: CounterState,
              right: CounterState
          ): CounterState =
            CounterState(left.value ++ right.value)
        }

      val graph =
        Graph[CounterState, CounterUpdate](reducer)
          .addNode(Node("start", _ => Increment(5)))
          .addNode(Node("branch1", _ => Increment(10)))
          .addNode(Node("branch2", _ => Increment(20)))
          .addNode(Node("merge", _ => Increment(4)))
          .setStart("start")
          .setEnd("merge")
          .addEdge(Edge("start", "branch1"))
          .addEdge(Edge("start", "branch2"))
          .addEdge(Edge("branch1", "merge"))
          .addEdge(Edge("branch2", "merge"))
          .validate()
          .runner

      graph
        .run(
          CounterState(0 :: Nil),
          onEvent = events += _
        )
        .state

      val eventList = events.toList

      val branch1Completed =
        eventList.indexOf(NodeCompleted("branch1"))

      val branch2Completed =
        eventList.indexOf(NodeCompleted("branch2"))

      val mergeStarted =
        eventList.indexOf(NodeStarted("merge"))

      branch1Completed should be >= 0
      branch2Completed should be >= 0
      mergeStarted should be > branch1Completed
      mergeStarted should be > branch2Completed
    }

  }

  "join receives merged states from both branches" in {
    case class State(values: Vector[String])

    sealed trait Update
    case class Append(value: String) extends Update

    val reducer = new Reducer[State, Update] {
      override def reduce(state: State, update: Update): State =
        update match {
          case Append(value) =>
            state.copy(values = state.values :+ value)
        }

      override def merge(left: State, right: State): State =
        State((left.values ++ right.values).distinct)
    }

    var stateObservedByMerge: Option[State] = None

    val graph =
      Graph[State, Update](reducer)
        .addNode(Node("start", _ => Append("start")))
        .addNode(Node("docs", _ => Append("docs")))
        .addNode(Node("web", _ => Append("web")))
        .addNode(
          Node(
            "merge",
            state => {
              stateObservedByMerge = Some(state)
              Append("merged")
            }
          )
        )
        .setStart("start")
        .setEnd("merge")
        .addEdge(Edge("start", "docs"))
        .addEdge(Edge("start", "web"))
        .addEdge(Edge("docs", "merge"))
        .addEdge(Edge("web", "merge"))
        .validate()
        .runner

    val result = graph.run(State(Vector.empty))

    stateObservedByMerge.value.values.toSet shouldBe Set(
      "start",
      "docs",
      "web"
    )

    result.state.values.toSet shouldBe Set(
      "start",
      "docs",
      "web",
      "merged"
    )
  }
}
