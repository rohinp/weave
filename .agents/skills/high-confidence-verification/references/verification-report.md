# Verification Report

Report only evidence actually obtained. Keep the report proportional to the task.

## Suggested Format

```text
Risk:
- Failure being addressed and why it matters.

Specification:
- Preconditions, postconditions, invariants, or model boundaries used.

Technique:
- Technique selected and why it fits this failure mode.

Evidence:
- Exact commands or analyses run and their results.
- Counterexamples found and fixed, if any.
- Representative defect used to confirm the check can fail, if performed.

Scope and limits:
- Inputs, states, bounds, numeric semantics, assumptions, timeouts, or unsupported behavior.

Remaining risk:
- Important behavior not checked or claims that cannot be made.
```

## Evidence Vocabulary

Use precise wording:

- **Tests passed:** the executed examples or generated cases passed.
- **Exhaustively checked:** every value or state inside the explicitly stated finite bound was checked.
- **No counterexample found:** the analyzer did not find one within its supported paths, configuration, and limits.
- **Solver returned `unsat`:** the encoded negation has no satisfying assignment in the selected logic.
- **Proved:** reserve for a valid proof with clearly stated specification, model, assumptions, and correspondence to the implementation.

Never report a check that was not run. If a tool was unavailable or returned unknown, state that directly.

