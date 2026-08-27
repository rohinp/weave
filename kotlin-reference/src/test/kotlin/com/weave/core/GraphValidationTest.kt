package com.weave.core

import kotlin.test.Test
import kotlin.test.assertEquals

class GraphValidationTest {
    private val reducer =
        object : Reducer<Int, Int> {
            override fun reduce(state: Int, update: Int): Int = state + update

            override fun merge(left: Int, right: Int): Int = left + right
        }

    @Test
    fun `requires a start node`() {
        val result = Graph.create(reducer).addNode(Node("node") { 1 }).validate()

        assertEquals(
            ValidationResult.Invalid<Int, Int>(ValidationError.StartNodeNotDefined),
            result,
        )
    }

    @Test
    fun `requires an existing start node`() {
        val result =
            Graph.create(reducer)
                .addNode(Node("node") { 1 })
                .setStart("missing")
                .validate()

        assertEquals(
            ValidationResult.Invalid<Int, Int>(ValidationError.NodeNotFound("missing")),
            result,
        )
    }

    @Test
    fun `requires an end node`() {
        val result =
            Graph.create(reducer)
                .addNode(Node("node") { 1 })
                .setStart("node")
                .validate()

        assertEquals(
            ValidationResult.Invalid<Int, Int>(ValidationError.EndNodeNotDefined),
            result,
        )
    }

    @Test
    fun `requires an existing end node`() {
        val result =
            Graph.create(reducer)
                .addNode(Node("node") { 1 })
                .setStart("node")
                .setEnd("missing")
                .validate()

        assertEquals(
            ValidationResult.Invalid<Int, Int>(ValidationError.NodeNotFound("missing")),
            result,
        )
    }

    @Test
    fun `requires existing edge endpoints`() {
        val result =
            Graph.create(reducer)
                .addNode(Node("node") { 1 })
                .setStart("node")
                .setEnd("node")
                .addEdge(Edge("node", "missing"))
                .validate()

        assertEquals(
            ValidationResult.Invalid<Int, Int>(ValidationError.NodeNotFound("missing")),
            result,
        )
    }

    @Test
    fun `requires an existing edge source`() {
        val result =
            Graph.create(reducer)
                .addNode(Node("node") { 1 })
                .setStart("node")
                .setEnd("node")
                .addEdge(Edge("missing", "node"))
                .validate()

        assertEquals(
            ValidationResult.Invalid<Int, Int>(ValidationError.NodeNotFound("missing")),
            result,
        )
    }
}
