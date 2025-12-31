# AGENTS.md

## Purpose

This repo builds **Donner**, a direct JVM bytecode compiler for santa-lang.
Follow the project guidelines in:
- `/Users/edd/Projects/agent-guidelines/foundation/agent-philosophy.md`
- `/Users/edd/Projects/agent-guidelines/foundation/code-philosophy.md`
- `/Users/edd/Projects/agent-guidelines/practices/*`

## Non-Negotiables

- **No interpreter** and **no custom VM**. Compile directly to JVM bytecode.
- **Kotlin + ASM** for compiler implementation.
- **ICU4J** for grapheme-cluster string indexing.
- **Persistent collections**: `kotlinx.collections.immutable`.
- **LANG.txt is the source of truth** for all behavior.

## Workflow

- Always start by reading `PLAN.md` to find the next unchecked release gate.
- Use **classical TDD** (RED → GREEN → REFACTOR).
- Keep functions small, descriptive, and predictable.
- Validate error vs nil behavior per LANG.txt §15.5.
- Update `PLAN.md` gates as they are completed.
- After each completed gate, **commit** using Conventional Commits and record the phase/step in the commit message.

## Git Discipline

- Use **Conventional Commits** (e.g., `feat:`, `fix:`, `test:`, `refactor:`).
- Commit after each completed release gate.
- Commit message **must** include phase and gate in the scope.
  - Required format: `type(phase-X-gate-Y): short description`
  - Example: `feat(phase-1-gate-2): lex comments`
- **Git history is the source of truth for progress**. Do not squash commits that would erase phase/gate history.

## Architecture

```
Source -> Lexer -> Parser -> Desugar -> Resolver -> Bytecode Gen (ASM) -> .class -> JVM
                                         |
                                         +-> Runtime library (Values, Collections, Builtins)
```

## Testing

- Test through public APIs with real collaborators.
- Avoid mocks except for external I/O boundaries.
- Prefer snapshot tests for lexer/parser, behavioral tests for runtime and codegen.
