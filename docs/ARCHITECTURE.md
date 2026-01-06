# Donner Architecture

Donner is a JVM bytecode compiler for santa-lang, written in Kotlin. It compiles santa-lang source code directly to JVM bytecode using the ASM library, with no intermediate interpreter or virtual machine layer.

## High-Level Pipeline

```
Source Code
    │
    ▼
┌─────────┐
│  Lexer  │  Tokenizes source into keywords, operators, literals
└────┬────┘
     │ List<Token>
     ▼
┌─────────┐
│ Parser  │  Builds Abstract Syntax Tree (Pratt parser)
└────┬────┘
     │ Program (AST)
     ▼
┌───────────────────────┐
│  Desugaring (3 passes)│  AST transformations
│  1. PlaceholderDesugar│  _ + 1 → |$0| $0 + 1
│  2. PipelineDesugar   │  Keeps |> as binary op
│  3. PatternParamDesugar│ |[a,b]| → |$0| { let [a,b] = $0; ... }
└────────┬──────────────┘
         │ Program (desugared AST)
         ▼
┌──────────┐
│ Resolver │  Validates scope and identifier references
└────┬─────┘
     │ (validated AST)
     ▼
┌──────────────┐
│ CodeGenerator│  Generates JVM bytecode using ASM
└────┬─────────┘
     │ CompiledScript (ByteArray + lambda classes)
     ▼
┌──────────────────┐
│ ScriptClassLoader│  Loads bytecode into JVM
└────┬─────────────┘
     │
     ▼
   JVM JIT compiles on first execution
```

## Component Details

### Lexer (`compiler/src/main/kotlin/santa/compiler/lexer/`)

The lexer tokenizes source code into a stream of tokens. Key features:

- **Token types**: Keywords, operators, literals (int, decimal, string), identifiers
- **Position tracking**: Each token carries source position (line, column) and span information
- **Multiline strings**: Supports heredoc-style multiline string literals
- **Underscore separators**: Numeric literals allow underscores for readability (`1_000_000`)

Files:
- `Lexer.kt` - Main tokenizer
- `Token.kt` - Token data class with type and lexeme
- `TokenType.kt` - Enumeration of all token types
- `SourcePosition.kt` / `Span.kt` - Source location tracking

### Parser (`compiler/src/main/kotlin/santa/compiler/parser/`)

The parser builds an Abstract Syntax Tree using a Pratt parser (top-down operator precedence). Key features:

- **Expression-oriented**: Everything is an expression that returns a value
- **Operator precedence**: Handled elegantly via binding power in Pratt parsing
- **Pattern matching**: Supports destructuring patterns in let bindings and match arms
- **Runner DSL**: Parses `input:`, `part_one:`, `part_two:`, and `test:` sections

Files:
- `Parser.kt` - Main parser implementation
- `Expr.kt` - AST node definitions (expressions, statements, patterns)
- `ExprRenderer.kt` - AST pretty-printing for debugging

### Desugaring (`compiler/src/main/kotlin/santa/compiler/desugar/`)

Three desugaring passes transform the AST before code generation:

#### 1. PlaceholderDesugarer

Transforms placeholder expressions (`_`) into lambda expressions:

```santa
# Before
_ + 1
10 - _
_ / _
filter(_ > 5)

# After
|$0| $0 + 1
|$0| 10 - $0
|$0, $1| $0 / $1
filter(|$0| $0 > 5)
```

Also transforms operator references (`<`, `+`, etc.) into binary lambdas:
```santa
# Before
sort(<)

# After
sort(|$0, $1| $0 < $1)
```

#### 2. PipelineDesugarer

Pipelines are NOT desugared to function calls. Instead, they remain as binary expressions and are handled at runtime by `Operators.pipeline`. This allows pipelines to work correctly with:

- Builtins like `x |> map(f)` where `map(f)` returns a partially applied builtin
- User functions where the right-hand side is evaluated to produce a callable

#### 3. PatternParamDesugarer

Transforms pattern parameters in lambdas into regular parameters with destructuring let bindings:

```santa
# Before
|[a, b]| a + b
|[x, y], z| x + y + z

# After
|$arg0| { let [a, b] = $arg0; a + b }
|$arg0, z| { let [x, y] = $arg0; x + y + z }
```

This allows the existing let destructuring codegen to handle pattern matching.

### Resolver (`compiler/src/main/kotlin/santa/compiler/resolver/`)

The resolver performs lexical scoping analysis in two passes:

**Pass 1: Declaration Hoisting**
- Forward-declares section names (`input`, `part_one`, `part_two`)
- Forward-declares top-level function bindings (enabling mutual recursion)

**Pass 2: Resolution**
- Validates all identifier references exist in scope
- Tracks breakability context (for `break` validation in iteration builtins)
- Validates `return` is only used inside functions

The resolver maintains a stack of scopes, where each scope is a set of declared names. Protected names (builtins) are checked but can be shadowed.

### Code Generator (`compiler/src/main/kotlin/santa/compiler/codegen/`)

The code generator produces JVM bytecode using ASM 9.7. This is the largest component in the compiler.

#### Class Structure

Each compiled program generates:
- **Main script class**: A synthetic class (`santa/Script1`, `santa/Script2`, etc.) with a static `execute()` method
- **Lambda inner classes**: Each lambda expression generates an inner class extending `FunctionValue`

#### Key Components

**ClassGenerator**: Orchestrates bytecode generation for the main script class
- Generates the `execute()` method that runs the program
- Handles forward-reference boxing for mutually recursive functions
- Delegates lambda compilation to inner class generation

**ExpressionGenerator**: Compiles expressions to bytecode
- Each expression leaves exactly one `Value` on the operand stack
- Manages local variable slots and scopes
- Handles boxing for mutable captured variables

**LambdaExpressionGenerator**: Specialized generator for lambda bodies
- Extends `ExpressionGenerator` with lambda-specific features
- Manages parameter access (from `List<Value>` argument)
- Handles captured variable access (from fields)

**TailRecursiveLambdaExpressionGenerator**: Handles TCO compilation
- Wraps function body in a loop
- Tail calls become: evaluate args, store in parameter slots, `GOTO loopStart`
- Non-tail calls compile normally

#### Custom ClassWriter

`SantaClassWriter` extends ASM's `ClassWriter` to handle lambda class resolution:
- When ASM computes stack frames, it needs common superclasses
- Lambda classes aren't loaded into JVM yet, so we tell ASM they all extend `FunctionValue`

## In-Memory Compilation

Donner never writes `.class` files to disk. The compilation model:

```kotlin
class CompiledScript(
    private val mainClassName: String,       // e.g., "santa.Script1"
    private val mainBytecode: ByteArray,     // Raw bytecode for main class
    private val lambdaClasses: Map<String, ByteArray>  // name -> bytecode for each lambda
)
```

Execution uses a custom `ScriptClassLoader`:

```kotlin
class ScriptClassLoader(...) : ClassLoader(...) {
    override fun findClass(name: String): Class<*> {
        return when {
            name == mainClassName -> defineClass(name, mainBytecode, 0, mainBytecode.size)
            name in lambdaClasses -> {
                val bytecode = lambdaClasses[name]!!
                defineClass(name, bytecode, 0, bytecode.size)
            }
            else -> super.findClass(name)
        }
    }
}
```

This enables:
- Single-pass compile-then-run model
- No temporary files or cleanup required
- JVM JIT compilation when classes are first loaded

## Value System (`runtime/src/main/kotlin/santa/runtime/value/`)

All runtime values implement the sealed `Value` interface:

```kotlin
sealed interface Value {
    fun isTruthy(): Boolean    // For conditionals
    fun isHashable(): Boolean  // For Set/Dict membership
    fun typeName(): String     // For error messages
}
```

### Value Types

| Type | Kotlin Class | Description |
|------|--------------|-------------|
| Integer | `IntValue` | 64-bit signed long |
| Decimal | `DecimalValue` | 64-bit double |
| String | `StringValue` | UTF-8 with grapheme cluster indexing |
| Boolean | `BoolValue` | Singleton TRUE/FALSE |
| Nil | `NilValue` | Singleton object |
| List | `ListValue` | Persistent list (`PersistentList<Value>`) |
| Set | `SetValue` | Persistent set (`PersistentSet<Value>`) |
| Dictionary | `DictValue` | Persistent map (`PersistentMap<Value, Value>`) |
| Range | `RangeValue` | Lazy integer range (bounded or unbounded) |
| LazySequence | `LazySequenceValue` | Lazy sequence with generator function |
| Function | `FunctionValue` | Abstract base for callable values |

### Persistent Collections

Donner uses `kotlinx-collections-immutable` for persistent data structures:
- All collections are immutable by default
- Structural sharing for efficient "updates"
- O(log32 N) access for most operations

### Grapheme Cluster Indexing

String indexing uses ICU's `BreakIterator` for proper Unicode handling:
- Emoji like "👨‍👩‍👧‍👦" count as one character (grapheme cluster)
- Negative indices count from end
- Slicing preserves grapheme boundaries

## Lambda Compilation

Each lambda expression generates an inner class:

```kotlin
// Santa: |x, y| x + y

// Generated: santa/Script1$Lambda1 extends FunctionValue
class Lambda1(/* captures */) : FunctionValue(arity = 2) {
    // Fields for captured variables
    private val capturedVar1: Value
    private val capturedVar2: Value  // or Value[] if boxed

    override fun invoke(args: List<Value>): Value {
        // Extract parameters from args list
        val x = args[0]
        val y = args[1]
        // Compile body
        return Operators.add(x, y)
    }
}
```

### Captures

Variables captured from enclosing scope become constructor parameters and fields:
- **Simple captures**: Stored directly as `Value` fields
- **Mutable captures**: Stored as `Value[1]` array (box) for sharing mutations
- **Self-references**: For recursive functions, stored via `FunctionValue.selfRef` field

### Boxing for Mutable Capture

When a mutable variable is captured by a closure, it's boxed in a `Value[]` array:

```kotlin
// Santa:
let mut counter = 0
let increment = || { counter = counter + 1 }

// Generated:
// counter is stored as Value[1] where array[0] is the current value
// Both the outer scope and the lambda reference the same array
```

### Forward Reference Boxing

Top-level functions that reference other functions defined later need boxing:

```kotlin
// Santa:
let f = |x| g(x)  // References g before it's defined
let g = |x| x * 2

// Solution: f's slot contains Value[1] initially with nil
// When g is compiled, f can capture the box
// When f is assigned, box contents are updated
// Captures see the update through the shared box
```

## Tail Call Optimization

TCO is implemented for self-recursive functions (a function calling itself in tail position).

### TailCallAnalyzer

Analyzes function bodies to identify tail-recursive patterns:
1. Find all self-calls (calls to the function's own name)
2. Check if ALL self-calls are in tail position
3. Return `TailRecursionInfo` if eligible for optimization

Tail position means the call is the last operation before returning. Not in tail position:
- `f(x) + 1` - addition happens after call
- `let y = f(x); y` - let binding wraps the call

### Compilation Strategy

For tail-recursive functions, `TailRecursiveLambdaExpressionGenerator`:

1. **Setup**: Create loop start label, extract initial parameters from args list into local slots
2. **Loop body**: Compile function body with tail-call awareness
3. **Tail calls**: Instead of invoking, evaluate new arguments, store in parameter slots, `GOTO loopStart`
4. **Non-tail calls**: Compile as normal recursive calls
5. **Exit**: Return expression result when non-tail path reaches end

```kotlin
// Conceptual transformation:
let factorial = |n, acc| if n <= 1 { acc } else { factorial(n - 1, n * acc) }

// Becomes:
fun invoke(args: List<Value>): Value {
    var n = args[0]
    var acc = args[1]
    while (true) {
        if (n <= 1) {
            return acc
        } else {
            val newN = n - 1
            val newAcc = n * acc
            n = newN
            acc = newAcc
            continue  // GOTO loopStart
        }
    }
}
```

## Control Flow

### Return and Break

Non-local control flow uses exceptions for clean stack unwinding:

```kotlin
// ReturnException - thrown by `return expr`, caught at function boundary
class ReturnException(val value: Value) : Throwable(null, null, false, false)

// BreakException - thrown by `break expr`, caught by iteration builtins
class BreakException(val value: Value) : Throwable(null, null, false, false)
```

These extend `Throwable` directly (not `Exception`) and disable stack trace generation for performance since they're used for normal control flow.

### Function Invocation

Each lambda's `invoke` method wraps the body in try-catch:

```kotlin
fun invoke(args: List<Value>): Value {
    return try {
        // Compile body here
        bodyResult
    } catch (e: ReturnException) {
        e.value
    }
}
```

## Java Interop

Donner provides full access to the JVM ecosystem through reflection-based interop.

### Core Functions

| Function | Description |
|----------|-------------|
| `require("java.util.ArrayList")` | Load a Java class, returns `JavaClassValue` |
| `java_new(ArrayList)` | Construct an instance via reflection |
| `java_call(obj, "method", args...)` | Call instance method |
| `java_static(Math, "abs", -5)` | Call static method |
| `java_field(obj, "fieldName")` | Access instance field |
| `java_static_field(System, "out")` | Access static field |

### Method Combinators

For pipeline-compatible usage:

```santa
// method(name) returns a FunctionValue that takes target as first arg
"  hello  " |> method("trim") |> method("toUpperCase")

// static_method(class, name) returns a FunctionValue
[-5, 3, -2] |> map(static_method(Math, "abs"))
```

### Type Conversion

`JavaInterop` handles bidirectional conversion:

**Santa to Java:**
- `IntValue` → `Long`
- `DecimalValue` → `Double`
- `StringValue` → `String`
- `ListValue` → `List<Any?>`
- `NilValue` → `null`

**Java to Santa:**
- `null` → `NilValue`
- `Long/Int/Short/Byte` → `IntValue`
- `Double/Float` → `DecimalValue`
- `String` → `StringValue`
- `Array` → `ListValue`
- Other objects → `JavaObjectValue` (wrapper)

### Caching

`JavaInterop` uses concurrent caches for:
- Loaded classes (`ConcurrentHashMap<String, Class<*>>`)
- Resolved methods (`ConcurrentHashMap<MethodKey, Method>`)
- Resolved constructors
- Resolved fields

## Runtime Library

### Operators (`runtime/src/main/kotlin/santa/runtime/Operators.kt`)

Static methods called from generated bytecode for dynamic dispatch:

```kotlin
object Operators {
    @JvmStatic fun add(left: Value, right: Value): Value
    @JvmStatic fun subtract(left: Value, right: Value): Value
    @JvmStatic fun pipeline(value: Value, func: Value): Value
    @JvmStatic fun compose(first: Value, second: Value): Value
    @JvmStatic fun index(target: Value, index: Value): Value
    // ... etc
}
```

### Builtins (`runtime/src/main/kotlin/santa/runtime/Builtins.kt`)

Built-in functions organized by category:
- **Collection**: `map`, `filter`, `fold`, `reduce`, `zip`, `sort`, etc.
- **Math**: `abs`, `signum`, `vec_add`
- **Bitwise**: `bit_and`, `bit_or`, `bit_xor`, `bit_shift_left`, etc.
- **String**: `int`, `ints`, `lines`, `split`, `regex_match`, `replace`, `join`
- **Lazy**: `iterate`, `repeat`, `cycle`, `combinations`

Builtins are accessed through `BuiltinFunctionValue` (also defined in `Builtins.kt`), which wraps the dispatch:

```kotlin
class BuiltinFunctionValue(val name: String, val arity: Int) : FunctionValue(arity) {
    override fun invoke(args: List<Value>): Value {
        return when (name) {
            "map" -> Builtins.map(args[0], args[1])
            "filter" -> Builtins.filter(args[0], args[1])
            // ... dispatch table
        }
    }
}
```

## Project Structure

```
donner/
├── compiler/                    # Core compiler library
│   └── src/main/kotlin/santa/compiler/
│       ├── lexer/               # Tokenization
│       │   ├── Lexer.kt
│       │   ├── Token.kt
│       │   └── TokenType.kt
│       ├── parser/              # AST construction
│       │   ├── Parser.kt
│       │   └── Expr.kt
│       ├── desugar/             # AST transformations
│       │   ├── PlaceholderDesugarer.kt
│       │   ├── PipelineDesugarer.kt
│       │   └── PatternParamDesugarer.kt
│       ├── resolver/            # Name resolution
│       │   └── Resolver.kt
│       ├── codegen/             # Bytecode generation
│       │   ├── Compiler.kt      # Facade
│       │   ├── CompiledScript.kt
│       │   ├── CodeGenerator.kt # Main codegen
│       │   └── TailCallAnalyzer.kt
│       └── error/               # Error formatting
│           └── ErrorFormatter.kt
├── runtime/                     # Runtime library
│   └── src/main/kotlin/santa/runtime/
│       ├── value/               # Value types
│       │   └── Value.kt         # All value definitions
│       ├── Operators.kt         # Runtime operator dispatch
│       ├── Builtins.kt          # Built-in functions and BuiltinFunctionValue
│       └── JavaInterop.kt       # Java interop utilities
├── cli/                         # Command-line interface
│   └── src/main/kotlin/santa/cli/
│       ├── Main.kt
│       ├── TestRunner.kt
│       └── BenchmarkRunner.kt
└── examples/
    └── java_interop.santa
```

## Testing

- **JUnit 5 + Kotest assertions**: Standard Kotlin testing
- **Snapshot tests**: Parser and lexer output captured and compared
- **Integration tests**: End-to-end compilation and execution
- **Example programs**: Real Advent of Code solutions as integration tests

Run tests with:
```bash
make test
```

## Building

Requires Java 21+:

```bash
make build          # Build CLI
make cli/jar        # Build fat JAR (shadowJar)
make cli/jpackage   # Build native binary
make docker/build   # Build Docker image
```
