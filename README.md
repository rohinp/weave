# Weave

Weave is a Kotlin/JVM graph and workflow runtime built around explicit,
deterministic execution semantics. The repository now contains a single Kotlin
implementation; the former Scala/sbt implementation has been retired.

The current milestone focuses on trustworthy synchronous workflow behavior
before introducing asynchronous or distributed execution.

## What is implemented

- immutable graph definitions with explicit validation
- sealed validation and execution results
- conditional routing over the reduced node state
- FIFO ready-queue scheduling
- branch-local state
- strict joins based on parent identity
- deterministic join-state merge order
- typed join deadlock and unreachable-end failures
- lifecycle callbacks and fixed-attempt retries
- typed conversion of workflow-computation exceptions

Conditional convergence, asynchronous execution, persistence, supervision, and
distributed execution are intentionally outside the current scope.

## Requirements

- JDK 25 (the project emits JVM 21 bytecode)
- no system Gradle installation is required

The repository's `.sdkmanrc` selects the expected JDK for SDKMAN users.

## Build and test

```shell
./gradlew check
```

Run only the test suite with:

```shell
./gradlew test
```

## Minimal example

```kotlin
import com.weave.core.Edge
import com.weave.core.Graph
import com.weave.core.Node
import com.weave.core.Reducer
import com.weave.core.RunResult
import com.weave.core.ValidationResult

val reducer = object : Reducer<Int, Int> {
    override fun reduce(state: Int, update: Int): Int = state + update
    override fun merge(left: Int, right: Int): Int = left + right
}

val graph = Graph.create(reducer)
    .addNode(Node("start") { 1 })
    .addNode(Node("end") { 2 })
    .addEdge(Edge("start", "end"))
    .setStart("start")
    .setEnd("end")

val runner = when (val validation = graph.validate()) {
    is ValidationResult.Valid -> validation.runner
    is ValidationResult.Invalid -> error(validation.error.message)
}

when (val result = runner.run(initialState = 0)) {
    is RunResult.Success -> println(result.state) // 3
    is RunResult.Failure -> error(result.error.message)
}
```

## API contracts

- Invalid graphs never expose a runner.
- Expected workflow failures are returned as `RunResult.Failure`; they are not
  thrown.
- `RetryPolicy.FixedAttempts(maxAttempts)` includes the initial attempt and
  stops after the first success.
- Node actions, reduction, routing predicates, and branch-state merging are
  workflow computation. Their exceptions become typed execution failures and
  emit `WorkflowFailed`.
- Event callbacks are observers. Callback exceptions escape unchanged and are
  neither retried nor converted into workflow failures.
- `Graph.toString()` is diagnostic output and has no stable formatting contract.

## Project layout

```text
src/main/kotlin/com/weave/core/   Public model and runtime
src/test/kotlin/com/weave/core/   Behavioral and API contract tests
build.gradle.kts                  Kotlin/JVM build
Status.md                         Compact project handoff
```

## Direction

The next work should preserve the current semantics and finish migration
quality gates—such as CI and publication decisions—before adding execution
features. Any future concurrency model should sit behind the established
workflow contracts rather than redefine them accidentally.
