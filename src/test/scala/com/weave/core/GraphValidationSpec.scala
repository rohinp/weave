package com.weave.core

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import TestData.*

class GraphValidationSpec extends AnyWordSpecLike with Matchers {

  "Graph validation" must {
    "validates a correct graph" in {
      val graph =
        testGraph
          .addNode(createNode[Int]("increment", _ + 1))
          .addNode(createNode("double", _ * 2))
          .setStart("increment")
          .setEnd("double")
          .addEdge(Edge("increment", "double"))

      graph.validate() match {
        case _: GraphRunner[?, ?] => succeed
        case error                => fail(error.toString)
      }
    }

    "fails validation when start node missing" in {
      val graph =
        testGraph
          .addNode(createNode[Int]("increment", _ + 1))

      graph.validate() shouldBe GraphError.StartNodeNotDefined()
    }

    "fails validation when start node does not exist" in {
      val graph =
        testGraph
          .addNode(createNode[Int]("increment", _ + 1))
          .setStart("missing")

      graph.validate() shouldBe GraphError.NodeNotFound("missing")
    }

    "fails validation when edge target missing" in {
      val graph =
        testGraph
          .addNode(createNode[Int]("increment", _ + 1))
          .setStart("increment")
          .setEnd("increment")
          .addEdge(Edge("increment", "missing"))

      graph.validate() shouldBe GraphError.NodeNotFound("missing")
    }

    "check if a node has multiple parent" in {
      val graph =
        testGraph
          .addNode(createNode[Int]("start", _ + 0))
          .addNode(createNode[Int]("a", _ + 1))
          .addNode(createNode[Int]("b", _ + 2))
          .addNode(createNode[Int]("m", _ + 0))
          .setStart("start")
          .setEnd("m")
          .addEdge(Edge("start", "a"))
          .addEdge(Edge("start", "b"))
          .addEdge(Edge("a", "m"))
          .addEdge(Edge("b", "m"))

      val (x, y) = graph
        .nextNodes("b", ChatState(List(AIMessage(0))))
        .partition(graph.isMultipleParentNode)

      x shouldBe List("m")
      y shouldBe List()
    }

  }

}
