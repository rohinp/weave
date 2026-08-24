# Pragmatic Engineering Heuristics

Load only the sections relevant to the task. Treat these as decision aids, not mandatory architecture.

## Domain Modeling

- Use the same domain terms in conversations, tests, APIs, and code.
- Resolve ambiguous vocabulary before encoding it in a public interface or persistent model.
- Place invariants near the data and behavior they protect.
- Separate domain decisions from transport, persistence, UI formatting, and application orchestration when doing so makes ownership clearer.
- Introduce entities, value objects, policies, services, aggregates, or repositories only when their semantics are present in the problem.
- Keep the model limited to behavior the software currently needs.

## Clean Design

- Make names express domain and operational intent.
- Keep control flow, dependencies, and side effects visible.
- Prefer composition to inheritance unless callers genuinely rely on subtype substitution.
- Let duplication reveal the right abstraction before consolidating it.
- Preserve causes and actionable context when handling errors.
- Reserve comments for constraints, rationale, or context the code cannot express.

## SOLID, YAGNI, and KISS

- Use **SRP** when unrelated reasons to change are tangled together.
- Use **OCP** when multiple known variations repeatedly modify stable code.
- Use **LSP** to detect implementations that violate their callers' behavioral expectations.
- Use **ISP** when clients are forced to depend on capabilities they do not use.
- Use **DIP** at boundaries where concrete infrastructure harms testing, replacement, or domain clarity.
- Use **YAGNI** to defer speculative features, extension points, and configuration.
- Use **KISS** to select the least complicated design that satisfies current behavior and constraints.
- Do not add an abstraction solely to demonstrate a principle.

## Testing and TDD

- Test behavior visible through a stable boundary rather than private implementation details.
- Use red-green-refactor when a failing test can express the requirement or defect clearly.
- Add characterization tests before changing poorly understood legacy behavior.
- Use meaningful domain examples and cover relevant boundaries, invalid inputs, repeated calls, ordering, and concurrency.
- Keep the verification scope proportional to the affected contract and blast radius.

## Refactoring

- Treat refactoring as behavior-preserving structural improvement.
- Prefer small transformations such as rename, extract, inline, move, split, or replace a primitive with a domain concept.
- Use code smells as investigation prompts, not automatic instructions to rewrite.
- Stop when the current responsibility and behavior are clear enough for the task.
- Avoid combining broad cleanup with urgent behavior changes.

## Design Patterns

- Use a pattern only when it names and simplifies a real collaboration or variation point.
- Consider Strategy for interchangeable policy, Adapter for external interfaces, Decorator for layered runtime behavior, Command for transportable invocation, and Factory when construction details leak.
- Prefer direct code when a pattern adds more indirection than clarity.

## Algorithms and Data Structures

- Establish input size, distribution, ordering, mutation rate, latency, and memory constraints before optimizing.
- Prefer standard collections and algorithms with obvious correctness properties.
- Check empty input, duplicates, missing values, limits, overflow, stable ordering, ties, and concurrent access when applicable.
- Prefer readable complexity unless measurement or explicit constraints justify a more intricate approach.

## Delivery Decisions

- Spend more design effort on expensive-to-reverse decisions such as public APIs, persistent data, security boundaries, and distributed contracts.
- Keep reversible decisions lightweight.
- Use thin vertical slices to learn before committing to broad architecture.
- Match the existing repository first; propose a new direction only with a concrete benefit, migration cost, and risk assessment.
