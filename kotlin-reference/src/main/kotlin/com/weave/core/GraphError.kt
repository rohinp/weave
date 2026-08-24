package com.weave.core

public sealed interface ValidationError {
    public data object EmptyGraph : ValidationError

    public data object StartNodeNotDefined : ValidationError

    public data object EndNodeNotDefined : ValidationError

    public data class NodeNotFound(public val nodeName: String) : ValidationError
}

public sealed interface ExecutionError {
    public data class NodeFailed(
        public val nodeName: String,
        public val cause: Throwable,
    ) : ExecutionError

    public data class EndNodeNotReached(public val endNode: String) : ExecutionError

    public data class BranchingNotSupported(public val nodeName: String) : ExecutionError
}
