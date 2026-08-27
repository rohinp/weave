# Review and Verification Prompts

Use only the sections relevant to the task. Do not paste these checklists into the final response.

## Before Editing

- What observable behavior must change or remain unchanged?
- What code, test, or boundary currently owns that behavior?
- Which repository instructions and conventions apply?
- What assumptions could materially change the solution?
- What is the smallest verifiable outcome?

## Design

- Are important rules and invariants named and owned clearly?
- Does the design reuse an established local pattern?
- Is each new abstraction justified by current behavior or a known variation?
- Are side effects and infrastructure dependencies visible?
- Is a simpler compatible design available?

## Tests

- Does a test or reproduction demonstrate the requirement or defect?
- Do tests assert behavior rather than implementation details?
- Are relevant boundaries and failure cases covered?
- Would the test fail for the intended reason if the behavior regressed?
- Is the verification scope proportional to the blast radius?

## Refactoring

- Is the intended change behavior-preserving?
- Is unclear behavior protected before it is moved?
- Are behavior changes separated from mechanical cleanup where practical?
- Does each extraction or abstraction reduce a real change hazard?
- Has the refactor stopped at the useful boundary of the task?

## Code Review

- Could this change introduce incorrect behavior, data loss, security exposure, races, or compatibility breaks?
- Are error paths and partial failures handled consistently with the repository?
- Are public APIs, schemas, or persistent formats changed deliberately?
- Is there unnecessary scope, complexity, or drive-by cleanup?
- Are findings precise, prioritized, and supported by file and line references?

## Final Verification

- Does every changed line trace to the requested outcome?
- Were newly unused imports, variables, helpers, and files removed?
- Did the narrow checks pass, and were broader checks needed?
- Does the diff preserve local style and avoid unrelated formatting changes?
- Does the final response state the outcome, evidence, and relevant residual risk?
