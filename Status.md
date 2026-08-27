# Weave — Project Handoff

## Goal

Weave is a Kotlin/JVM graph and workflow runtime developed from first
principles. The immediate priority is stable, deterministic workflow semantics;
new runtime features come after the Scala-to-Kotlin migration is fully closed.

Guiding principle:

```text
Workflow semantics first
Execution infrastructure later
```

## Migration status

The Kotlin implementation is now the main project rather than a reference
submodule.

- Gradle build, wrapper, Kotlin sources, and Kotlin tests live at repository root.
- Root project name is `weave`.
- The Scala sources, Scala tests, sbt configuration, Pekko quickstart, and
  Pekko-specific logging configuration have been removed.
- The project has no Scala or Apache Pekko dependency.
- The Kotlin behavior suite contains 33 tests ported from or added against the
  stabilized Scala semantics.

## Toolchain

- Kotlin: 2.4.10
- Gradle wrapper: 9.5.0, distribution checksum pinned
- Java toolchain: JDK 25
- JVM target: 21
- JUnit: 6.1.3
- Kotlin explicit API mode and warnings-as-errors are enabled
- Gradle configuration cache, build cache, and parallel execution are enabled

Canonical verification command:

```shell
./gradlew clean check
```

## Public model

Package: `com.weave.core`

- `Graph<S, U>` is an immutable graph definition.
- `Node<S, U>` maps state `S` to update `U` and owns its retry policy.
- `Edge<S>` has `from`, `to`, and a state predicate.
- `Reducer<S, U>` reduces updates and merges branch states.
- `ValidationResult` exposes either a validated `GraphRunner` or a
  `ValidationError`.
- `RunResult` exposes either the final state or an `ExecutionError`.
- `GraphEvent` reports node, checkpoint, and workflow lifecycle events.

## Execution semantics

Execution is synchronous and deterministic:

1. A FIFO ready queue schedules work.
2. Every outgoing branch receives the state produced by its parent.
3. A multi-parent node is a strict join and waits for every distinct configured
   parent.
4. Join inputs merge in graph parent order, independent of arrival order.
5. If only incomplete joins remain, execution returns
   `ExecutionError.JoinDeadlock`.
6. If scheduling finishes without executing the configured end node, execution
   returns `ExecutionError.EndNodeNotReached`.

The configured end node is executed like any other node. Its reduced state is
the successful workflow result.

## Failure and callback contracts

- Exceptions from node actions, reduction, routing predicates, and branch-state
  merging become `RunResult.Failure(ExecutionError.RuntimeError)`.
- A terminal workflow failure emits `WorkflowFailed`.
- `RetryPolicy.FixedAttempts(n)` counts the initial execution and stops on the
  first success.
- Callback exceptions escape unchanged. Callbacks are observers, not workflow
  computation, and are never retried.
- Validation and expected execution failures are values, not thrown exceptions.

## Intentional limitations

The following are deferred feature work, not migration gaps:

- conditional convergence or partial joins
- asynchronous or parallel execution
- cycles and general loop semantics
- persistence and checkpoint recovery
- cancellation, timeouts, and supervision
- actor, distributed, or remote execution
- stable serialization and compatibility guarantees

`Graph.toString()` remains diagnostic-only.

## Next migration-quality work

Before expanding runtime behavior, decide and implement only the operational
pieces needed to ship the Kotlin project:

1. CI running `./gradlew clean check` on the supported JDK.
2. Artifact coordinates, publication, and compatibility policy if the library
   will be consumed externally.
3. Formatting/static-analysis tooling if the team wants it enforced.
4. A release or migration note for any previous Scala consumers.

These items should not alter the stabilized execution semantics.
