package com.weave.core

import org.scalatest.EitherValues
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

class GraphSpec extends AnyWordSpecLike with Matchers with EitherValues{

  "Graph" must {
    "runs a simple graph from start to end" in {
      val increment = Node[Int]("increment", state => state + 1)
      val double = Node[Int]("double", state => state * 2)

      val graph =
        Graph[Int]()
          .addNode(increment)
          .addNode(double)
          .setStart("increment")
          .addEdge("increment", "double")
          .setEnd("double")

      graph.validate().value.run(10) shouldBe 22

    }

    "stops execution at end node" in {
      val graph =
        Graph[Int]()
          .addNode(Node("a", _ + 1))
          .addNode(Node("b", _ * 2))
          .addNode(Node("c", _ - 100))
          .setStart("a")
          .setEnd("b")
          .addEdge("a", "b")
          .addEdge("b", "c")

      val result =
        graph
          .validate()
          .getOrElse(fail("validation failed"))
          .run(10)

      result shouldBe 22
    }

    "returns result when start and end are same node" in {
      val graph =
        Graph[Int]()
          .addNode(Node("increment", _ + 1))
          .setStart("increment")
          .setEnd("increment")

      val result =
        graph
          .validate()
          .getOrElse(fail("validation failed"))
          .run(10)

      result shouldBe 11
    }

  }


  "Graph validation" must {
    "validates a correct graph" in {
      val graph =
        Graph[Int]()
          .addNode(Node("increment", _ + 1))
          .addNode(Node("double", _ * 2))
          .setStart("increment")
          .setEnd("double")
          .addEdge("increment", "double")

      graph.validate().isRight shouldBe true
    }

    "fails validation when start node missing" in {
      val graph =
        Graph[Int]()
          .addNode(Node("increment", _ + 1))

      graph.validate() shouldBe Left(GraphError.StartNodeNotDefined())
    }

    "fails validation when start node does not exist" in {
      val graph =
        Graph[Int]()
          .addNode(Node("increment", _ + 1))
          .setStart("missing")

      graph.validate() shouldBe Left(GraphError.NodeNotFound("missing"))
    }

    "fails validation when edge target missing" in {
      val graph =
        Graph[Int]()
          .addNode(Node("increment", _ + 1))
          .setStart("increment")
          .setEnd("increment")
          .addEdge("increment", "missing")

      graph.validate() shouldBe Left(GraphError.NodeNotFound("missing"))
    }
  }

}
