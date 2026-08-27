package com.weave.core

/** Executes a validated graph synchronously. */
public class GraphRunner<S, U> internal constructor(private val graph: Graph<S, U>) {
    /**
     * Executes the workflow and returns an explicit success or workflow failure.
     *
     * Exceptions raised by node actions, state reduction, routing predicates, and
     * branch-state merging become [RunResult.Failure]. Exceptions raised by
     * [onEvent] escape unchanged because the callback is an observer, not part of
     * workflow computation.
     */
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

                val childrenResult =
                    runCatching {
                        if (workItem.nodeName == endNode) {
                            emptyList()
                        } else {
                            graph.nextNodes(workItem.nodeName, nextState)
                        }
                    }
                val children = childrenResult.getOrElse { cause ->
                    return fail(ExecutionError.RuntimeError(workItem.nodeName, cause), onEvent)
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

                for (nodeName in finishedJoins) {
                    val arrivals = requireNotNull(runtimeState.pendingJoins.remove(nodeName))
                    val combinedStateResult =
                        runCatching {
                            val orderedStates = graph.parentNodes(nodeName).map(arrivals::getValue)
                            orderedStates.drop(1).fold(orderedStates.first(), graph.reducer::merge)
                        }
                    val combinedState = combinedStateResult.getOrElse { cause ->
                        return fail(ExecutionError.RuntimeError(nodeName, cause), onEvent)
                    }
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
