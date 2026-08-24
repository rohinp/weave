package com.weave.core

import com.weave.core.GraphEvent.*
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import TestData.*

class GraphRetryPolicySpec extends AnyWordSpecLike with Matchers {

  "Graph Retry policies" must {
    "do not retries a failing node" in {
      val events = collection.mutable.ListBuffer.empty[GraphEvent]
      val failure = RuntimeException("boom")

      val flakyNode =
        createNode(
          "flaky",
          state => {
            throw failure
          }
        )

      val graph = testGraph
        .addNode(flakyNode)
        .setStart("flaky")
        .setEnd("flaky")
        .validate()
        .runner

      graph.run(
        initialState = createState(10),
        onEvent = events += _
      )

      events.toList shouldBe List(
        NodeStarted("flaky"),
        GraphEvent.NodeFailed("flaky", failure),
        WorkflowFailed("workflow", failure)
      )
    }
    "retries a failing node" in {
      var attempts = 0
      val events = collection.mutable.ListBuffer.empty[GraphEvent]
      val failure = RuntimeException("boom")

      val flakyNode =
        createNode[Int](
          "flaky",
          state => {
            attempts += 1

            if (attempts < 3)
              throw failure

            state + 1
          },
          RetryPolicy.FixedAttempts(3)
        )

      val graph = testGraph
        .addNode(flakyNode)
        .setStart("flaky")
        .setEnd("flaky")
        .validate()
        .runner

      graph.run(
        initialState = createState(10),
        onEvent = events += _
      )

      events.toList shouldBe List(
        NodeStarted("flaky"),
        GraphEvent.NodeFailed(
          "flaky",
          failure
        ),
        NodeStarted("flaky"),
        GraphEvent.NodeFailed(
          "flaky",
          failure
        ),
        NodeStarted("flaky"),
        NodeCompleted("flaky"),
        CheckpointCreated(
          "flaky",
          ChatState(List(HumanMessage(10), HumanMessage(11)))
        ),
        WorkflowCompleted("workflow")
      )
    }

    "do not retry once succeed" in {
      var attempts = 0
      val events = collection.mutable.ListBuffer.empty[GraphEvent]
      val failure = RuntimeException("boom")

      val flakyNode =
        createNode[Int](
          "flaky",
          state => {
            attempts += 1

            if (attempts < 2)
              throw failure

            state + 1
          },
          RetryPolicy.FixedAttempts(4)
        )

      val graph = testGraph
        .addNode(flakyNode)
        .setStart("flaky")
        .setEnd("flaky")
        .validate()
        .runner

      graph.run(
        initialState = createState(10),
        onEvent = events += _
      )

      events.toList shouldBe List(
        NodeStarted("flaky"),
        GraphEvent.NodeFailed(
          "flaky",
          failure
        ),
        NodeStarted("flaky"),
        NodeCompleted("flaky"),
        CheckpointCreated(
          "flaky",
          ChatState(List(HumanMessage(10), HumanMessage(11)))
        ),
        WorkflowCompleted("workflow")
      )
    }

    "emits one failure event per exhausted attempt" in {
      val events = collection.mutable.ListBuffer.empty[GraphEvent]
      val failure = RuntimeException("boom")

      val graph = testGraph
        .addNode(
          createNode[Int](
            "flaky",
            _ => throw failure,
            RetryPolicy.FixedAttempts(3)
          )
        )
        .setStart("flaky")
        .setEnd("flaky")
        .validate()
        .runner

      graph.run(createState(10), events += _)

      events.count(_ == NodeStarted("flaky")) shouldBe 3
      events.count(_ == NodeFailed("flaky", failure)) shouldBe 3
      events.count(_ == WorkflowFailed("workflow", failure)) shouldBe 1
      events.count {
        case WorkflowCompleted(_) => true
        case _                    => false
      } shouldBe 0
    }

  }

}
