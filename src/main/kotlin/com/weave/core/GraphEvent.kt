package com.weave.core

public sealed interface GraphEvent {
    public val name: String

    public data class NodeStarted(override val name: String) : GraphEvent

    public data class NodeCompleted(override val name: String) : GraphEvent

    public data class NodeFailed(
        override val name: String,
        public val cause: Throwable,
    ) : GraphEvent

    public data class CheckpointCreated<S>(
        override val name: String,
        public val state: S,
    ) : GraphEvent

    public data class WorkflowCompleted(override val name: String) : GraphEvent

    public data class WorkflowFailed(
        override val name: String,
        public val cause: Throwable,
    ) : GraphEvent
}
