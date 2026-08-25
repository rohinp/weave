package com.weave.core

public class GraphRunner<S, U> internal constructor(private val graph: Graph<S, U>) {
    public fun run(
        initialState: S,
        onEvent: (GraphEvent) -> Unit = {},
    ): RunResult<S> {
        val endNode = requireNotNull(graph.end)
        val runtimeState =
            RuntimeState(
                workQueue = ArrayDeque(listOf(WorkItem(requireNotNull(graph.start), initialState))),
            )
        val completedStates = mutableMapOf<String, S>()

        while (true) {
            val workItem = runtimeState.dequeue()
            if (workItem != null) {
                val node = requireNotNull(graph.nodes[workItem.nodeName])
                val execution = executeNode(node, workItem.state, onEvent)
                val nextState = execution.getOrElse { cause ->
                    return fail(ExecutionError.RuntimeError(workItem.nodeName, cause), onEvent)
                }

                completedStates[workItem.nodeName] = nextState

                val children =
                    if (workItem.nodeName == endNode) {
                        emptyList()
                    } else {
                        graph.nextNodes(workItem.nodeName, nextState)
                    }
                val (joinNodes, readyNodes) = children.partition(graph::isMultipleParentNode)

                runtimeState.enqueue(readyNodes.map { nodeName -> WorkItem(nodeName, nextState) })
                runtimeState.addJoinArrivals(
                    joinNodes.map { joinNode ->
                        PendingJoinInput(joinNode, workItem.nodeName, nextState)
                    },
                )
                continue
            }

            if (runtimeState.hasPendingJoins) {
                val finishedJoins =
                    runtimeState.finishedJoins { nodeName -> graph.parentNodes(nodeName).toSet() }
                if (finishedJoins.isEmpty()) {
                    return fail(ExecutionError.JoinDeadlock(runtimeState.pendingJoins.keys.toSet()), onEvent)
                }

                finishedJoins.forEach { nodeName ->
                    val arrivals = requireNotNull(runtimeState.pendingJoins.remove(nodeName))
                    val orderedStates = graph.parentNodes(nodeName).map(arrivals::getValue)
                    val combinedState =
                        orderedStates.drop(1).fold(orderedStates.first(), graph.reducer::merge)
                    runtimeState.enqueue(listOf(WorkItem(nodeName, combinedState)))
                }
                continue
            }

            if (endNode !in completedStates) {
                return fail(ExecutionError.EndNodeNotReached(endNode), onEvent)
            }

            onEvent(GraphEvent.WorkflowCompleted(WORKFLOW_NAME))
            return RunResult.Success(completedStates.getValue(endNode))
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
