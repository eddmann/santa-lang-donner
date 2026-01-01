# AOC Solutions Analysis Report

## Summary

Tested **61 .santa files** across years 2018, 2022, 2023, and 2025.

| Year | Files | Passed | Failed | Timeouts |
|------|-------|--------|--------|----------|
| 2018 | 14    | 5      | 6      | 3        |
| 2022 | 27    | 18     | 7      | 2        |
| 2023 | 14    | 6      | 6      | 2        |
| 2025 | 7     | 3      | 4      | 0        |
| **Total** | **62** | **32** | **23** | **7** |

---

## Categorized Issues

### 1. CRITICAL: Rest Pattern Elements After `..` Not Processed

**Affected Files:** `aoc2025_day05.santa`
**Error:** `Error: Undefined variable: prev_hi`

**Root Cause:** In `CodeGenerator.kt:1254-1255`, when processing a `RestPattern` in a list pattern, the code does `break` which exits the loop, skipping any elements after the rest pattern.

For pattern `[..init, [prev_lo, prev_hi]]`:
- `..init` is processed
- `break` is called
- `[prev_lo, prev_hi]` is **never** processed - variables never bound

**Fix Location:** `compiler/src/main/kotlin/santa/compiler/codegen/CodeGenerator.kt:1237-1256` (and similar at line 1394-1411)

**Fix Required:**
- Calculate `restEnd = size - (count of elements after rest)`
- Slice `init` from `0..restEnd`
- Continue processing remaining elements from `restEnd` onwards

---

### 2. CRITICAL: Builtin Functions Not Shadowed by Local Bindings in Calls

**Affected Files:** `aoc2018_day01.santa` (part_two tests 4-7)
**Error:** `scan: folder must be a Function`

**Root Cause:** In `CodeGenerator.kt:1943`, when compiling a `CallExpr`, the code checks:
```kotlin
if (callee is IdentifierExpr && callee.name in BUILTIN_FUNCTIONS) {
    compileBuiltinCall(callee.name, expr.arguments)
```

This does NOT check if there's a local binding for the name first. So even when a local `scan` function is defined, calls go to the builtin.

**Fix Location:** `compiler/src/main/kotlin/santa/compiler/codegen/CodeGenerator.kt:1943`

**Fix Required:**
```kotlin
val hasLocalBinding = callee is IdentifierExpr && lookupBinding(callee.name) != null
if (callee is IdentifierExpr && callee.name in BUILTIN_FUNCTIONS && !hasLocalBinding) {
```

---

### 3. MISSING: `evaluate` Builtin Function

**Affected Files:** `aoc2022_day13.santa`
**Error:** `Error: Undefined variable: evaluate`

**Root Cause:** The `evaluate` function (to parse JSON-like list/integer literals from strings) doesn't exist in Builtins.

**Example Usage:**
```santa
let parse_packets = lines >> filter(_ != "") >> map(evaluate)
```

**Fix Required:** Add `fun evaluate(value: Value): Value` to `Builtins.kt` that parses:
- Integer literals: `42` → `IntValue(42)`
- List literals: `[1, 2, [3, 4]]` → `ListValue(...)`

---

### 4. MISSING: `intersection` with Two Arguments

**Affected Files:** `aoc2023_day03.santa`
**Error:** `NoSuchMethodError: intersection(Value, Value)`

**Root Cause:** The code calls `intersection(a, b)` but `Builtins.intersection` only accepts a single argument (a list of collections).

**Fix Location:** `runtime/src/main/kotlin/santa/runtime/Builtins.kt:1470`

**Fix Required:** Add two-argument overload:
```kotlin
@JvmStatic
fun intersection(a: Value, b: Value): Value { ... }
```

---

### 5. MISSING: Range Indexing Support

**Affected Files:** `aoc2022_day18.santa`
**Error:** `Cannot index Range`

**Root Cause:** In `Operators.index()`, there's no case for indexing into a `RangeValue`. Code does `xs[0]` where `xs` is a range.

**Fix Location:** `runtime/src/main/kotlin/santa/runtime/Operators.kt:222-253`

**Fix Required:** Add case for `RangeValue`:
```kotlin
is RangeValue -> when (index) {
    is IntValue -> {
        val i = index.value.toInt()
        val result = target.getStart() + (i * target.getStep())
        if (target.contains(result)) IntValue(result) else NilValue
    }
    // ... range slicing
}
```

---

### 6. SYNTAX: Unbounded Range in Index Position

**Affected Files:** `aoc2018_day04.santa`
**Error:** `SyntaxError: Expected ')' after arguments` at `action[3][1..]`

**Root Cause:** Parser doesn't support unbounded ranges `[1..]` inside index brackets. The `..` with no end needs special handling.

**Fix Location:** `compiler/src/main/kotlin/santa/compiler/parser/Parser.kt`

---

### 7. SYNTAX: Trailing Commas in Collection Literals

**Affected Files:** `aoc2022_day23.santa`
**Error:** `SyntaxError: Expected expression` at trailing comma

**Root Cause:** Set/list literals don't allow trailing commas:
```santa
{
    [y - 1, x - 1],   // <-- trailing comma causes error
}
```

**Fix Location:** `compiler/src/main/kotlin/santa/compiler/parser/Parser.kt`

---

### 8. RESOLVER: Forward References in Memoized Functions

**Affected Files:** `aoc2023_day12.santa`, `aoc2023_day14.santa`, `aoc2018_day11.santa`
**Errors:**
- `Undefined identifier 'arrangements'`
- `Undefined identifier 'horizontal_tilt'`
- `Undefined variable: max_point`

**Root Cause:** When function A calls memoized function B, and B is defined after A, the resolver fails. Also, mutable variables defined in outer scope may not be visible.

**Fix Location:** `compiler/src/main/kotlin/santa/compiler/resolver/Resolver.kt`

---

### 9. RUNTIME: `all?` on Dictionary

**Affected Files:** `aoc2018_day07.santa`
**Error:** `all?: expected bounded collection, got Dictionary`

**Root Cause:** `all?` doesn't support `DictValue` as input.

**Fix Location:** `runtime/src/main/kotlin/santa/runtime/Builtins.kt` - `all?` function

---

### 10. RUNTIME: `size` on LazySequence

**Affected Files:** `aoc2023_day13.santa`
**Error:** `size: expected collection, got LazySequence`

**Root Cause:** `size` doesn't work on `LazySequenceValue` (would need to consume it).

**Note:** This might be intentional - lazy sequences are unbounded. The solution should call `list()` first.

---

### 11. RUNTIME: `set` on LazySequence

**Affected Files:** `aoc2018_day03.santa`
**Error:** `set: element LazySequence is not hashable`

**Root Cause:** A lazy sequence is being used as an element in a set, but it's not hashable.

---

### 12. RUNTIME: Cast Error LazySequence to ListValue

**Affected Files:** `aoc2018_day06.santa`
**Error:** `LazySequenceValue cannot be cast to ListValue`

**Root Cause:** Code expects a list but receives a lazy sequence. Need to call `list()` to materialize.

---

### 13. RUNTIME: Compose with LazySequence

**Affected Files:** `aoc2023_day07.santa`
**Error:** `Compose left-hand side must be a Function, got LazySequence`

**Root Cause:** Pipeline/compose evaluation ordering issue where a lazy sequence ends up on the left of `>>`.

---

### 14. RUNTIME: `fold`/`fold_s` Returning Function

**Affected Files:** `aoc2025_day01.santa`, `aoc2025_day02.santa`
**Error:** Tests fail - `actual: <function>` instead of expected value

**Root Cause:** The pattern `fold(init) |acc, x| { ... } >> second` creates a partial application issue. The `fold(init)` with two arguments should return a curried function that takes the collection, but something is going wrong with the composition.

This needs investigation into how partial application interacts with `>>`.

---

### 15. RUNTIME: Index Out of Bounds

**Affected Files:** `aoc2022_day16.santa`
**Error:** `Index: 1, Size: 1`

**Root Cause:** Code tries to access index 1 of a 1-element list (valid indices: 0 only).

---

### 16. TIMEOUTS (7 files)

Files that exceeded 30s timeout:
- `aoc2018_day12.santa`
- `aoc2018_day13.santa`
- `aoc2018_day14.santa`
- `aoc2022_day14.santa`
- `aoc2022_day17.santa`
- `aoc2023_day08.santa`
- `aoc2023_day09.santa`

These may be:
- Performance issues in the runtime
- Infinite loops due to bugs
- Genuinely slow algorithms

---

## Priority Order for Fixes

### P0 (Blocking Many Solutions)
1. **Rest pattern elements after `..`** - Blocks pattern matching with rest+suffix
2. **Builtin shadowing in calls** - Blocks local function definitions
3. **Trailing commas** - Easy syntax fix
4. **Unbounded range syntax** - Easy syntax fix

### P1 (Missing Features)
5. **`evaluate` function** - Needed for Day 13 2022
6. **`intersection(a, b)` overload** - Needed for Day 3 2023
7. **Range indexing** - Needed for Day 18 2022

### P2 (Edge Cases)
8. Forward references in memoized functions
9. `all?` on Dictionary
10. Fold/compose interaction bugs

### P3 (Performance)
11. Investigate timeout causes

---

## Files to Modify

| File | Changes |
|------|---------|
| `compiler/.../codegen/CodeGenerator.kt` | Fix rest patterns, fix builtin shadowing |
| `compiler/.../parser/Parser.kt` | Trailing commas, unbounded range syntax |
| `compiler/.../resolver/Resolver.kt` | Forward references |
| `runtime/.../Builtins.kt` | Add `evaluate`, fix `intersection`, `all?` on dict |
| `runtime/.../Operators.kt` | Range indexing |
