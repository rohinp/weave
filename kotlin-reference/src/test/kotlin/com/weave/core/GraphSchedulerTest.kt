package com.weave.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class GraphSchedulerTest {
    private data class State(val values: List<String>)

    private data class Append(val value: String)

    private val reducer =
        object : Reducer<State, Append> {
            override fun reduce(state: State, update: Append): State =
                state.copy(values = state.values + update.value)

            override fun merge(left: State, right: State): State =
                State(left.values + right.values)
        }

    @Test
    fun `branches receive independent copies of their parent state`() {
        var leftInput: State? = null
        var rightInput: State? = null
        val graph =
            Graph.create(reducer)
                .addNode(Node("start") { Append("start") })
                .addNode(
                    Node("left") { state ->
                        leftInput = state
                        Append("left")
                    },
                )
                .addNode(
                    Node("right") { state ->
                        rightInput = state
                        Append("right")
                    },
                )
                .setStart("start")
                .setEnd("left")
                .addEdge(Edge("start", "left"))
                .addEdge(Edge("start", "right"))

        val result = runner(graph).run(State(emptyList()))

        assertIs<RunResult.Success<State>>(result)
        assertEquals(State(listOf("start")), leftInput)
        assertEquals(State(listOf("start")), rightInput)
        assertEquals(State(listOf("start", "left")), result.state)
    }

    @Test
    fun `ready work executes breadth first`() {
        val events = mutableListOf<GraphEvent>()
        val graph =
            Graph.create(reducer)
                .addNode(Node("start") { Append("start") })
                .addNode(Node("left") { Append("left") })
                .addNode(Node("right") { Append("right") })
                .addNode(Node("left-child") { Append("left-child") })
                .addNode(Node("right-child") { Append("right-child") })
                .setStart("start")
                .setEnd("right-child")
                .addEdge(Edge("start", "left"))
                .addEdge(Edge("start", "right"))
                .addEdge(Edge("left", "left-child"))
                .addEdge(Edge("right", "right-child"))

        runner(graph).run(State(emptyList()), events::add)

        assertEquals(
            listOf("start", "left", "right", "left-child", "right-child"),
            events.filterIsInstance<GraphEvent.NodeStarted>().map { it.name },
        )
    }

    @Test
    fun `strict join runs once after every parent and receives deterministic merged state`() {
        var mergeInput: State? = null
        val events = mutableListOf<GraphEvent>()
        val graph =
            Graph.create(reducer)
                .addNode(Node("start") { Append("start") })
                .addNode(Node("docs") { Append("docs") })
                .addNode(Node("web") { Append("web") })
                .addNode(
                    Node("merge") { state ->
                        mergeInput = state
                        Append("merged")
                    },
                )
                .setStart("start")
                .setEnd("merge")
                .addEdge(Edge("start", "docs"))
                .addEdge(Edge("start", "web"))
                .addEdge(Edge("docs", "merge"))
                .addEdge(Edge("web", "merge"))

        val result = assertIs<RunResult.Success<State>>(runner(graph).run(State(emptyList()), events::add))

        assertEquals(State(listOf("start", "docs", "start", "web")), mergeInput)
        assertEquals(State(listOf("start", "docs", "start", "web", "merged")), result.state)
        assertEquals(1, events.count { it == GraphEvent.NodeStarted("merge") })
        assertEquals(1, events.count { it == GraphEvent.WorkflowCompleted("workflow") })

        val mergeStarted = events.indexOf(GraphEvent.NodeStarted("merge"))
        assertEquals(true, mergeStarted > events.indexOf(GraphEvent.NodeCompleted("docs")))
        assertEquals(true, mergeStarted > events.indexOf(GraphEvent.NodeCompleted("web")))
    }

    @Test
    fun `released join schedules downstream work`() {
        val events = mutableListOf<GraphEvent>()
        val graph =
            Graph.create(reducer)
                .addNode(Node("start") { Append("start") })
                .addNode(Node("docs") { Append("docs") })
                .addNode(Node("web") { Append("web") })
                .addNode(Node("merge") { Append("merged") })
                .addNode(Node("final") { Append("final") })
                .setStart("start")
                .setEnd("final")
                .addEdge(Edge("start", "docs"))
                .addEdge(Edge("start", "web"))
                .addEdge(Edge("docs", "merge"))
                .addEdge(Edge("web", "merge"))
                .addEdge(Edge("merge", "final"))

        val result = assertIs<RunResult.Success<State>>(runner(graph).run(State(emptyList()), events::add))

        assertEquals(
            State(listOf("start", "docs", "start", "web", "merged", "final")),
            result.state,
        )
        assertEquals(
            listOf("start", "docs", "web", "merge", "final"),
            events.filterIsInstance<GraphEvent.NodeStarted>().map { it.name },
        )
        assertEquals(1, events.count { it == GraphEvent.WorkflowCompleted("workflow") })
    }

    @Test
    fun `strict join supports three ordered parents`() {
        var mergeInput: State? = null
        val graph =
            Graph.create(reducer)
                .addNode(Node("start") { Append("start") })
                .addNode(Node("a") { Append("a") })
                .addNode(Node("b") { Append("b") })
                .addNode(Node("c") { Append("c") })
                .addNode(
                    Node("merge") { state ->
                        mergeInput = state
                        Append("merged")
                    },
                )
                .setStart("start")
                .setEnd("merge")
                .addEdge(Edge("start", "a"))
                .addEdge(Edge("start", "b"))
                .addEdge(Edge("start", "c"))
                .addEdge(Edge("a", "merge"))
                .addEdge(Edge("b", "merge"))
                .addEdge(Edge("c", "merge"))

        runner(graph).run(State(emptyList()))

        assertEquals(
            State(listOf("start", "a", "start", "b", "start", "c")),
            mergeInput,
        )
    }

    @Test
    fun `multiple independent joins are each released once`() {
        val events = mutableListOf<GraphEvent>()
        val graph =
            Graph.create(reducer)
                .addNode(Node("start") { Append("start") })
                .addNode(Node("a1") { Append("a1") })
                .addNode(Node("a2") { Append("a2") })
                .addNode(Node("b1") { Append("b1") })
                .addNode(Node("b2") { Append("b2") })
                .addNode(Node("join-a") { Append("join-a") })
                .addNode(Node("join-b") { Append("join-b") })
                .setStart("start")
                .setEnd("join-b")
                .addEdge(Edge("start", "a1"))
                .addEdge(Edge("start", "a2"))
                .addEdge(Edge("start", "b1"))
                .addEdge(Edge("start", "b2"))
                .addEdge(Edge("a1", "join-a"))
                .addEdge(Edge("a2", "join-a"))
                .addEdge(Edge("b1", "join-b"))
                .addEdge(Edge("b2", "join-b"))

        val result = runner(graph).run(State(emptyList()), events::add)

        assertIs<RunResult.Success<State>>(result)
        assertEquals(1, events.count { it == GraphEvent.NodeStarted("join-a") })
        assertEquals(1, events.count { it == GraphEvent.NodeStarted("join-b") })
    }

    @Test
    fun `incomplete strict join returns a typed deadlock`() {
        val events = mutableListOf<GraphEvent>()
        val graph =
            Graph.create(reducer)
                .addNode(Node("start") { Append("start") })
                .addNode(Node("active") { Append("active") })
                .addNode(Node("inactive") { Append("inactive") })
                .addNode(Node("merge") { Append("merged") })
                .setStart("start")
                .setEnd("merge")
                .addEdge(Edge("start", "active"))
                .addEdge(Edge("start", "inactive") { false })
                .addEdge(Edge("active", "merge"))
                .addEdge(Edge("inactive", "merge"))

        val result = assertIs<RunResult.Failure>(runner(graph).run(State(emptyList()), events::add))

        assertEquals(ExecutionError.JoinDeadlock(setOf("merge")), result.error)
        assertEquals(1, events.count { it is GraphEvent.WorkflowFailed })
        assertEquals(0, events.count { it is GraphEvent.WorkflowCompleted })
    }

    @Test
    fun `terminal node skips its children while other ready work drains`() {
        val events = mutableListOf<GraphEvent>()
        val graph =
            Graph.create(reducer)
                .addNode(Node("start") { Append("start") })
                .addNode(Node("end") { Append("end") })
                .addNode(Node("other") { Append("other") })
                .addNode(Node("after-end") { Append("after-end") })
                .setStart("start")
                .setEnd("end")
                .addEdge(Edge("start", "end"))
                .addEdge(Edge("start", "other"))
                .addEdge(Edge("end", "after-end"))

        val result = assertIs<RunResult.Success<State>>(runner(graph).run(State(emptyList()), events::add))

        assertEquals(State(listOf("start", "end")), result.state)
        assertEquals(
            listOf("start", "end", "other"),
            events.filterIsInstance<GraphEvent.NodeStarted>().map { it.name },
        )
    }

    @Test
    fun `duplicate arrival from one parent cannot complete a strict join`() {
        val runtimeState = RuntimeState<String>()
        runtimeState.addJoinArrivals(
            listOf(
                PendingJoinInput("merge", "left", "first"),
                PendingJoinInput("merge", "left", "replacement"),
            ),
        )

        assertEquals(emptyList(), runtimeState.finishedJoins { setOf("left", "right") })
        assertEquals(setOf("left"), runtimeState.pendingJoins.getValue("merge").keys)
        assertEquals("replacement", runtimeState.pendingJoins.getValue("merge").getValue("left"))
    }

    private fun runner(graph: Graph<State, Append>): GraphRunner<State, Append> =
        assertIs<ValidationResult.Valid<State, Append>>(graph.validate()).runner
}
