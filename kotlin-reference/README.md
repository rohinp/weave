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

## Run tests

```shell
./gradlew test
```
