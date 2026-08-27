---
name: high-confidence-verification
description: Select and apply proportionate correctness techniques for high-risk or hard-to-reason-about code, including contracts, property-based testing, domain types, invariants, state-machine testing, exhaustive checks, static or symbolic analysis, and focused SMT or model checking. Use when the user explicitly requests stronger or formal verification, repository instructions require it, or after identifying material correctness risk and obtaining the user's agreement on scope. Do not use merely to add ceremony to routine code.
---

# High-Confidence Verification

Increase confidence with the smallest technique that addresses a concrete failure risk. Treat specification and evidence as separate: tests and proofs only establish properties that were stated and modeled correctly.

## Respect the Verification Gate

Proceed only when at least one condition holds:

1. The user explicitly asks for high-confidence or formal verification.
2. Applicable repository instructions require a technique.
3. A material risk is identified, the technique and cost are proposed, and the user agrees.

An explicit request authorizes proportionate work within the requested scope. It does not automatically authorize new dependencies, architectural changes, long-running analysis, or a broader verification project.

When the skill was not explicitly requested, do not silently add specialized techniques. State:

- the concrete failure risk;
- the proposed technique and why ordinary tests may be insufficient;
- the expected code, dependency, and runtime cost;
- the narrower alternative;
- the decision required from the user.

Running existing checks and adding ordinary focused tests remain normal implementation work unless the user or repository says otherwise.

## Follow the Workflow

1. **Establish authority.** Record which verification-gate condition applies. Stop at a recommendation if user agreement is still needed.
2. **Specify behavior.** State relevant assumptions, preconditions, postconditions, invariants, invalid states, and observable failure behavior.
3. **Classify risk.** Consider failure impact, likelihood, reversibility, state-space complexity, and how easily ordinary tests could miss the defect.
4. **Select evidence.** Read `references/technique-selection.md` and choose the least costly technique that targets the identified risk.
5. **Agree on escalation.** Before adding tools or material scope, present the recommendation and obtain any required consent.
6. **Implement idiomatically.** Follow the repository and language conventions. Do not distort production design merely to demonstrate a formal technique.
7. **Seek counterexamples.** Make failures reproducible and confirm that tests or checks would fail for a representative defect.
8. **Run proportionate checks.** Start narrowly, then expand according to the affected contract and blast radius.
9. **Report evidence.** Use `references/verification-report.md`. Distinguish observations, bounded checks, solver results, and proofs precisely.

## Escalate Gradually

Use this general order, skipping techniques that do not match the failure mode:

```text
examples and boundary tests
→ executable contracts and invariants
→ property-based or exhaustive tests
→ stronger domain types
→ state-machine or reference-model tests
→ static or symbolic analysis
→ focused SMT solving or model checking
```

Do not climb the ladder because a higher step sounds more rigorous. Escalate when the current evidence leaves an important, addressable uncertainty.

## Preserve Honest Limits

- A passing test establishes only the exercised behavior.
- Property-based testing samples a domain unless it is explicitly exhaustive.
- Types prevent only the invalid states they encode and only at checked boundaries.
- A model checker proves properties of its bounded model, not automatically the production system.
- An SMT `unsat` result proves that the encoded counterexample formula has no model under the selected logic and assumptions.
- Mathematical integers do not model machine overflow unless encoded accordingly.
- Symbolic tools may time out, return unknown, or omit unsupported behavior.
- Never use words such as *proved*, *verified*, or *exhaustive* without stating the exact scope and assumptions.

## Use Language Guidance

Read `references/language-guidance.md` before selecting tools for Python, Scala 3, or Kotlin. Prefer tools already present in the repository. Ask before introducing a new dependency or build step unless the user already requested that exact tool.

## Keep Scope Deliberate

Do not:

- apply every technique to every change;
- replace readable code with verification scaffolding without a clear benefit;
- treat generated tests as a substitute for examples and domain understanding;
- weaken assertions or alter tests merely to make a check pass;
- claim whole-system correctness from a small verified kernel;
- continue into expensive verification after the agreed success condition is met.

