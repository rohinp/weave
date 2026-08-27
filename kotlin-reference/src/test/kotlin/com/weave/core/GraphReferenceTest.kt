package com.weave.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame

class GraphReferenceTest {
    private data class State(val values: List<Int>)

    private data class Append(val value: Int)

    private val reducer =
        object : Reducer<State, Append> {
            override fun reduce(state: State, update: Append): State =
                state.copy(values = state.values + update.value)

            override fun merge(left: State, right: State): State =
                State(left.values + right.values)
        }

    @Test
    fun `runs a validated linear graph from start to end`() {
        val events = mutableListOf<GraphEvent>()
        val validation =
            Graph.create(reducer)
                .addNode(Node("increment") { state -> Append(state.values.last() + 1) })
                .addNode(Node("double") { state -> Append(state.values.last() * 2) })
                .setStart("increment")
                .setEnd("double")
                .addEdge(Edge("increment", "double"))
                .validate()

        val runner = assertIs<ValidationResult.Valid<State, Append>>(validation).runner
        val result = assertIs<RunResult.Success<State>>(runner.run(State(listOf(10)), events::add))

        assertEquals(State(listOf(10, 11, 22)), result.state)
        assertEquals(
            listOf(
                GraphEvent.NodeStarted("increment"),
                GraphEvent.NodeCompleted("increment"),
                GraphEvent.CheckpointCreated("increment", State(listOf(10, 11))),
                GraphEvent.NodeStarted("double"),
                GraphEvent.NodeCompleted("double"),
                GraphEvent.CheckpointCreated("double", State(listOf(10, 11, 22))),
                GraphEvent.WorkflowCompleted("workflow"),
            ),
            events,
        )
    }

    @Test
    fun `returns a successful result when start and end are the same node`() {
        val events = mutableListOf<GraphEvent>()
        val validation =
            Graph.create(reducer)
                .addNode(Node("only") { Append(11) })
                .setStart("only")
                .setEnd("only")
                .validate()

        val runner = assertIs<ValidationResult.Valid<State, Append>>(validation).runner
        val result = assertIs<RunResult.Success<State>>(runner.run(State(listOf(10)), events::add))

        assertEquals(State(listOf(10, 11)), result.state)
        assertEquals(
            listOf(
                GraphEvent.NodeStarted("only"),
                GraphEvent.NodeCompleted("only"),
                GraphEvent.CheckpointCreated("only", State(listOf(10, 11))),
                GraphEvent.WorkflowCompleted("workflow"),
            ),
            events,
        )
    }

    @Test
    fun `routes using state produced by the reducer`() {
        val events = mutableListOf<GraphEvent>()
        val validation =
            Graph.create(reducer)
                .addNode(Node("start") { Append(10) })
                .addNode(Node("small") { Append(20) })
                .addNode(Node("large") { Append(100) })
                .setStart("start")
                .setEnd("large")
                .addEdge(Edge("start", "small") { state -> state.values.last() < 10 })
                .addEdge(Edge("start", "large") { state -> state.values.last() >= 10 })
                .validate()

        val runner = assertIs<ValidationResult.Valid<State, Append>>(validation).runner
        val result = assertIs<RunResult.Success<State>>(runner.run(State(listOf(5)), events::add))

        assertEquals(State(listOf(5, 10, 100)), result.state)
        assertEquals(
            listOf("start", "large"),
            events.filterIsInstance<GraphEvent.NodeStarted>().map { it.name },
        )
    }

    @Test
    fun `returns a typed validation error`() {
        val result = Graph.create(reducer).validate()

        assertEquals(
            ValidationResult.Invalid<State, Append>(ValidationError.EmptyGraph),
            result,
        )
    }

    @Test
    fun `returns node failures instead of throwing`() {
        val failure = IllegalStateException("boom")
        val events = mutableListOf<GraphEvent>()
        val validation =
            Graph.create(reducer)
                .addNode(Node("explode") { _: State -> throw failure })
                .setStart("explode")
                .setEnd("explode")
                .validate()

        val runner = assertIs<ValidationResult.Valid<State, Append>>(validation).runner
        val result = assertIs<RunResult.Failure>(runner.run(State(emptyList()), events::add))
        val error = assertIs<ExecutionError.RuntimeError>(result.error)

        assertEquals("explode", error.nodeName)
        assertSame(failure, error.cause)
        assertEquals(
            listOf(
                GraphEvent.NodeStarted("explode"),
                GraphEvent.NodeFailed("explode", failure),
                GraphEvent.WorkflowFailed("workflow", failure),
            ),
            events,
        )
    }

    @Test
    fun `preserves successful events before a middle node fails`() {
        val failure = IllegalStateException("middle failed")
        val events = mutableListOf<GraphEvent>()
        val validation =
            Graph.create(reducer)
                .addNode(Node("start") { Append(1) })
                .addNode(Node("explode") { _: State -> throw failure })
                .addNode(Node("end") { Append(3) })
                .setStart("start")
                .setEnd("end")
                .addEdge(Edge("start", "explode"))
                .addEdge(Edge("explode", "end"))
                .validate()

        val runner = assertIs<ValidationResult.Valid<State, Append>>(validation).runner
        val result = assertIs<RunResult.Failure>(runner.run(State(emptyList()), events::add))

        assertEquals(ExecutionError.RuntimeError("explode", failure), result.error)
        assertEquals(
            listOf(
                GraphEvent.NodeStarted("start"),
                GraphEvent.NodeCompleted("start"),
                GraphEvent.CheckpointCreated("start", State(listOf(1))),
                GraphEvent.NodeStarted("explode"),
                GraphEvent.NodeFailed("explode", failure),
                GraphEvent.WorkflowFailed("workflow", failure),
            ),
            events,
        )
    }

    @Test
    fun `processes fan out in FIFO edge order`() {
        val events = mutableListOf<GraphEvent>()
        val validation =
            Graph.create(reducer)
                .addNode(Node("start") { Append(1) })
                .addNode(Node("left") { Append(2) })
                .addNode(Node("right") { Append(3) })
                .setStart("start")
                .setEnd("left")
                .addEdge(Edge("start", "left"))
                .addEdge(Edge("start", "right"))
                .validate()

        val runner = assertIs<ValidationResult.Valid<State, Append>>(validation).runner
        val result = assertIs<RunResult.Success<State>>(runner.run(State(emptyList()), events::add))

        assertEquals(State(listOf(1, 2)), result.state)
        assertEquals(
            listOf("start", "left", "right"),
            events.filterIsInstance<GraphEvent.NodeStarted>().map { it.name },
        )
    }

    @Test
    fun `retries until a node succeeds`() {
        var attempts = 0
        val failure = IllegalStateException("not yet")
        val events = mutableListOf<GraphEvent>()
        val validation =
            Graph.create(reducer)
                .addNode(
                    Node(
                        name = "flaky",
                        action = {
                            attempts += 1
                            if (attempts < 3) throw failure
                            Append(1)
                        },
                        retryPolicy = RetryPolicy.FixedAttempts(4),
                    ),
                )
                .setStart("flaky")
                .setEnd("flaky")
                .validate()

        val runner = assertIs<ValidationResult.Valid<State, Append>>(validation).runner
        val result = assertIs<RunResult.Success<State>>(runner.run(State(emptyList()), events::add))

        assertEquals(State(listOf(1)), result.state)
        assertEquals(3, attempts)
        assertEquals(3, events.count { it == GraphEvent.NodeStarted("flaky") })
        assertEquals(2, events.count { it == GraphEvent.NodeFailed("flaky", failure) })
        assertEquals(1, events.count { it == GraphEvent.WorkflowCompleted("workflow") })
    }

    @Test
    fun `reports one workflow failure after retries are exhausted`() {
        val failure = IllegalStateException("always fails")
        val events = mutableListOf<GraphEvent>()
        val validation =
            Graph.create(reducer)
                .addNode(
                    Node(
                        name = "flaky",
                        retryPolicy = RetryPolicy.FixedAttempts(3),
                        action = { throw failure },
                    ),
                )
                .setStart("flaky")
                .setEnd("flaky")
                .validate()

        val runner = assertIs<ValidationResult.Valid<State, Append>>(validation).runner
        val result = assertIs<RunResult.Failure>(runner.run(State(emptyList()), events::add))

        assertEquals(ExecutionError.RuntimeError("flaky", failure), result.error)
        assertEquals(3, events.count { it == GraphEvent.NodeStarted("flaky") })
        assertEquals(3, events.count { it == GraphEvent.NodeFailed("flaky", failure) })
        assertEquals(1, events.count { it == GraphEvent.WorkflowFailed("workflow", failure) })
        assertEquals(0, events.count { it is GraphEvent.WorkflowCompleted })
    }

    @Test
    fun `never retry policy attempts a failing node exactly once`() {
        var attempts = 0
        val failure = IllegalStateException("boom")
        val events = mutableListOf<GraphEvent>()
        val validation =
            Graph.create(reducer)
                .addNode(
                    Node("never") {
                        attempts += 1
                        throw failure
                    },
                )
                .setStart("never")
                .setEnd("never")
                .validate()

        val runner = assertIs<ValidationResult.Valid<State, Append>>(validation).runner
        val result = assertIs<RunResult.Failure>(runner.run(State(emptyList()), events::add))

        assertEquals(ExecutionError.RuntimeError("never", failure), result.error)
        assertEquals(1, attempts)
        assertEquals(1, events.count { it == GraphEvent.NodeStarted("never") })
        assertEquals(1, events.count { it == GraphEvent.NodeFailed("never", failure) })
        assertEquals(1, events.count { it == GraphEvent.WorkflowFailed("workflow", failure) })
    }
}
