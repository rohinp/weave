package com.weave.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

class PublicModelTest {
    @Test
    fun `events use structural type and payload equality`() {
        assertNotEquals<GraphEvent>(GraphEvent.NodeStarted("node"), GraphEvent.NodeCompleted("node"))
        assertNotEquals<GraphEvent>(
            GraphEvent.CheckpointCreated("node", 1),
            GraphEvent.CheckpointCreated("node", 2),
        )
    }

    @Test
    fun `fixed attempts must allow at least one attempt`() {
        val error = assertFailsWith<IllegalArgumentException> { RetryPolicy.FixedAttempts(0) }

        assertEquals("maxAttempts must be greater than zero", error.message)
    }

    @Test
    fun `validation errors expose stable messages`() {
        assertEquals("Graph is empty", ValidationError.EmptyGraph.message)
        assertEquals("Start node not defined", ValidationError.StartNodeNotDefined.message)
        assertEquals("End node not defined", ValidationError.EndNodeNotDefined.message)
        assertEquals("Node 'missing' not found in graph", ValidationError.NodeNotFound("missing").message)
    }

    @Test
    fun `graph exposes conditional children and unique ordered parents`() {
        val reducer =
            object : Reducer<Int, Int> {
                override fun reduce(state: Int, update: Int): Int = state + update

                override fun merge(left: Int, right: Int): Int = left + right
            }
        val graph =
            Graph.create(reducer)
                .addNode(Node("start") { 1 })
                .addNode(Node("left") { 1 })
                .addNode(Node("right") { 1 })
                .addNode(Node("join") { 1 })
                .addEdge(Edge("start", "left") { state -> state > 0 })
                .addEdge(Edge("start", "right") { state -> state < 0 })
                .addEdge(Edge("left", "join"))
                .addEdge(Edge("right", "join"))
                .addEdge(Edge("left", "join"))

        assertEquals(listOf("left"), graph.nextNodes("start", 1))
        assertEquals(listOf("left", "right"), graph.parentNodes("join"))
        assertEquals(true, graph.isMultipleParentNode("join"))
    }
}
