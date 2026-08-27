package com.weave.core

internal data class WorkItem<S>(
    val nodeName: String,
    val state: S,
)

internal data class PendingJoinInput<S>(
    val joinNode: String,
    val parentNode: String,
    val state: S,
)

internal class RuntimeState<S>(
    val workQueue: ArrayDeque<WorkItem<S>> = ArrayDeque(),
    val pendingJoins: LinkedHashMap<String, LinkedHashMap<String, S>> = linkedMapOf(),
) {
    val hasPendingJoins: Boolean
        get() = pendingJoins.isNotEmpty()

    fun dequeue(): WorkItem<S>? = workQueue.removeFirstOrNull()

    fun enqueue(items: Iterable<WorkItem<S>>) {
        workQueue.addAll(items)
    }

    fun addJoinArrivals(inputs: Iterable<PendingJoinInput<S>>) {
        inputs.forEach { input ->
            pendingJoins
                .getOrPut(input.joinNode) { linkedMapOf() }[input.parentNode] = input.state
        }
    }

    fun finishedJoins(expectedParents: (String) -> Set<String>): List<String> =
        pendingJoins
            .filter { (nodeName, arrivals) -> arrivals.keys == expectedParents(nodeName) }
            .keys
            .toList()
}
