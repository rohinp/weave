package com.weave.core

import com.weave.core.GraphEvent.*
import org.scalatest.EitherValues
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import TestData.*

class GraphEventSpec extends AnyWordSpecLike with Matchers with EitherValues {

  "Graph Events" must {
    "emits execution events" in {
      val events = collection.mutable.ListBuffer.empty[GraphEvent]

      val graph = testGraph.addNode(createIntNode("increment", _ + 1))
        .addNode(createIntNode("double", _ * 2))
        .setStart("increment")
        .setEnd("double")
        .addEdge(Edge("increment", "double"))
        .validate()
        .value

      graph.run(
        initialState = createIntState(10),
        onEvent = events += _
      )

      events.toList shouldBe List(
        NodeStarted("increment"),
        NodeCompleted("increment"),
        CheckpointCreated("increment", ChatState(List(HumanMessage(10), HumanMessage(11)))),
        NodeStarted("double"),
        NodeCompleted("double"),
        CheckpointCreated("double", ChatState(List(HumanMessage(10), HumanMessage(11), HumanMessage(22)))),
        WorkflowCompleted("workflow"))
    }

    "emits node and workflow failure events" in {

      val events =
        collection.mutable.ListBuffer.empty[GraphEvent]

      val graph = testGraph
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
          createIntState(10),
          events += _
        )

      events.toList.toString shouldBe List(
        GraphEvent.NodeStarted("explode"),
        GraphEvent.NodeFailed(
          "explode",
          RuntimeException("boom")
        ),
        GraphEvent.WorkflowFailed(
          "workflow",
          RuntimeException("boom")
        )
      ).toString
    }

    "middle node fails, start node succeeds" in {

      val events =
        collection.mutable.ListBuffer.empty[GraphEvent]

      val graph = testGraph
        .addNode(
          createIntNode(
            "success",
            identity
          )
        )
        .addNode(
          createIntNode(
            "explode",
            _ => throw RuntimeException("boom")
          )
        )
        .addNode(
          createIntNode(
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
          createIntState(10),
          events += _
        )

      events.toList shouldBe List(
        GraphEvent.NodeStarted("success"),
        GraphEvent.NodeCompleted("success"),
        CheckpointCreated("success", ChatState(List(HumanMessage(10), HumanMessage(10)))),
        GraphEvent.NodeStarted("explode"),
        GraphEvent.NodeFailed(
          "explode",
          RuntimeException("boom")
        ),
        GraphEvent.WorkflowFailed(
          "workflow",
          RuntimeException("boom")
        )
      )
    }

    "creates checkpoints after successful nodes" in {

      val events =
        collection.mutable.ListBuffer.empty[GraphEvent]

      val graph =
        testGraph
          .addNode(createIntNode("a", _ + 1))
          .addNode(createIntNode("b", _ * 2))
          .setStart("a")
          .setEnd("b")
          .addEdge(Edge("a", "b"))

      graph
        .validate()
        .value
        .run(
          createIntState(10),
          events += _
        )

      events.collect {
        case c: CheckpointCreated[_] => c
      } shouldBe List(
        CheckpointCreated("a", 11),
        CheckpointCreated("b", 22)
      )
    }

  }

}
