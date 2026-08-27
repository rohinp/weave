package com.weave.core

@ConsistentCopyVisibility
public data class Graph<S, U> private constructor(
    public val reducer: Reducer<S, U>,
    public val nodes: Map<String, Node<S, U>>,
    public val edges: List<Edge<S>>,
    public val start: String?,
    public val end: String?,
) {
    public fun addNode(node: Node<S, U>): Graph<S, U> =
        copy(nodes = nodes + (node.name to node))

    public fun addEdge(edge: Edge<S>): Graph<S, U> = copy(edges = edges + edge)

    public fun setStart(nodeName: String): Graph<S, U> = copy(start = nodeName)

    public fun setEnd(nodeName: String): Graph<S, U> = copy(end = nodeName)

    public fun validate(): ValidationResult<S, U> {
        if (nodes.isEmpty()) return ValidationResult.Invalid(ValidationError.EmptyGraph)

        val startNode = start ?: return ValidationResult.Invalid(ValidationError.StartNodeNotDefined)
        if (startNode !in nodes) {
            return ValidationResult.Invalid(ValidationError.NodeNotFound(startNode))
        }

        val endNode = end ?: return ValidationResult.Invalid(ValidationError.EndNodeNotDefined)
        if (endNode !in nodes) {
            return ValidationResult.Invalid(ValidationError.NodeNotFound(endNode))
        }

        edges.firstOrNull { it.from !in nodes || it.to !in nodes }?.let { edge ->
            val missingNode = if (edge.from !in nodes) edge.from else edge.to
            return ValidationResult.Invalid(ValidationError.NodeNotFound(missingNode))
        }

        return ValidationResult.Valid(GraphRunner(this))
    }

    public fun nextNodes(
        nodeName: String,
        state: S,
    ): List<String> = edges.filter { it.from == nodeName && it.condition(state) }.map { it.to }

    public fun parentNodes(nodeName: String): List<String> =
        edges.filter { it.to == nodeName }.map { it.from }.distinct()

    public fun isMultipleParentNode(nodeName: String): Boolean = parentNodes(nodeName).size > 1

    public companion object {
        public fun <S, U> create(reducer: Reducer<S, U>): Graph<S, U> =
            Graph(
                reducer = reducer,
                nodes = emptyMap(),
                edges = emptyList(),
                start = null,
                end = null,
            )
    }
}
