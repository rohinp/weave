package com.weave.core

import com.weave.core.GraphEvent.*
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import TestData.*

class GraphEventSpec extends AnyWordSpecLike with Matchers {

  "Graph Events" must {
    "emits execution events" in {
      val events = collection.mutable.ListBuffer.empty[GraphEvent]

      val graph = testGraph
        .addNode(createNode[Int]("increment", _ + 1))
        .addNode(createNode("double", _ * 2))
        .setStart("increment")
        .setEnd("double")
        .addEdge(Edge("increment", "double"))
        .validate()
        .runner

      graph.run(
        initialState = createState(10),
        onEvent = events += _
      )

      events.toList shouldBe List(
        NodeStarted("increment"),
        NodeCompleted("increment"),
        CheckpointCreated(
          "increment",
          ChatState(List(HumanMessage(10), HumanMessage(11)))
        ),
        NodeStarted("double"),
        NodeCompleted("double"),
        CheckpointCreated(
          "double",
          ChatState(List(HumanMessage(10), HumanMessage(11), HumanMessage(22)))
        ),
        WorkflowCompleted("workflow")
      )
    }

    "emits node and workflow failure events" in {

      val events =
        collection.mutable.ListBuffer.empty[GraphEvent]
      val failure = RuntimeException("boom")

      val graph = testGraph
        .addNode(
          Node(
            "explode",
            _ => throw failure
          )
        )
        .setStart("explode")
        .setEnd("explode")

      graph
        .validate()
        .runner
        .run(
          createState(10),
          events += _
        )

      events.toList shouldBe List(
        GraphEvent.NodeStarted("explode"),
        GraphEvent.NodeFailed("explode", failure),
        GraphEvent.WorkflowFailed("workflow", failure)
      )
    }

    "middle node fails, start node succeeds" in {

      val events =
        collection.mutable.ListBuffer.empty[GraphEvent]
      val failure = RuntimeException("boom")

      val graph = testGraph
        .addNode(
          createNode(
            "success",
            identity
          )
        )
        .addNode(
          createNode(
            "explode",
            _ => throw failure
          )
        )
        .addNode(
          createNode(
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
        .runner
        .run(
          createState(10),
          events += _
        )

      events.toList shouldBe List(
        GraphEvent.NodeStarted("success"),
        GraphEvent.NodeCompleted("success"),
        CheckpointCreated(
          "success",
          ChatState(List(HumanMessage(10), HumanMessage(10)))
        ),
        GraphEvent.NodeStarted("explode"),
        GraphEvent.NodeFailed(
          "explode",
          failure
        ),
        GraphEvent.WorkflowFailed(
          "workflow",
          failure
        )
      )
    }

    "creates checkpoints after successful nodes" in {

      val events =
        collection.mutable.ListBuffer.empty[GraphEvent]

      val graph =
        testGraph
          .addNode(createNode[Int]("a", _ + 1))
          .addNode(createNode("b", _ * 2))
          .setStart("a")
          .setEnd("b")
          .addEdge(Edge("a", "b"))

      graph
        .validate()
        .runner
        .run(
          createState(10),
          events += _
        )

      events.collect { case c: CheckpointCreated[_] =>
        c
      } shouldBe List(
        CheckpointCreated(
          "a",
          createState(10)
            .copy(messages = List(HumanMessage(10), HumanMessage(11)))
        ),
        CheckpointCreated(
          "b",
          ChatState(List(HumanMessage(10), HumanMessage(11), HumanMessage(22)))
        )
      )
    }

    "distinguishes event type and payload" in {
      NodeStarted("node") should not equal NodeCompleted("node")
      CheckpointCreated("node", 1) should not equal CheckpointCreated("node", 2)
    }

  }

}
