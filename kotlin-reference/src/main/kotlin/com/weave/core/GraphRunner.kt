package com.weave.core

public class GraphRunner<S, U> internal constructor(private val graph: Graph<S, U>) {
    public fun run(initialState: S): RunResult<S> {
        var nodeName = requireNotNull(graph.start)
        var state = initialState

        while (true) {
            val node = requireNotNull(graph.nodes[nodeName])
            state =
                try {
                    graph.reducer.reduce(state, node.action(state))
                } catch (cause: Throwable) {
                    return RunResult.Failure(ExecutionError.NodeFailed(nodeName, cause))
                }

            if (nodeName == graph.end) return RunResult.Success(state)

            val children = graph.matchingChildren(nodeName, state)
            when (children.size) {
                0 -> return RunResult.Failure(ExecutionError.EndNodeNotReached(requireNotNull(graph.end)))
                1 -> nodeName = children.single()
                else -> return RunResult.Failure(ExecutionError.BranchingNotSupported(nodeName))
            }
        }
    }
}
