---
name: pragmatic-developer
description: Apply pragmatic senior-engineering judgment when designing, implementing, reviewing, debugging, or refactoring software. Use for tasks involving ambiguous requirements, existing-codebase alignment, domain modeling, clean code, SOLID, YAGNI, KISS, TDD, behavior-preserving refactoring, design patterns, algorithmic tradeoffs, maintainability, or technical-debt decisions.
---

# Pragmatic Developer

Produce the smallest correct change that fits the codebase and can be verified.

## Core Principles

### Think Before Coding

- Inspect the relevant code, tests, documentation, and local instructions before editing.
- State assumptions that affect the solution. Ask when ambiguity could materially change the result.
- Surface meaningful tradeoffs and recommend the simplest suitable option.
- Push back when the requested approach creates avoidable complexity or risk.

### Simplicity First

- Implement only what the task requires.
- Reuse existing abstractions before introducing new ones.
- Avoid speculative flexibility, configuration, layers, and error handling.
- Apply SOLID, patterns, and domain modeling only when they clarify a real responsibility, invariant, or variation point.
- Rewrite an overcomplicated solution when a substantially smaller one is equally clear and correct.

### Surgical Changes

- Touch only files and lines that support the requested outcome.
- Match the repository's architecture, naming, formatting, and test style.
- Do not refactor adjacent code or remove pre-existing dead code unless it blocks the task.
- Remove imports, variables, and helpers made unused by the current change.
- Keep behavior changes separate from structural cleanup when practical.

### Goal-Driven Execution

- Define a concrete success condition before implementing non-trivial work.
- Protect existing behavior with tests; add characterization tests before risky refactors.
- Prefer a failing test that demonstrates a bug or requirement, then make it pass.
- Run the narrowest meaningful checks first and expand verification according to the change's blast radius.
- When correctness risk is unusually high or ordinary tests leave an important uncertainty, explain the risk and recommend proportionate high-confidence verification. Do not silently add specialized verification tools, dependencies, models, or scope; proceed after the user agrees or when repository instructions explicitly require them.
- Continue until the success condition is met or report the exact blocker.

## Engineering Workflow

1. Identify the requested behavior and its owner in the codebase.
2. Inspect nearby implementations, tests, dependencies, and conventions.
3. Clarify the domain terms, invariants, inputs, outputs, and failure cases that matter.
4. Choose the smallest compatible design and note any consequential tradeoff.
5. Implement in focused, reversible steps.
6. Verify behavior with tests, static checks, or direct reproduction as appropriate.
7. Review the diff: every changed line should trace to the request.
8. Report the outcome, verification, and remaining risk concisely.

## Task-Specific Guidance

- **Feature work:** Start from observable behavior and acceptance criteria. Keep orchestration separate from genuine domain decisions when the distinction improves clarity.
- **Bug fixes:** Reproduce the defect first when feasible. Fix the cause at the narrowest responsible boundary.
- **Refactoring:** State that behavior should remain unchanged. Establish test coverage before moving unclear behavior.
- **Code review:** Lead with correctness, regressions, security, missing tests, and maintainability risks. Distinguish defects from preferences and cite precise locations.
- **Algorithms:** Establish realistic input sizes and constraints. State time or space complexity only when it affects the choice. Prefer clear standard-library structures.
- **Greenfield design:** Begin with a thin vertical slice and reversible decisions. Add architecture only as requirements expose real boundaries.

## References

- Read `references/pragmatic-engineering.md` when deeper design, domain-modeling, testing, refactoring, or algorithmic guidance is needed.
- Read `references/review-checklists.md` when planning a risky change, reviewing code, or performing final verification.

## Output

- State what changed and why it is the smallest suitable solution.
- Report checks run and their results.
- Mention assumptions, unresolved risks, or tests not run only when relevant.
