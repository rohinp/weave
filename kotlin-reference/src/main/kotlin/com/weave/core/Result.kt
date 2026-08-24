package com.weave.core

public sealed interface ValidationResult<S, U> {
    public data class Valid<S, U>(public val runner: GraphRunner<S, U>) : ValidationResult<S, U>

    public data class Invalid<S, U>(public val error: ValidationError) : ValidationResult<S, U>
}

public sealed interface RunResult<out S> {
    public data class Success<S>(public val state: S) : RunResult<S>

    public data class Failure(public val error: ExecutionError) : RunResult<Nothing>
}
