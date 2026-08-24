Below is a compact handoff summary you can paste into Codex as project context.

# Weave — Project Handoff Summary

## Project Goal

Weave is a Scala-first graph/workflow runtime being built from first principles, inspired by systems such as LangGraph, but intentionally developed incrementally with TDD.

The goal is not simply to clone an agent framework. The project is evolving toward a more general workflow/agent orchestration runtime where execution semantics are clearly separated from a future execution engine such as Pekko.

Current principle:

```text
Workflow semantics first
Execution runtime later
```

Pekko, async execution, supervision, distributed execution, persistence, and parallelism are intentionally postponed until the core scheduling semantics are correct.

---

# 1. Initial Graph Model

The project started from a very simple immutable graph model.

Conceptually:

```scala
Graph[S]
Node[S]
```

Originally a node behaved like:

```scala
S => S
```

The graph contained:

- nodes
- edges
- start node
- end node

Execution initially performed simple graph traversal.

---

# 2. Validation Phase

Validation was separated from execution.

Architecture:

```text
Graph
  ↓ validate
GraphRunner
  ↓ run
```

Rather than throwing validation exceptions, validation returns graph errors.

Validation currently covers concepts such as:

- missing start
- missing end
- missing nodes
- invalid edge targets
- graph structure sanity

The exact return API has changed during development, but the intent is explicit validation before execution.

---

# 3. Conditional Edges

Edges evolved from a simple mapping into first-class values.

Conceptually:

```scala
Edge[S](
  from: String,
  to: String,
  condition: S => Boolean
)
```

Routing happens against the state produced after executing the current node.

So:

```text
state
  ↓ node
update
  ↓ reducer
new state
  ↓ edge conditions
next nodes
```

There is already a test verifying that routing uses the reduced/new state.

---

# 4. Runtime Events

Lifecycle events were introduced.

Current event concepts include:

```scala
NodeStarted
NodeCompleted
NodeFailed

CheckpointCreated

WorkflowCompleted
WorkflowFailed
```

Execution accepts:

```scala
onEvent: GraphEvent => Unit
```

Events are callback-based for now.

No actor/event-stream infrastructure yet.

---

# 5. Runtime Failure Handling

Node exceptions are owned by the workflow runtime instead of leaking directly through the JVM.

Node execution is wrapped in `Try`, then converted into runtime errors.

Important semantics:

```text
NodeStarted
↓
node execution
↓
success:
  NodeCompleted
  CheckpointCreated

failure:
  NodeFailed
  possibly retry
  eventually WorkflowFailed
```

Internally, `executeNode` now correctly returns:

```scala
Either[GraphError.ExecutionError, S]
```

rather than relying on unsafe casts.

The public `run` API still returns a Scala 3 union:

```scala
GraphError.ExecutionError | S
```

A likely cleanup is to change the public API to:

```scala
Either[GraphError.ExecutionError, S]
```

because this is naturally a success/failure computation.

---

# 6. Retry Policies

Retry policies were introduced.

Current policies include:

```scala
RetryPolicy.Never

RetryPolicy.FixedAttempts(maxAttempts)
```

Retries actually re-run:

```scala
node.f(state)
```

rather than retrying a previously-created `Try`.

Existing tests cover:

- no retries under `Never`
- retry until success
- stop retrying after success

A useful missing test:

```text
when all retries fail:
- exact number of attempts happens
- NodeFailed is emitted for every failed attempt
- WorkflowFailed is emitted exactly once
- WorkflowCompleted is never emitted
```

---

# 7. Reducer-Based State Model

The state model evolved from:

```scala
Node[S](S => S)
```

to:

```scala
Graph[S, U]

Node[S, U](S => U)

Reducer[S, U]
```

Execution now follows:

```text
S
↓
Node[S,U]
↓
U
↓
Reducer.reduce
↓
S
```

Reducer concept:

```scala
trait Reducer[S, U] {
  def reduce(state: S, update: U): S

  def merge(left: S, right: S): S
}
```

The implementation may currently support a variadic `merge`, but conceptually merge combines branch states.

Tests already verify:

- reducer is applied after node execution
- edge routing sees reduced state
- direct reducer merge behavior

---

# 8. Why WorkItem Was Introduced

Originally the scheduler looked like:

```text
List[nodeName]
+
single global state
```

This caused branches to share state sequentially.

Example:

```text
start
├── docs
└── web
```

would effectively behave like:

```text
start(state)
↓
docs(state)
↓
web(docsState)
```

instead of:

```text
docs(startState)
web(startState)
```

The scheduler was therefore changed to schedule node execution together with branch-local state.

Current abstraction:

```scala
case class WorkItem[S](
  nodeName: String,
  state: S
)
```

The runtime now schedules:

```text
Node execution + input state
```

rather than merely node names.

This enabled correct fan-out semantics.

There is a passing test:

```text
branches receive independent copies of state
```

---

# 9. BFS / Ready Queue

The traversal is still conceptually BFS.

Current ready work uses:

```scala
scala.collection.immutable.Queue[WorkItem[S]]
```

The important architectural change is not BFS vs DFS.

It is:

```text
before:
queue of nodes

now:
queue of execution requests
```

---

# 10. RuntimeState

The scheduler was expanded to distinguish ready work from blocked joins.

Current abstraction:

```scala
case class RuntimeState[S](
  workQueue: Queue[WorkItem[S]],
  pendingJoins: Map[String, Map[String, S]]
)
```

Meaning:

```text
workQueue
=
nodes ready to execute

pendingJoins
=
joinNode -> parentNode -> branch state
```

This is an important scheduler distinction:

```text
ready work
vs
blocked work
```

RuntimeState currently contains helpers roughly like:

```scala
def dequeue

def enqueue

def addMultipleJoinArrival

def removeJoin

def isJoinPending
```

---

# 11. PendingJoinInput

Branch states approaching a multi-parent node preserve their source parent.

Current abstraction:

```scala
case class PendingJoinInput[S](
  joinNode: String,
  parentNode: String,
  state: S
)
```

This was introduced because:

```scala
Map[String, List[S]]
```

was insufficient.

Knowing only state arrival count cannot distinguish:

```text
branch1 arrived twice
```

from:

```text
branch1 + branch2 arrived
```

Current storage:

```scala
Map[
  joinNode,
  Map[parentNode, state]
]
```

is much safer.

---

# 12. Join / Barrier Semantics

Current scheduler semantics support a strict multi-parent join.

Example:

```text
       start
       /   \
  branch1 branch2
       \   /
       merge
```

Execution behaves conceptually as:

```text
branch1 completes
→ register arrival for merge

branch2 completes
→ register arrival for merge

when merge has all expected inputs:
  merge branch states
  enqueue one WorkItem("merge", combinedState)
```

The join node should therefore execute exactly once.

There is already a passing test:

```text
merge node executes exactly once
```

---

# 13. Important Join Limitation

Current join readiness is approximately determined by:

```scala
pendingJoinInputs.size == incomingEdgeCount(joinNode)
```

A better next refactor is to compare actual parent identities.

Add something like:

```scala
def parentNodes(nodeName: String): Set[String]
```

Then join completion should be based on:

```scala
arrivals.keySet == graph.parentNodes(nodeName)
```

rather than only count.

This expresses the real invariant:

```text
all required parents have arrived
```

---

# 14. Deterministic Merge Ordering

Current join state collection may use:

```scala
pendingJoins(nodeName).values.toList
```

That should not define merge order because map iteration order should not become workflow semantics.

Prefer ordering states according to the graph's incoming edges.

Conceptually:

```scala
val parents =
  graph.incomingEdges(nodeName).map(_.from)

val orderedStates =
  parents.map(pendingInputs)

val combined =
  orderedStates.reduceLeft(graph.reducer.merge)
```

Long-term reducer merge semantics need a contract.

Ideally merge is:

```text
associative
```

and preferably:

```text
commutative
```

Otherwise the runtime must guarantee deterministic ordering.

---

# 15. End Node Semantics Need Cleanup

An important bug was identified.

Current runner may still execute children of an end node and only later return the state previously captured for the end node.

Example:

```text
a -> b -> c
     ^
    end
```

A test that only checks the returned state after `b` can pass even if `c` actually executed.

A required test is:

```text
does not execute children of the end node
```

using `NodeStarted` events.

Expected started nodes:

```text
a
b
```

not:

```text
a
b
c
```

Important branching nuance:

```text
reaching one end node must not necessarily stop the entire scheduler
if other ready branches still exist
```

So the likely rule is:

```text
end node executes
its children are not scheduled
other existing WorkItems continue
workflow finishes when scheduler is drained
```

---

# 16. GraphState

Execution also currently maintains something like:

```scala
GraphState[S]
```

which stores state by executed node.

This is used to retrieve the final state's end-node value.

Important distinction:

```text
RuntimeState
=
scheduler bookkeeping

GraphState
=
execution result/history bookkeeping
```

Earlier versions incorrectly used GraphState to determine whether joins were complete.

That was changed because:

```text
"parent executed sometime"
```

is not equivalent to:

```text
"join received input from parent"
```

Pending join arrivals are now the correct source of truth.

---

# 17. Current GraphRunner Shape

Current runner conceptually does:

```text
execute(runtimeState, graphState):

  dequeue ready WorkItem

  if work exists:
      execute node

      on failure:
          return execution error

      on success:
          reduce update to new state

          find children whose edge conditions pass

          partition children into:
              normal children
              multi-parent children

          enqueue normal children

          register pending join arrivals

          record executed node state

          recurse

  else if pending joins exist:

      find completed joins

      if none completed:
          runtime deadlock/error

      otherwise:
          merge their branch states
          enqueue completed join WorkItems
          remove joins from pending map
          recurse

  else:
      return final end state
```

---

# 18. Runtime Deadlock

Current runtime has a branch similar to:

```text
ready queue empty
+
pending joins exist
+
none are complete
```

This currently produces a generic RuntimeError.

This should evolve into a dedicated concept such as:

```scala
JoinDeadlock(
  pendingJoins: Set[String]
)
```

Do not state:

```text
"this must not happen because validation passed"
```

because runtime edge conditions can make a structurally-valid graph deadlock.

Validation cannot necessarily prove runtime progress.

---

# 19. Conditional Routing Limitation

A test for conditional branches currently remains ignored.

Example:

```text
        start
       /     \
 positive   negative
       \     /
          end
```

Only one branch activates.

Current scheduler identifies a multi-parent node structurally:

```text
incoming edge count > 1
```

Therefore `end` becomes a strict AND join and waits for both parents even though only one branch was activated.

This exposes an important future semantic distinction:

```text
strict AND join
vs
activated-parent join
vs
OR join
```

Current milestone should explicitly support only:

```text
unconditional fan-out + strict AND joins
```

Conditional convergence should remain postponed.

TODO wording:

```text
Conditional convergence requires activated-parent join semantics.
The current scheduler treats every multi-parent node as a strict AND join.
```

---

# 20. Multiple End Nodes

The API/test also attempted:

```scala
.setEnd("positive")
.setEnd("negative")
```

Multiple end-node semantics are not currently defined.

Do not rush this.

For now, a single declared terminal node is simpler.

Conditional-routing graphs can eventually:

- support multiple terminal nodes, or
- converge into a synthetic/real final node using activated-parent semantics.

---

# 21. Current Tests

Existing tests broadly cover:

## Graph execution

- simple start → end graph
- start and end same node
- end-state retrieval
- BFS work processing

## Reducers

- reducer applied after node
- routing uses reduced state
- branch-local independent state
- reducer merge behavior
- branch merge unit behavior
- join executes exactly once

## Events

- NodeStarted
- NodeCompleted
- CheckpointCreated
- NodeFailed
- WorkflowFailed
- WorkflowCompleted

## Retry

- Never
- succeeds after retries
- no unnecessary retries after success

## Validation

- valid graph
- missing start
- missing node
- invalid target
- identifying multi-parent node

---

# 22. Tests That Should Be Added Next

These are the highest-value tests for the current milestone.

## A. End node does not schedule children

Graph:

```text
a -> b -> c
     ^
    end
```

Assert started nodes are exactly:

```text
a, b
```

---

## B. Join starts only after all parents complete

Graph:

```text
start
├── branch1
└── branch2
    ↓
  merge
```

Use event indexes.

Assert:

```text
NodeStarted(merge)
```

occurs after:

```text
NodeCompleted(branch1)
NodeCompleted(branch2)
```

---

## C. Join receives merged states from all branches

Use a simple state:

```scala
case class State(values: Vector[String])
```

and updates:

```scala
Append("...")
```

Graph:

```text
start
├── docs
└── web
    ↓
  merge
```

Capture the state seen by merge.

Assert it contains:

```text
start
docs
web
```

The final result should additionally contain:

```text
merged
```

---

## D. Three-parent join

Graph:

```text
       start
      /  |  \
     a   b   c
      \  |  /
       merge
```

Assert:

- merge starts once
- merge starts after all 3 parents
- merge sees all 3 branch outputs

This validates variadic/multiple state merge semantics.

---

## E. Duplicate parent arrival does not satisfy join

Direct RuntimeState unit test.

Expected parents:

```text
branch1
branch2
```

Arrivals:

```text
branch1
branch1
```

Join must remain incomplete.

This protects parent-identity semantics.

---

## F. Multiple independent joins

Graph:

```text
          start
       /   |   |   \
     a1   a2  b1   b2
      \   /    \   /
      joinA    joinB
```

Assert both joins execute exactly once.

---

## G. Join can schedule downstream work

Graph:

```text
start
├── a
└── b
    ↓
  merge
    ↓
  final
```

Assert `final` executes with merge's state.

---

## H. Incomplete join produces runtime deadlock

Graph with strict AND join where one incoming conditional edge never activates.

Assert execution returns a deadlock/runtime scheduling error.

---

## I. Exhausted retries

Node always fails with:

```scala
FixedAttempts(3)
```

Assert:

```text
3 NodeStarted
3 NodeFailed
1 WorkflowFailed
0 WorkflowCompleted
```

---

## J. WorkflowCompleted emitted once

Verify this for:

- linear graph
- fan-out/fan-in graph

Failure path must emit zero WorkflowCompleted events.

---

# 23. Tests That Should Be Strengthened

Current:

```text
"all queued work items are processed"
```

should not only assert merge count.

Instead assert execution of:

```text
start
branch1
branch2
merge
```

exactly once.

Avoid depending on order of independent branches unless BFS insertion order is intentionally part of the contract.

---

# 24. RuntimeState Refactoring Suggestions

Useful API cleanup:

```scala
def enqueue(item: WorkItem[S]): RuntimeState[S]

def enqueue(items: Iterable[WorkItem[S]]): RuntimeState[S]

def removeJoin(nodeName: String): RuntimeState[S]
```

Then GraphRunner can do:

```scala
runtimeState
  .removeJoin(nodeName)
  .enqueue(WorkItem(nodeName, combinedState))
```

rather than directly modifying RuntimeState internals.

Remove unused values such as:

```scala
EmptyWorkQueue
NoneCompletedJoins
```

if they are no longer referenced.

---

# 25. Event Tests

Avoid comparing exceptions through:

```scala
events.toString
```

Prefer structural pattern matching.

Example intent:

```text
NodeStarted(explode)
NodeFailed(explode, boom)
WorkflowFailed(workflow, boom)
```

Assert:

- exception message
- same throwable instance if desired
- no WorkflowCompleted

This is less brittle.

---

# 26. Reducer Merge Caveat

When branches start from the same common ancestor:

```text
question
├── docs result includes question
└── web result includes question
```

merging full branch states may duplicate:

```text
question
```

Example:

```text
left  = question, docs
right = question, web
```

naïve concatenation becomes:

```text
question, docs, question, web
```

This is currently being handled in test reducers manually, e.g. by:

```scala
distinct
```

or:

```scala
right.messages.drop(1)
```

Do not solve this deeply yet.

This is a future state-model concern.

Possible later solutions include:

- merge branch updates rather than whole states
- field-specific reducers
- common-ancestor-aware merge
- state versions
- per-field merge policies

Current scheduler milestone only needs predictable merge behavior.

---

# 27. What Not To Implement Yet

Do not add these yet:

```text
Pekko actors
async execution
parallel execution
distributed execution
persistence
checkpoint recovery
loops
repeated join activations
conditional convergence
dynamic fan-out
multiple join policies
execution IDs
workflow IDs
```

The current runtime is still proving strict DAG scheduling semantics.

---

# 28. Current Milestone Definition

The current milestone can be described as:

```text
Immutable synchronous workflow scheduler
with:
- BFS ready queue
- branch-local state
- conditional routing
- reducer-based updates
- strict AND fan-in joins
- retries
- runtime events
- checkpoints
- explicit runtime failures
```

Once the key tests above are green, this milestone is solid.

---

# 29. Likely Next Architectural Step

After strict DAG scheduling is stable, the next major problem is:

```text
conditional convergence / activated-parent joins
```

Example:

```text
        start
       /     \
     true    false
       \     /
        next
```

A structural parent count is insufficient because not every incoming edge is active during every execution.

At that point the runtime will need to distinguish:

```text
graph topology
```

from:

```text
activated execution dependencies
```

This will likely lead toward execution-level identity rather than relying only on node names.

Potential future concepts:

```scala
ExecutionId
BranchId
Activation
JoinInstance
```

But these should be introduced only when required by a failing test.

---

# 30. Guiding Development Approach

Continue using TDD and let failing graph scenarios drive abstractions.

Preferred progression:

```text
simple execution
↓
branching
↓
branch-local state
↓
ready queue
↓
blocked joins
↓
strict fan-in
↓
conditional activation
↓
loops/repeated execution
↓
async scheduling
↓
Pekko/runtime distribution
```

Avoid designing future orchestration features before the current scheduler semantics demand them.

The project has now progressed beyond a basic graph walker and is becoming a genuine workflow scheduler.