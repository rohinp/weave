package com.weave.core

import com.weave.core.GraphEvent.*
import org.scalatest.EitherValues
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

class GraphEventSpec extends AnyWordSpecLike with Matchers with EitherValues {

  "Graph Events" must {
    "emits execution events" in {
      val events = collection.mutable.ListBuffer.empty[GraphEvent]

      val graph = Graph[Int]()
        .addNode(Node("increment", _ + 1))
        .addNode(Node("double", _ * 2))
        .setStart("increment")
        .setEnd("double")
        .addEdge(Edge("increment", "double"))
        .validate()
        .value

      graph.run(
        initialState = 10,
        onEvent = events += _
      )

      events.toList shouldBe List(
        NodeStarted("increment"),
        NodeCompleted("increment"),
        NodeStarted("double"),
        NodeCompleted("double"),
        WorkflowCompleted()
      )
    }

    "emits node and workflow failure events" in {

      val events =
        collection.mutable.ListBuffer.empty[GraphEvent]

      val graph = Graph[Int]()
        .addNode(
          Node(
            "explode",
            _ => throw RuntimeException("boom")
          )
        )
        .setStart("explode")
        .setEnd("explode")

      graph
        .validate()
        .value
        .run(
          10,
          events += _
        )

      events.toList.toString shouldBe List(
        GraphEvent.NodeStarted("explode"),
        GraphEvent.NodeFailed(
          "explode",
          RuntimeException("boom")
        ),
        GraphEvent.WorkflowFailed(
          RuntimeException("boom")
        )
      ).toString
    }

    "middle node fails, start node succeeds" in {

      val events =
        collection.mutable.ListBuffer.empty[GraphEvent]

      val graph = Graph[Int]()
        .addNode(
          Node(
            "success",
            identity
          )
        )
        .addNode(
          Node(
            "explode",
            _ => throw RuntimeException("boom")
          )
        )
        .addNode(
          Node(
            "end",
            identity
          )
        )
        .setStart("success")
        .setEnd("end")
        .addEdge(Edge("success", "explode", _ => true))
        .addEdge(Edge("explode", "end", _ => true))

      graph
        .validate()
        .value
        .run(
          10,
          events += _
        )

      events.toList.toString shouldBe List(
        GraphEvent.NodeStarted("success"),
        GraphEvent.NodeCompleted("success"),
        GraphEvent.NodeStarted("explode"),
        GraphEvent.NodeFailed(
          "explode",
          RuntimeException("boom")
        ),
        GraphEvent.WorkflowFailed(
          RuntimeException("boom")
        )
      ).toString
    }

  }

}
