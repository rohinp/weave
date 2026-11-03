package com.weave.core

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

import scala.collection.immutable.Queue

class RuntimeStateSpec extends AnyWordSpecLike with Matchers {

  "RuntimeState" should {
    "duplicate arrival from one parent does not satisfy a two-parent join" in {
      val initial =
        RuntimeState[String](
          Queue.empty,
          Map.empty
        )

      val runtime =
        initial
          .addMultipleJoinArrival(
            List(
              PendingJoinInput("merge", "branch1", "state-1"),
              PendingJoinInput("merge", "branch1", "state-2")
            )
          )

      val finished =
        RuntimeState.finishedJoins(
          runtime,
          _ => Set("branch1", "branch2")
        )

      finished shouldBe empty

      // Note: A later arrival from the same parent replaces the earlier state.
      runtime.pendingJoins("merge").keySet shouldBe Set("branch1")
      runtime.pendingJoins("merge")("branch1") shouldBe "state-2"
    }
  }
}
