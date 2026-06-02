package com.weave.core

import org.scalatest.EitherValues
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

class GraphRunnerSpec extends AnyWordSpecLike with Matchers with EitherValues{

  "Graph Runner" must {
    "runs a simple graph from start to end" in {
      val increment = Node[Int]("increment", state => state + 1)
      val double = Node[Int]("double", state => state * 2)

      val graph =
        Graph[Int]()
          .addNode(increment)
          .addNode(double)
          .setStart("increment")
          .addEdge(Edge("increment", "double"))
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
          .addEdge(Edge("a", "b"))
          .addEdge(Edge("b", "c"))

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

    "routes based on state" in {
      val graph =
        Graph[Int]()
          .addNode(Node("start", identity))
          .addNode(Node("positive", _ * 10))
          .addNode(Node("negative", _ * -10))
          .setStart("start")
          .setEnd("positive")
          .setEnd("negative")

          .addEdge(Edge("start", "positive", _ > 0))
          .addEdge(Edge("start", "negative", _ <= 0))

      val result1 =
        graph.validate().toOption.get.run(5)

      val result2 =
        graph.validate().toOption.get.run(-5)

      result1 shouldBe 50
      result2 shouldBe 50
    }

  }

}
