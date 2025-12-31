# Advent of Code Compatibility Analysis

This document tracks the compatibility of the Donner santa-lang compiler with the
solutions in `~/Projects/advent-of-code`.

## Executive Summary

**Current Status: NOT READY for AOC solutions**

The Donner compiler successfully implements the core language features but is missing
several critical features heavily used in AOC solutions. None of the existing AOC
solutions can run successfully in their current form.

## Missing Features (Blocking)

### 1. Multiline String Literals
**Impact: ALL 26 solutions in 2022, ALL 14 in 2023, ALL 7 in 2025**
**Severity: CRITICAL**

AOC solutions use multiline strings in test blocks:
```santa
test: {
  input: "1000
2000
3000

4000"  // <-- Lexer throws "Unterminated string literal"
  part_one: 24000
}
```

**Location**: `compiler/src/main/kotlin/santa/compiler/lexer/Lexer.kt:118-119`
```kotlin
if (c == '\n') {
    throw LexingException("Unterminated string literal", start)
}
```

**Fix Required**: Support multiline strings (either raw or allow newlines in regular strings).

---

### 2. Collection Slicing with Ranges
**Impact: 23+ solutions**
**Severity: CRITICAL**

Slicing syntax `collection[start..end]` is not implemented:
```santa
let s = "hello world"
s[0..5]  // RuntimeError: String index must be Integer, got Range
```

```santa
let xs = [1, 2, 3, 4, 5]
xs[1..4]  // RuntimeError: List index must be Integer, got Range
```

**Location**: `runtime/src/main/kotlin/santa/runtime/Operators.kt:216-230`

The `index` function only handles `IntValue`, not `RangeValue`.

**LANG.txt Reference**: Section 10.1 (List slicing), Section 3.3 (String slicing)
```santa
let numbers = [1, 2, 3, 4, 5];
numbers[1..3]    // [2, 3]
numbers[2..]     // [3, 4, 5]
numbers[..3]     // [1, 2, 3]
```

---

### 3. Placeholder Expressions (`_`)
**Impact: 20+ solutions in 2022**
**Severity: CRITICAL**

Placeholder syntax for creating anonymous functions is not implemented:
```santa
let inc = _ + 1  // NotImplementedError: Placeholders not yet implemented
```

Common patterns in AOC:
```santa
filter(_ > 0)
map(_ * 2)
sort(|[_, a], [_, b]| a > b)  // Also uses destructuring
```

**Location**: `compiler/src/main/kotlin/santa/compiler/codegen/CodeGenerator.kt:378`
```kotlin
is PlaceholderExpr -> TODO("Placeholders not yet implemented")
```

---

### 4. Destructuring in Lambda Parameters
**Impact: 23 solutions**
**Severity: CRITICAL**

Lambda parameter destructuring is not supported:
```santa
[[1, 2], [3, 4]] |> map(|[a, b]| a + b)
// SyntaxError: Expected parameter name
```

AOC patterns:
```santa
fold_s([0, 0]) |[sum, count], n| [sum + n, count + 1]
iterate(|[a, b]| [b, a + b], [0, 1])
sort(|[_, a], [_, b]| a > b)
```

**LANG.txt Reference**: Section 8 shows `|[a, b], _|` syntax.

---

### 5. AOC URL Read Support (`read("aoc://...")`)
**Impact: Tests using puzzle input in ALL solutions**
**Severity: HIGH**

The `read("aoc://year/day")` function returns `nil`:
```kotlin
// runtime/src/main/kotlin/santa/runtime/Builtins.kt:1673-1676
pathStr.startsWith("aoc://") -> {
    // AOC URLs: aoc://year/day -> fetch from adventofcode.com
    // For now, return nil - CLI will provide actual implementation
    NilValue
}
```

**Workaround Possible**: Use local `.input` files with relative paths.

---

### 6. Dict Shorthand Syntax
**Impact: 1-2 solutions (day 11)**
**Severity: MEDIUM**

Dict shorthand where identifier is used as both key and value:
```santa
let items = [1, 2, 3]
#{items, "activity": 0}
// NotImplementedError: Shorthand entries in dict literals not yet implemented
```

**Location**: `compiler/src/main/kotlin/santa/compiler/codegen/CodeGenerator.kt:1303`

---

### 7. Recursive Memoization Self-Reference
**Impact: Day 12 (2023) - dynamic programming**
**Severity: MEDIUM**

While regular recursive functions work, memoized recursive self-reference fails:
```santa
let fib = memoize(|n| if n < 2 { n } else { fib(n-1) + fib(n-2) })
// ResolveError: Undefined identifier 'fib'
```

The spec shows (lang.txt line 1077-1081):
```santa
let fib = memoize |n| {
  if n < 2 { n }
  else { fib(n - 1) + fib(n - 2) }
}
```

---

## Working Features

These features work correctly and are used in AOC solutions:

- Basic literals (int, decimal, string, bool, nil)
- String multiplication: `"#" * 5` -> `"#####"`
- List multiplication: `[1, 2] * 2` -> `[1, 2, 1, 2]`
- Pipeline operator: `|>`
- Function composition: `>>`
- Trailing lambda syntax: `[1,2,3] |> map |x| { x * 2 }`
- Self-recursive functions: `let fact = |n| if n <= 1 { 1 } else { n * fact(n-1) }`
- `ints` builtin: `ints("1 -2 3 foo 4")` -> `[1, -2, 3, 4]`
- `fold_s` builtin (without destructuring params)
- `regex_match` and `regex_match_all`
- `iterate`, `repeat`, `cycle`, `take`
- `map`, `filter`, `fold`, `reduce`, etc.
- `update`, `assoc` (with explicit lambdas)
- Range expressions: `1..10`, `1..=10`, `4..`
- AOC section parsing: `input:`, `part_one:`, `part_two:`, `test:`
- Test block validation (with inline inputs)

---

## Solution-by-Solution Analysis (2022)

| Day | Multiline | Slicing | Placeholders | Destructuring | Status |
|-----|-----------|---------|--------------|---------------|--------|
| 01  | YES       | NO      | NO           | NO            | BLOCKED: multiline |
| 02  | YES       | NO      | NO           | YES           | BLOCKED: multiline, destructuring |
| 03  | YES       | YES     | NO           | YES           | BLOCKED: multiline, slicing, destructuring |
| 04  | YES       | YES     | NO           | YES           | BLOCKED: multiline, slicing, destructuring |
| 05  | YES       | YES     | NO           | YES           | BLOCKED: multiline, slicing, destructuring |
| 06  | NO*       | YES     | NO           | NO            | BLOCKED: slicing |
| 07  | YES       | YES     | NO           | YES           | BLOCKED: multiline, slicing, destructuring |
| 08  | YES       | YES     | NO           | YES           | BLOCKED: multiline, slicing, destructuring |
| 09  | YES       | NO      | NO           | YES           | BLOCKED: multiline, destructuring |
| 10  | YES       | YES     | NO           | YES           | BLOCKED: multiline, slicing, destructuring |
| 11  | YES       | YES     | NO           | YES + dict    | BLOCKED: multiline, slicing, destructuring, dict shorthand |
| 12  | YES       | YES     | NO           | YES           | BLOCKED: multiline, slicing, destructuring |
| 13  | YES       | YES     | NO           | YES           | BLOCKED: multiline, slicing, destructuring |
| ...  | ...       | ...     | ...          | ...           | ... |

*Day 6 uses single-line test inputs but requires slicing.

---

## Recommended Implementation Order

1. **Multiline Strings** (unblocks test block parsing for all solutions)
2. **Collection Slicing** (used in 23+ solutions)
3. **Destructuring in Lambda Params** (used in 23 solutions)
4. **Placeholder Expressions** (used in 20+ solutions)
5. **AOC URL Read** (enables full-input tests)
6. **Dict Shorthand** (nice-to-have)
7. **Memoize Self-Reference** (enables DP solutions like day 12)

---

## Testing Progress

| Category | Status |
|----------|--------|
| Core language (literals, operators) | PASS |
| Functions and closures | PASS |
| Built-ins (most) | PASS |
| AOC section parsing | PASS |
| Test block execution | BLOCKED (multiline strings) |
| 2022 solutions | 0/26 passing |
| 2023 solutions | 0/14 passing |
| 2025 solutions | 0/7 passing |

---

## How to Test

```bash
# Test a simple script
./gradlew cli:run -q --args="/path/to/script.santa"

# Run tests in a solution file (requires multiline string support)
./gradlew cli:run -q --args="-t /path/to/solution.santa"
```

---

*Generated: 2025-12-31*
*Donner version: Phase 15 complete*
