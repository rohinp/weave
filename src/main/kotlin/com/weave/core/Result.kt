package com.weave.core

/** Result of graph validation. Invalid graphs never expose a runner. */
public sealed interface ValidationResult<S, U> {
    /** A validated graph ready for execution. */
    public data class Valid<S, U>(public val runner: GraphRunner<S, U>) : ValidationResult<S, U>

    /** The first validation error found. */
    public data class Invalid<S, U>(public val error: ValidationError) : ValidationResult<S, U>
}

/** Explicit outcome of workflow execution. */
public sealed interface RunResult<out S> {
    /** Successful state produced by the configured end node. */
    public data class Success<S>(public val state: S) : RunResult<S>

    /** A workflow-owned execution failure. */
    public data class Failure(public val error: ExecutionError) : RunResult<Nothing>
}
