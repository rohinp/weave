# Language Guidance

Prefer each ecosystem's established style. Equivalent syntax is not required, and the guarantees are not identical.

## Python

- Use normal exceptions or validation results at public boundaries according to repository style.
- Use type annotations to improve tooling, while acknowledging that Python does not enforce them at runtime.
- Use dataclasses or small validated domain objects when they clarify invariants.
- Consider Hypothesis for property-based and stateful testing.
- Consider mypy for opt-in static checking and CrossHair for supported symbolic contract analysis.
- Consider Z3 only for a compact, precisely modeled constraint problem.

Dynamic Python cannot generally make invalid states unrepresentable. Callers may bypass annotations, deserialization may create unchecked data, and runtime mutation may violate assumptions. Validate at untrusted boundaries and avoid claiming compile-time guarantees.

## Scala 3

- Prefer opaque types, enums, sealed hierarchies, smart constructors, and exhaustive pattern matching when they encode real domain restrictions.
- Prefer `Either` or the repository's established result type for expected domain failure.
- Use `require` or assertions for programmer contracts when that matches local style; do not force all failures into exceptions.
- Consider ScalaCheck with the repository's test framework for generated properties and command/state models.
- Treat compiler checks as real static evidence but not proof of arbitrary business logic.
- Consider Stainless only for a supported, deliberately selected subset and after user agreement.

## Kotlin

- Prefer value classes, sealed classes or interfaces, data classes, and controlled constructors for domain restrictions.
- Use the repository's established failure style, commonly nullable results, sealed results, or exceptions at appropriate boundaries.
- Consider Kotest Property when the project already uses Kotest or the user agrees to add it.
- Use exhaustive `when` expressions and strict compiler settings where practical.
- Do not imply that Kotlin's ordinary compiler symbolically verifies business logic.

## Cross-Language Limits

- Static types protect checked code, not necessarily network input, reflection, persistence, or foreign interfaces.
- Runtime contracts detect violations but do not prevent all invalid construction.
- JVM `Int` and Python integers have different overflow behavior; model the production numeric semantics.
- Keep the specification independent enough that a test or model is unlikely to duplicate the implementation defect.

