# Technique Selection

Select a technique because it addresses a named failure mode, not because it appears on a checklist.

## Risk Levels

### Low

Examples include formatting, simple mappings, UI presentation, and reversible glue code. Prefer examples, boundary tests, and existing static checks. This skill will rarely add value.

### Medium

Examples include business rules, validation, data transformations, persistent state, and non-trivial algorithms. Consider contracts, properties, domain types, invariants, or exhaustive checks over a small domain.

### High

Examples include money, authorization, security boundaries, concurrency, irreversible operations, migrations, data integrity, and complex state transitions. Recommend stronger evidence, but still obtain agreement before introducing specialized tools or substantial modeling.

## Selection Table

| Failure risk or code shape | First suitable technique | Evidence added | Important limitation |
|---|---|---|---|
| Invalid input crosses a boundary | Preconditions and ordinary boundary tests | Rejection behavior is executable | Runtime checks do not prevent construction |
| Output must obey a precise rule | Postconditions | Checks each executed result | Covers only executions that occur |
| An object must remain valid across operations | Data or class invariants | Checks valid state at construction and transitions | External mutation or unchecked deserialization may bypass it |
| Many inputs share a general rule | Property-based testing | Searches generated examples and shrinks failures | Usually samples rather than exhausts the domain |
| Invalid state combinations should be impossible | Domain modeling with types | Moves selected errors to construction or compilation | Dynamic languages and unchecked boundaries weaken the guarantee |
| Correctness depends on operation sequences | State-machine or model-based testing | Compares transitions and state against a simpler model | The model may repeat the implementation's mistake |
| Domain is genuinely small and bounded | Exhaustive testing | Checks every member of the stated domain | Says nothing outside that bound |
| Compiler or analyzer can reason about paths | Static or symbolic analysis | Finds type or path counterexamples without hand-written cases | Tool coverage may be incomplete or return unknown |
| Small critical formula has a precise specification | SMT solving | Proves satisfiability or absence of encoded counterexamples | Proves the formula, not the code-model correspondence |
| Small protocol has bounded states and transitions | Explicit-state model checking | Explores every reachable state in the model | State-space bounds and abstraction limit the claim |

## Consent Boundary

No additional discussion is normally needed to:

- run checks already configured by the repository;
- add focused tests customary for the existing test suite;
- use existing types or assertions consistently with nearby code.

Discuss and obtain agreement before:

- adding a property-testing, symbolic-execution, SMT, or model-checking dependency;
- introducing verification-only production APIs or changing architecture;
- creating a substantial reference model or formal specification;
- running expensive or potentially long analysis;
- expanding verification beyond the requested component.

When uncertain, propose two levels: a low-cost baseline and an optional stronger check.

