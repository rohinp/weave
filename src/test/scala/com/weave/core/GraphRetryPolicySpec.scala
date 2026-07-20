package com.weave.core

import com.weave.core.GraphEvent.*
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import TestData.*

class GraphRetryPolicySpec extends AnyWordSpecLike with Matchers {

  "Graph Retry policies" must {
    "do not retries a failing node" in {
      val events = collection.mutable.ListBuffer.empty[GraphEvent]

      val flakyNode =
        createNode(
          "flaky",
          state => {
              throw RuntimeException("boom")
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

      events.toList.toString shouldBe List(
        NodeStarted("flaky"),
        GraphEvent.NodeFailed(
          "flaky",
          RuntimeException("boom")
        ),
        WorkflowFailed("workflow",RuntimeException("boom"))
      ).toString
    }
    "retries a failing node" in {
      var attempts = 0
      val events = collection.mutable.ListBuffer.empty[GraphEvent]

      val flakyNode =
        createNode[Int](
          "flaky",
          state => {
            attempts += 1

            if (attempts < 3)
              throw RuntimeException("boom")

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
          RuntimeException("boom")
        ),
        NodeStarted("flaky"),
        GraphEvent.NodeFailed(
          "flaky",
          RuntimeException("boom")
        ),
        NodeStarted("flaky"),
        NodeCompleted("flaky"),
        CheckpointCreated("flaky", ChatState(List(HumanMessage(10), HumanMessage(11)))),
        WorkflowCompleted("workflow")
      )
    }

    "do not retry once succeed" in {
      var attempts = 0
      val events = collection.mutable.ListBuffer.empty[GraphEvent]

      val flakyNode =
        createNode[Int](
          "flaky",
          state => {
            attempts += 1

            if (attempts < 2)
              throw RuntimeException("boom")

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
          RuntimeException("boom")
        ),
        NodeStarted("flaky"),
        NodeCompleted("flaky"),
        CheckpointCreated("flaky", createState(11)),
        WorkflowCompleted("workflow")
      )
    }

  }

}
