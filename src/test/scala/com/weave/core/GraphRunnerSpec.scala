package com.weave.core

import org.scalatest.EitherValues
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import TestData.*

class GraphRunnerSpec extends AnyWordSpecLike with Matchers with EitherValues{

  "Graph Runner" must {
    "runs a simple graph from start to end" in {
      val increment = createIntNode(name = "increment", f = state => state + 1)
      val double = createIntNode(name = "double", f = state => state * 2)

      val graph =
        testGraph
          .addNode(increment)
          .addNode(double)
          .setStart("increment")
          .addEdge(Edge("increment", "double"))
          .setEnd("double")

      graph.validate().value.run(createIntState(10)) shouldBe Right(ChatState(List(HumanMessage(10), HumanMessage(11), HumanMessage(22))))

    }

    "stops execution at end node" in {
      val graph =
        testGraph
          .addNode(createIntNode("a", _ + 1))
          .addNode(createIntNode("b", _ * 2))
          .addNode(createIntNode("c", _ - 100))
          .setStart("a")
          .setEnd("b")
          .addEdge(Edge("a", "b"))
          .addEdge(Edge("b", "c"))

      val result =
        graph
          .validate()
          .getOrElse(fail("validation failed"))
          .run(createIntState(10))

      result shouldBe Right(ChatState(List(HumanMessage(10), HumanMessage(11), HumanMessage(22))))
    }

    "returns result when start and end are same node" in {
      val graph =
        testGraph
          .addNode(createIntNode("increment", _ + 1))
          .setStart("increment")
          .setEnd("increment")

      val result =
        graph
          .validate()
          .getOrElse(fail("validation failed"))
          .run(createIntState(10))

      result shouldBe Right(ChatState(List(HumanMessage(10), HumanMessage(11))))
    }

    "routes based on state" in {
      val graph =
        testGraph
          .addNode(createIntNode("start", identity))
          .addNode(createIntNode("positive", _ * 10))
          .addNode(createIntNode("negative", _ * -10))
          .setStart("start")
          .setEnd("positive")
          .setEnd("negative") // Multiple end nodes not handled

          .addEdge(createIntEdge("start", "positive", _ > 0))
          .addEdge(createIntEdge("start", "negative", _ <= 0))

      val result =
        graph.validate().toOption.get.run(createIntState(5))

      result shouldBe Right(ChatState(List(HumanMessage(5), HumanMessage(5), HumanMessage(50))))
    }

  }

}
