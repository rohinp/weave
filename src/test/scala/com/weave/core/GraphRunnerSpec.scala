package com.weave.core

import org.scalatest.EitherValues
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import TestData.*

class GraphRunnerSpec extends AnyWordSpecLike with Matchers with EitherValues{

  "Graph Runner" must {
    "runs a simple graph from start to end" in {
      val increment = createNode[Int](name = "increment", f = state => state + 1)
      val double = createNode[Int](name = "double", f = state => state * 2)

      val graph =
        testGraph
          .addNode(increment)
          .addNode(double)
          .setStart("increment")
          .addEdge(Edge("increment", "double"))
          .setEnd("double")

      graph.validate().value.run(createState(10)) shouldBe Right(ChatState(List(HumanMessage(10), HumanMessage(11), HumanMessage(22))))

    }

    "stops execution at end node" in {
      val graph =
        testGraph
          .addNode(createNode[Int]("a", _ + 1))
          .addNode(createNode("b", _ * 2))
          .addNode(createNode("c", _ - 100))
          .setStart("a")
          .setEnd("b")
          .addEdge(Edge("a", "b"))
          .addEdge(Edge("b", "c"))

      val result =
        graph
          .validate()
          .getOrElse(fail("validation failed"))
          .run(createState(10))

      result shouldBe Right(ChatState(List(HumanMessage(10), HumanMessage(11), HumanMessage(22))))
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
          .getOrElse(fail("validation failed"))
          .run(createState(10))

      result shouldBe Right(ChatState(List(HumanMessage(10), HumanMessage(11))))
    }

    "routes based on state" in {
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
        graph.validate().toOption.get.run(createState(5))

      result shouldBe Right(ChatState(List(HumanMessage(5), HumanMessage(5), HumanMessage(50))))
    }

  }

}
