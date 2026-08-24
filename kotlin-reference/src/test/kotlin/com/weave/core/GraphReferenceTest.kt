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
        val validation =
            Graph.create(reducer)
                .addNode(Node("increment") { state -> Append(state.values.last() + 1) })
                .addNode(Node("double") { state -> Append(state.values.last() * 2) })
                .setStart("increment")
                .setEnd("double")
                .addEdge(Edge("increment", "double"))
                .validate()

        val runner = assertIs<ValidationResult.Valid<State, Append>>(validation).runner
        val result = assertIs<RunResult.Success<State>>(runner.run(State(listOf(10))))

        assertEquals(State(listOf(10, 11, 22)), result.state)
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
        val validation =
            Graph.create(reducer)
                .addNode(Node("explode") { _: State -> throw failure })
                .setStart("explode")
                .setEnd("explode")
                .validate()

        val runner = assertIs<ValidationResult.Valid<State, Append>>(validation).runner
        val result = assertIs<RunResult.Failure>(runner.run(State(emptyList())))
        val error = assertIs<ExecutionError.NodeFailed>(result.error)

        assertEquals("explode", error.nodeName)
        assertSame(failure, error.cause)
    }

    @Test
    fun `reports the scheduler boundary when a node fans out`() {
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
        val result = assertIs<RunResult.Failure>(runner.run(State(emptyList())))

        assertEquals(ExecutionError.BranchingNotSupported("start"), result.error)
    }
}
