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
- synchronous linear execution with reducer-based updates
- callback events and fixed-attempt retries for linear execution
- explicit failure when branching reaches the not-yet-ported scheduler boundary

Strict fan-out and join scheduling will be ported in subsequent vertical slices.

## Run tests

```shell
./gradlew test
```
