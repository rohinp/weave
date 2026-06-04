package com.weave.core

import com.weave.core.GraphEvent.*
import org.scalatest.EitherValues
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

class GraphRetryPolicySpec extends AnyWordSpecLike with Matchers with EitherValues {

  "Graph Retry policies" must {
    "doe not retries a failing node" in {
      val events = collection.mutable.ListBuffer.empty[GraphEvent]

      val flakyNode =
        Node[Int](
          "flaky",
          state => {
              throw RuntimeException("boom")
          }
        )
        
      val graph = Graph[Int]()
        .addNode(flakyNode)
        .setStart("flaky")
        .setEnd("flaky")
        .validate()
        .value

      graph.run(
        initialState = 10,
        onEvent = events += _
      )

      events.toList.toString shouldBe List(
        NodeStarted("flaky"),
        GraphEvent.NodeFailed(
          "flaky",
          RuntimeException("boom")
        ),
        WorkflowFailed(RuntimeException("boom"))
      ).toString
    }
    "retries a failing node" in {
      var attempts = 0
      val events = collection.mutable.ListBuffer.empty[GraphEvent]

      val flakyNode =
        Node[Int](
          "flaky",
          state => {
            attempts += 1

            if (attempts < 3)
              throw RuntimeException("boom")

            state + 1
          },
          RetryPolicy.FixedAttempts(3)
        )

      val graph = Graph[Int]()
        .addNode(flakyNode)
        .setStart("flaky")
        .setEnd("flaky")
        .validate()
        .value

      graph.run(
        initialState = 10,
        onEvent = events += _
      )

      events.toList.toString shouldBe List(
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
        WorkflowCompleted()
      ).toString
    }

  }

}
