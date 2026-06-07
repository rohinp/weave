package com.weave.core

import org.scalatest.EitherValues
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import TestData.*

class GraphValidationSpec extends AnyWordSpecLike with Matchers with EitherValues{

  "Graph validation" must {
    "validates a correct graph" in {
      val graph =
        testGraph
          .addNode(createIntNode("increment", _ + 1))
          .addNode(createIntNode("double", _ * 2))
          .setStart("increment")
          .setEnd("double")
          .addEdge(Edge("increment", "double"))

      graph.validate().isRight shouldBe true
    }

    "fails validation when start node missing" in {
      val graph =
        testGraph
          .addNode(createIntNode("increment", _ + 1))

      graph.validate() shouldBe Left(GraphError.StartNodeNotDefined())
    }

    "fails validation when start node does not exist" in {
      val graph =
        testGraph
          .addNode(createIntNode("increment", _ + 1))
          .setStart("missing")

      graph.validate() shouldBe Left(GraphError.NodeNotFound("missing"))
    }

    "fails validation when edge target missing" in {
      val graph =
        testGraph
          .addNode(createIntNode("increment", _ + 1))
          .setStart("increment")
          .setEnd("increment")
          .addEdge(Edge("increment", "missing"))

      graph.validate() shouldBe Left(GraphError.NodeNotFound("missing"))
    }
  }

}
