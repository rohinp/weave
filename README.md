# Weave

Weave is a Scala 3/JVM workflow runtime built around explicit, deterministic
execution semantics. The current implementation focuses on a synchronous graph
scheduler while the broader runtime architecture is developed incrementally
with tests.

The longer-term direction is an event-sourced workflow and agent runtime powered
by Apache Pekko. Graph execution will be one workflow model supported by that
runtime, rather than the boundary of the entire library.

## What is implemented

- immutable graph definitions with explicit validation
- conditional routing over reduced node state
- FIFO ready-queue scheduling
- branch-local state
- strict joins based on parent identity
- deterministic join-state merge ordering
- typed join-deadlock and runtime failures
- lifecycle callbacks, checkpoints, and fixed-attempt retries

Event persistence, replay, asynchronous execution, supervision, and distributed
execution are not implemented yet.

## Requirements

- JDK 25
- sbt (the project pins its sbt version in `project/build.properties`)

The repository's `.sdkmanrc` selects the expected JDK for SDKMAN users.

## Build and test

Run the Scala test suite with:

```shell
sbt test
```

Check formatting with:

```shell
sbt scalafmtCheckAll
```

## Project layout

```text
src/main/scala/com/weave/core/   Graph model and synchronous runtime
src/test/scala/com/weave/core/   Behavioral runtime tests
build.sbt                        Scala/sbt build
Status.md                        Architecture and project handoff notes
```

## Development approach

Development is test-driven. The current scheduler semantics should be protected
one scenario at a time before introducing durable event sourcing and Pekko-backed
execution.
