package com.weave.core

/** A graph-definition error discovered before execution. */
public sealed interface ValidationError {
    public val message: String

    public data object EmptyGraph : ValidationError {
        override val message: String = "Graph is empty"
    }

    public data object StartNodeNotDefined : ValidationError {
        override val message: String = "Start node not defined"
    }

    public data object EndNodeNotDefined : ValidationError {
        override val message: String = "End node not defined"
    }

    public data class NodeNotFound(public val nodeName: String) : ValidationError {
        override val message: String = "Node '$nodeName' not found in graph"
    }
}

/** A workflow-owned failure represented by [RunResult.Failure]. */
public sealed interface ExecutionError {
    public val cause: Throwable

    public val message: String

    public data class RuntimeError(
        public val nodeName: String,
        override val cause: Throwable,
    ) : ExecutionError {
        override val message: String = "Runtime error in node '$nodeName': ${cause.message}"
    }

    public data class JoinDeadlock(public val pendingJoins: Set<String>) : ExecutionError {
        override val message: String =
            "Workflow cannot progress; pending joins: ${pendingJoins.sorted().joinToString()}"
        override val cause: Throwable = IllegalStateException(message)
    }

    public data class EndNodeNotReached(public val endNode: String) : ExecutionError {
        override val message: String = "Workflow completed without executing end node '$endNode'"
        override val cause: Throwable = IllegalStateException(message)
    }
}
