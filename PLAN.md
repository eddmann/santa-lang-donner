# Donner: Santa-Lang JVM Bytecode Compiler Plan

A direct JVM bytecode compiler for santa-lang, implemented in Kotlin with ASM, targeting the latest Java LTS (21).

## Architecture (MANDATORY)

This is a direct JVM bytecode compiler. There is no AST interpreter and no custom bytecode VM.

```
Source -> Lexer -> Parser -> Desugar -> Resolver -> Bytecode Gen -> .class -> JVM
                                            |
                                            +-> Runtime Library (Values, Collections, Builtins)
```

- Bytecode is generated with ASM and executed by the JVM.
- Runtime library provides value model, persistent collections, built-ins, and I/O.
- All behavior must match LANG.txt (source of truth).

### Forbidden Approaches
- No tree-walking interpreter
- No custom bytecode VM
- No transpilation to another language

---

## Source of Truth

`lang.txt` is the authoritative specification. Every phase includes:
- LANG.txt section references
- Tests derived from specification examples
- Validation of runtime error vs nil behavior

---

## Key Decisions

- Persistent collections library: `kotlinx.collections.immutable`

---

## Project Structure (Proposed)

```
/
├── PLAN.md
├── continue.sh
├── lang.txt
├── compiler/
│   ├── build.gradle.kts
│   └── src/
│       ├── main/kotlin/...
│       └── test/kotlin/...
├── runtime/
│   ├── build.gradle.kts
│   └── src/
│       ├── main/kotlin/...
│       └── test/kotlin/...
└── cli/
    ├── build.gradle.kts
    └── src/main/kotlin/...
```

---

## LANG.txt Coverage Checklist

| Section | Description | Phase |
| --- | --- | --- |
| 2 | Lexical structure | Phase 1 |
| 3 | Type system, hashability | Phases 5-6 |
| 4 | Operators and precedence | Phases 2, 7 |
| 5 | Variables and bindings | Phases 3-4 |
| 6 | Expressions | Phases 2, 7 |
| 7 | Control flow | Phase 8 |
| 8 | Functions and closures | Phase 8 |
| 9 | Pattern matching | Phase 3, 8 |
| 10 | Collections | Phases 5-6 |
| 11 | Built-in functions | Phases 9-10 |
| 12 | AOC runner | Phase 11 |
| 13 | External functions | Phase 11 |
| 14 | Semantics | Phases 4-8 |
| 15 | Implementation notes | Phase 12 |
| Appendix A | Grammar | Phases 1-3 |
| Appendix B | Builtins | Phases 9-10 |
| Appendix C | Precedence | Phase 2 |
| Appendix D | Examples | Phase 13 |

---

## TDD Workflow (MANDATORY)

For every feature:
1. RED: write a failing test from LANG.txt
2. GREEN: implement minimal code to pass
3. REFACTOR: clean up while green
4. Update release gate checkboxes

---

## Phase 0: Scaffolding

Goal: establish Kotlin/Gradle structure and test harness.

Release Gate 0
- [x] Gradle multi-module build (compiler/runtime/cli)
- [x] Test runner wired (JUnit + snapshots)
- [x] CI-friendly commands (`./gradlew test`)

---

## Phase 1: Lexer

LANG.txt: Section 2, Appendix A

Release Gate 1
- [x] All token types lexed correctly
- [x] Comments ignored
- [x] Line/column spans accurate
- [x] Snapshot tests green

---

## Phase 2: Parser - Expressions

LANG.txt: Sections 4, 6, 8, 14.5, Appendix A/C

Release Gate 2
- [x] Pratt parser handles precedence table exactly
- [x] Infix calls, ranges, pipeline, composition
- [x] Literals and collection expressions
- [x] Snapshot tests green

---

## Phase 3: Parser - Statements, Patterns, Sections

LANG.txt: Sections 5, 7, 9, 12, Appendix A

Release Gate 3
- [x] let/mut, return, break
- [x] Destructuring patterns (rest, nested, wildcard)
- [x] Match expressions and guards
- [x] AOC sections parsed
- [x] Empty {} disambiguation (set vs block)
- [x] Dict shorthand parsing
- [x] Snapshot tests green

---

## Phase 4: Resolver and Semantic Validation

LANG.txt: Sections 5, 7, 14.6, 15.5

Release Gate 4
- [x] Lexical scoping and shadowing rules
- [x] Built-in function names are protected
- [x] Invalid return/break contexts raise runtime errors
- [x] Resolver tests green

---

## Phase 5: Value Model

LANG.txt: Section 3, 14.1

Release Gate 5
- [x] Value types modeled (int, decimal, string, bool, nil, list, set, dict, lazy seq, function)
- [x] Truthy/falsy rules correct
- [x] Equality and hashing semantics correct
- [x] Grapheme-cluster indexing uses ICU4J

---

## Phase 6: Collections and Lazy Sequences

LANG.txt: Sections 3.4-3.9, 10

Release Gate 6
- [x] Persistent List/Set/Dict support
- [x] Range behavior (inclusive, exclusive, unbounded)
- [x] Lazy sequence operations (iterate, repeat, cycle, zip)
- [x] Hashability rules enforced

---

## Phase 7: Bytecode Generation - Core

LANG.txt: Sections 4, 6, 14

Release Gate 7
- [x] Bytecode for literals, locals, blocks
- [x] Arithmetic, comparison, logical ops
- [x] Indexing and slicing
- [x] Function call conventions
- [x] Compile+run tests for core expressions

---

## Phase 8: Control Flow and Functions

LANG.txt: Sections 7, 8, 9

Release Gate 8
- [x] if / if-let / match execution
- [x] Lambdas and closures with captured variables
- [x] Rest parameters and spread (completed in Phase 15)
- [x] Pipeline and composition behavior (completed in Phase 14)
- [x] Tail-call optimization for self-recursion (loop transformation)

---

## Phase 9: Built-ins (Core)

LANG.txt: Section 11 (core set)

Release Gate 9
- [x] Numeric, string, and collection core built-ins
- [x] Error vs nil behavior matches spec
- [x] Built-in tests green

---

## Phase 10: Built-ins (Complete)

LANG.txt: Appendix B

Release Gate 10
- [x] All remaining built-ins implemented
- [x] Regex and md5 built-ins validated
- [x] Built-in test suite green
- [N/A] evaluate built-in (not implementing - requires full compiler embedding)

---

## Phase 11: AOC Runner and CLI

LANG.txt: Section 12, 13

Release Gate 11
- [x] Sections executed correctly
- [x] Test blocks validated
- [x] Script mode works
- [x] read/puts/env external functions
- [x] CLI commands and exit codes

---

## Phase 12: Error Handling and Reporting

LANG.txt: Section 15.5

Release Gate 12
- [x] Errors include spans and stack traces
- [x] Runtime error vs nil behavior correct
- [x] Error messages clear and actionable

---

## Phase 13: Benchmarks and Optimization

LANG.txt: Section 15.4

Release Gate 13
- [x] Bytecode optimizations where safe
- [x] Benchmark harness in place
- [x] Example programs run within expected time

---

## Phase 14: Integration and Polish

LANG.txt: Appendix D

Release Gate 14
- [x] Pipeline (|>) and composition (>>) operators implemented
- [x] Partial application for builtins (e.g., `map(|x| x * 2)`)
- [x] Builtins as first-class function values
- [x] Example program tests (AOC style, word frequency, pipelines)
- [x] Full test suite green
- [x] Return statements (exception-based early return from functions)
- [x] Break statements (exception-based early exit from iterations)
- [x] Range expressions (exclusive, inclusive, unbounded)
- [x] Recursive function self-reference

---

## Phase 15: Spread and Rest Parameters

LANG.txt: Sections 4.9, 8.5, 8.6

Release Gate 15
- [x] Spread in list literals (`[1, ..xs, 2]`)
- [x] Spread in set literals (`{1, ..xs, 2}`)
- [x] Rest parameters in functions (`|head, ..remaining|`)
- [x] Spread in function call arguments (`f(1, ..args, 2)`)
- [x] Full test suite green

---

## Phase 16: AOC Compatibility

Goal: Run real Advent of Code solutions from `~/Projects/advent-of-code`.

See `AOC_COMPATIBILITY.md` for detailed analysis.

LANG.txt: Sections 2.4, 6.4, 8.3, 9, 10, 13

Release Gate 16
- [x] Multiline string literals (allow `\n` in strings, or add raw string syntax)
- [x] Collection slicing with ranges (`xs[1..3]`, `s[0..5]`)
- [x] Placeholder expressions (`_ + 1` → `|x| x + 1`)
- [x] Destructuring in lambda parameters (`|[a, b]| a + b`)
- [ ] Dict shorthand codegen (`#{items, op}` where `items`/`op` are identifiers)
- [ ] AOC URL read support (`read("aoc://2022/1")` → fetch/cache puzzle input)
- [ ] Memoize with recursive self-reference (`let fib = memoize |n| ... fib(n-1) ...`)
- [ ] AOC 2022 solutions pass (26 days)
- [ ] AOC 2023 solutions pass (14 days)
- [ ] AOC 2025 solutions pass (7 days)

### Implementation Notes

**Multiline strings** (`Lexer.kt:118`):
- Remove the `if (c == '\n') throw` check
- Track line numbers correctly within string

**Collection slicing** (`Operators.kt:216`):
- Handle `RangeValue` as index in `index()` function
- Call `ListValue.slice()` / add `StringValue.slice()`

**Placeholders** (`CodeGenerator.kt:378`):
- Convert `PlaceholderExpr` in binary ops to lambda wrapper
- Track placeholder positions for multi-placeholder expressions

**Lambda destructuring** (`Parser.kt:323`):
- Parse pattern instead of just identifier after `|`
- Generate destructuring code in lambda body

---

## Notes on JVM Bytecode Strategy

- Use ASM with Java 21 bytecode level.
- Each santa file compiles to a main class with static entry.
- Functions compile to synthetic classes implementing `SantaFn`.
- Captures stored as fields; call via `invoke` method.
- Use line number tables for error spans.
