# Weave Kotlin reference

This standalone Gradle build is the Kotlin/JVM reference implementation of
Weave's synchronous workflow semantics.

It intentionally has no dependency on the Scala module or Apache Pekko. During
the migration, the stabilized Scala tests remain the behavioral source of
truth and equivalent Kotlin scenarios are added incrementally.

## Current scope

- immutable graph definitions
- basic graph validation
- explicit validation and execution results
- retry policy and lifecycle event contracts
- FIFO ready queue with branch-local state
- strict parent-identity joins with deterministic state merge order
- typed join deadlocks
- callback events and fixed-attempt retries

Conditional convergence remains outside the current strict-join milestone.

## API contracts

- Validation and execution use sealed `ValidationResult` and `RunResult` values;
  expected workflow failures are not thrown.
- `FixedAttempts(maxAttempts)` counts the initial execution as an attempt and
  stops immediately after the first success.
- Failing to reach the configured end node returns the typed
  `ExecutionError.EndNodeNotReached` result.
- Node actions, state reduction, routing predicates, and branch-state merging
  are workflow computation. Their exceptions become `RunResult.Failure` and
  emit `WorkflowFailed`.
- Event callbacks are observers. Callback exceptions escape unchanged and are
  never retried or converted into workflow failures.
- `Graph.toString()` is diagnostic output only and has no stable formatting
  contract.

## Run tests

```shell
./gradlew test
```
