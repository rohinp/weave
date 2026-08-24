package com.weave.core

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import TestData.*

class GraphRunnerSpec extends AnyWordSpecLike with Matchers {

  "Graph Runner" must {
    "runs a simple graph from start to end" in {
      val increment =
        createNode[Int](name = "increment", f = state => state + 1)
      val double = createNode[Int](name = "double", f = state => state * 2)

      val graph =
        testGraph
          .addNode(increment)
          .addNode(double)
          .setStart("increment")
          .addEdge(Edge("increment", "double"))
          .setEnd("double")

      graph.validate().runner.run(createState(10)).state shouldBe ChatState(
        List(HumanMessage(10), HumanMessage(11), HumanMessage(22))
      )

    }

    "stops execution at end node" in {
      val events = collection.mutable.ListBuffer.empty[GraphEvent]

      val graph =
        testGraph
          .addNode(createNode[Int]("a", _ + 1))
          .addNode(createNode("b", _ * 2))
          .addNode(createNode("c", _ - 100))
          .setStart("a")
          .setEnd("b")
          .addEdge(Edge("a", "b"))
          .addEdge(Edge("b", "c"))

      val result = {
        graph
          .validate()
          .runner
          .run(createState(10), onEvent = events += _)
      }

      events.collect { case GraphEvent.NodeStarted(name) =>
        name
      } shouldBe List("a", "b")

      result.state shouldBe ChatState(
        List(HumanMessage(10), HumanMessage(11), HumanMessage(22))
      )
    }

    "returns result when start and end are same node" in {
      val graph =
        testGraph
          .addNode(createNode[Int]("increment", _ + 1))
          .setStart("increment")
          .setEnd("increment")

      val result =
        graph
          .validate()
          .runner
          .run(createState(10))

      result.state shouldBe ChatState(List(HumanMessage(10), HumanMessage(11)))
    }

    "routes to the matching conditional branch" in {
      val events = collection.mutable.ListBuffer.empty[GraphEvent]

      val graph =
        testGraph
          .addNode(createNode[Int]("start", identity))
          .addNode(createNode("positive", _ * 10))
          .addNode(createNode("negative", _ * -10))
          .setStart("start")
          .setEnd("positive")
          .addEdge(createEdge("start", "positive", _ > 0))
          .addEdge(createEdge("start", "negative", _ <= 0))

      val result =
        graph.validate().runner.run(createState(5), onEvent = events += _)

      result.state shouldBe ChatState(
        List(HumanMessage(5), HumanMessage(5), HumanMessage(50))
      )

      events.collect { case GraphEvent.NodeStarted(name) => name } shouldBe
        List("start", "positive")
    }

    "released join executes only once and does not remain pending" in {
      val events = collection.mutable.ListBuffer.empty[GraphEvent]

      val graph = testGraph
        .addNode(createNode[Int]("start", identity))
        .addNode(createNode("left", _ + 1))
        .addNode(createNode("right", _ + 2))
        .addNode(createNode("merge", identity))
        .setStart("start")
        .setEnd("merge")
        .addEdge(Edge("start", "left"))
        .addEdge(Edge("start", "right"))
        .addEdge(Edge("left", "merge"))
        .addEdge(Edge("right", "merge"))
        .validate()
        .runner

      graph.run(createState(0), events += _).state

      events.count {
        case GraphEvent.NodeStarted("merge") => true
        case _                               => false
      } shouldBe 1

      events.count {
        case GraphEvent.NodeCompleted("merge") => true
        case _                                 => false
      } shouldBe 1
    }

    "continues already-ready branches after the end node" in {
      val events = collection.mutable.ListBuffer.empty[GraphEvent]

      val graph = testGraph
        .addNode(createNode[Int]("start", identity))
        .addNode(createNode("end", _ + 1))
        .addNode(createNode("other", _ + 2))
        .setStart("start")
        .setEnd("end")
        .addEdge(Edge("start", "end"))
        .addEdge(Edge("start", "other"))
        .validate()
        .runner

      graph.run(createState(0), events += _).state

      events.collect { case GraphEvent.NodeStarted(name) => name } shouldBe
        List("start", "end", "other")
    }

    "reports a deadlock when a strict join cannot receive every parent" in {
      val events = collection.mutable.ListBuffer.empty[GraphEvent]

      val graph = testGraph
        .addNode(createNode[Int]("start", identity))
        .addNode(createNode("active", _ + 1))
        .addNode(createNode("inactive", _ + 2))
        .addNode(createNode("merge", identity))
        .setStart("start")
        .setEnd("merge")
        .addEdge(Edge("start", "active"))
        .addEdge(createEdge("start", "inactive", _ => false))
        .addEdge(Edge("active", "merge"))
        .addEdge(Edge("inactive", "merge"))
        .validate()
        .runner

      val result = graph.run(createState(0), events += _)

      result shouldBe GraphError.JoinDeadlock(Set("merge"))
      events.collect { case GraphEvent.WorkflowFailed(_, cause) =>
        cause.getMessage
      } shouldBe List("Workflow cannot progress; pending joins: merge")
      events.collect { case GraphEvent.WorkflowCompleted(name) =>
        name
      } shouldBe empty
    }

  }

}
