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

      // As of now we execute all nodes, but while returning final state its only the end node.
      events.collect { case GraphEvent.NodeStarted(name) =>
        name
      } shouldBe List("a", "b", "c")

      // this is end state result node b, although c is also executed
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

    /*
     * TODO: This one fails need a fix, not handled a situation where conditions are applied on edges.
     * */
    "routes based on state" in {
      val events = collection.mutable.ListBuffer.empty[GraphEvent]

      val graph =
        testGraph
          .addNode(createNode[Int]("start", identity))
          .addNode(createNode("positive", _ * 10))
          .addNode(createNode("negative", _ * -10))
          .setStart("start")
          .setEnd("positive")
          .setEnd("negative") // Multiple end nodes not handled
          .addEdge(createEdge("start", "positive", _ > 0))
          .addEdge(createEdge("start", "negative", _ <= 0))

      val result =
        graph.validate().runner.run(createState(5), onEvent = events += _)

      println(events)
      result.state shouldBe ChatState(
        List(HumanMessage(5), HumanMessage(5), HumanMessage(50))
      )
    }

    "released join executes only once and does not remain pending" in {
      val events = collection.mutable.ListBuffer.empty[GraphEvent]

      // Build normal diamond graph and run.

      events.count {
        case GraphEvent.NodeStarted("merge") => true
        case _                               => false
      } shouldBe 1

      events.count {
        case GraphEvent.NodeCompleted("merge") => true
        case _                                 => false
      } shouldBe 1
    }

  }

}
