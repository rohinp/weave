package com.weave.core

public class GraphRunner<S, U> internal constructor(private val graph: Graph<S, U>) {
    public fun run(
        initialState: S,
        onEvent: (GraphEvent) -> Unit = {},
    ): RunResult<S> {
        var nodeName = requireNotNull(graph.start)
        var state = initialState

        while (true) {
            val node = requireNotNull(graph.nodes[nodeName])
            val execution = executeNode(node, state, onEvent)
            state = execution.getOrElse { cause ->
                return fail(ExecutionError.RuntimeError(nodeName, cause), onEvent)
            }

            if (nodeName == graph.end) {
                onEvent(GraphEvent.WorkflowCompleted(WORKFLOW_NAME))
                return RunResult.Success(state)
            }

            val children = graph.nextNodes(nodeName, state)
            when (children.size) {
                0 -> return fail(ExecutionError.EndNodeNotReached(requireNotNull(graph.end)), onEvent)
                1 -> nodeName = children.single()
                else -> return fail(ExecutionError.BranchingNotSupported(nodeName), onEvent)
            }
        }
    }

    private fun executeNode(
        node: Node<S, U>,
        state: S,
        onEvent: (GraphEvent) -> Unit,
    ): Result<S> {
        val maxAttempts =
            when (val policy = node.retryPolicy) {
                RetryPolicy.Never -> 1
                is RetryPolicy.FixedAttempts -> policy.maxAttempts
            }

        repeat(maxAttempts) {
            onEvent(GraphEvent.NodeStarted(node.name))
            val attempt = runCatching { graph.reducer.reduce(state, node.action(state)) }

            if (attempt.isSuccess) {
                val nextState = attempt.getOrThrow()
                onEvent(GraphEvent.NodeCompleted(node.name))
                onEvent(GraphEvent.CheckpointCreated(node.name, nextState))
                return Result.success(nextState)
            }

            val cause = attempt.exceptionOrNull() ?: error("Failed attempt has no cause")
            onEvent(GraphEvent.NodeFailed(node.name, cause))
            if (it == maxAttempts - 1) return Result.failure(cause)
        }

        error("Retry policy produced no attempts")
    }

    private fun fail(
        error: ExecutionError,
        onEvent: (GraphEvent) -> Unit,
    ): RunResult.Failure {
        onEvent(GraphEvent.WorkflowFailed(WORKFLOW_NAME, error.cause))
        return RunResult.Failure(error)
    }

    private companion object {
        private const val WORKFLOW_NAME: String = "workflow"
    }
}
