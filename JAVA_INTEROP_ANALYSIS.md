# Java Interoperability Analysis for Santa-Lang (Donner)

## Executive Summary

This document analyzes how to add JVM interoperability to santa-lang, enabling santa-lang programs to call Java/Kotlin/Scala libraries, create Java objects, and interoperate with the broader JVM ecosystem. This is similar to how Clojure, Scala, and Kotlin provide seamless Java interop.

---

## Part 1: Current Architecture Analysis

### 1.1 Value System

All runtime values in santa-lang implement the `Value` sealed interface (`runtime/src/main/kotlin/santa/runtime/value/Value.kt`):

```kotlin
sealed interface Value {
    fun isTruthy(): Boolean
    fun isHashable(): Boolean
    fun typeName(): String
}
```

Current value types:
- `IntValue` - 64-bit signed integers
- `DecimalValue` - 64-bit floating-point
- `StringValue` - UTF-8 strings with grapheme-cluster indexing
- `BoolValue` - Boolean (TRUE/FALSE singletons)
- `NilValue` - Singleton representing absence of value
- `ListValue` - Persistent immutable lists
- `SetValue` - Persistent immutable sets
- `DictValue` - Persistent immutable dictionaries
- `RangeValue` - Lazy integer ranges
- `LazySequenceValue` - Lazy sequences with generators
- `FunctionValue` - First-class functions (abstract class)

### 1.2 Compilation Pipeline

```
Source -> Lexer -> Parser -> Desugar -> Resolver -> CodeGenerator -> .class -> JVM
```

- **Lexer** (`compiler/src/main/kotlin/santa/compiler/lexer/Lexer.kt`): Tokenizes source
- **Parser** (`compiler/src/main/kotlin/santa/compiler/parser/Parser.kt`): Pratt parser producing AST
- **Desugarer**: Three passes for placeholders, pipelines, pattern params
- **Resolver** (`compiler/src/main/kotlin/santa/compiler/resolver/Resolver.kt`): Name resolution and scoping
- **CodeGenerator** (`compiler/src/main/kotlin/santa/compiler/codegen/CodeGenerator.kt`): ASM bytecode emission

### 1.3 Function Call Compilation

From `CodeGenerator.kt:2537-2632`:

1. **Built-in functions**: Direct static method calls to `Builtins.methodName()`
2. **User-defined functions**:
   - Evaluate callee expression
   - Build `List<Value>` from arguments
   - Call `Operators.invokeFunction(FunctionValue, List<Value>)`
3. **Partial application**: Handled automatically when fewer args than expected

### 1.4 Class Loading

From `CompiledScript.kt`:
- Custom `ScriptClassLoader` loads compiled script and lambda classes
- Scripts compile to synthetic classes (e.g., `santa/Script123`)
- Lambdas compile to inner classes extending `FunctionValue`

---

## Part 2: Survey of JVM Interop Approaches

### 2.1 Clojure's Approach

**Syntax** ([Clojure Java Interop](https://clojure.org/reference/java_interop)):
```clojure
;; Instance method
(.toUpperCase "hello")

;; Static method
(System/getProperty "java.vm.version")

;; Constructor
(ArrayList. 10)
(new ArrayList 10)

;; Field access
(.-x point)
```

**Implementation**:
- Compiles to direct bytecode when types are known
- Falls back to reflection for dynamic cases
- Type hints (`^String`) to avoid reflection

### 2.2 Scala's Approach

**Syntax** ([Scala-Java Interop](https://docs.scala-lang.org/scala3/book/interacting-with-java.html)):
```scala
// Seamless - Java classes work like Scala classes
"hello".toUpperCase()
System.getProperty("java.vm.version")
new ArrayList[Int]()
```

**Implementation**:
- Full static typing enables direct bytecode
- No reflection needed in most cases

### 2.3 Kotlin's Approach

**Syntax** ([Kotlin Java Interop](https://kotlinlang.org/docs/java-interop.html)):
```kotlin
// Seamless
"hello".uppercase()
System.getProperty("java.vm.version")
ArrayList<Int>()
```

**Implementation**:
- Static typing + inference
- Null safety interop with platform types
- Direct bytecode compilation

---

## Part 3: Design Options for Santa-Lang

### Option A: Clojure-Style Special Forms

```santa
// Instance method
(.toUpperCase "hello")
// With pipeline
"hello" |> .toUpperCase

// Static method
System/getProperty("java.vm.version")

// Constructor
ArrayList.(10)

// Field access
(.-x point)
```

**Pros**: Familiar to Clojure users, explicit, works well with pipelines
**Cons**: Requires new parsing rules, different from regular function calls

### Option B: Dot-Syntax (OOP-Style)

```santa
// Instance method
"hello".toUpperCase()

// Static method
java.lang.System.getProperty("java.vm.version")

// Constructor
new java.util.ArrayList(10)

// Field access
point.x
```

**Pros**: Familiar to Java/Kotlin developers, intuitive
**Cons**: Requires `new` keyword, conflicts with potential future features, less functional

### Option C: Built-in Interop Functions

```santa
// Instance method
java_call("hello", "toUpperCase")
java_call(list, "add", item)

// Static method
java_static("java.lang.System", "getProperty", "java.vm.version")

// Constructor
java_new("java.util.ArrayList", 10)

// Field access
java_field(point, "x")
java_set_field(point, "x", 100)
```

**Pros**: Minimal syntax changes, explicit, easy to implement
**Cons**: Verbose, not idiomatic, poor ergonomics

### Option D: Qualified Names with Double-Colon

```santa
// Instance method (using special syntax marker)
obj::toString()
"hello"::toUpperCase()

// Static method
java.lang.System::getProperty("java.vm.version")

// Constructor
java.util.ArrayList::new(10)

// Field access
point::x
```

**Pros**: Clear disambiguation from santa-lang calls, Rust-like familiarity
**Cons**: New syntax to learn

### Option E: Import + Natural Syntax (Recommended)

```santa
// Import statement
import java.util.ArrayList
import java.lang.System

// Instance method - dot syntax
"hello".toUpperCase()
list.add(item)

// Static method - class reference
System.getProperty("java.vm.version")

// Constructor - function-like
ArrayList()
ArrayList(10)

// Field access
point.x
```

**Pros**: Cleanest syntax, most intuitive, matches how Scala/Kotlin work
**Cons**: More complex implementation, potential name conflicts

---

## Part 4: Recommended Design

### 4.1 Overview

A **hybrid approach** that balances explicitness with ergonomics:

1. **New `java` prefix for explicit interop** (no imports needed)
2. **Optional `import` for cleaner syntax**
3. **Dot notation for method calls and field access**
4. **Integration with pipelines**

### 4.2 Syntax Specification

#### 4.2.1 Without Imports (Explicit)

```santa
// Constructor
let list = java.util.ArrayList()
let list = java.util.ArrayList(10)

// Instance method
list.java:add(item)
"hello".java:toUpperCase()

// Static method
java.lang.System.getProperty("java.vm.version")
java.lang.Math.max(a, b)

// Instance field access
point.java:x

// Static field access
java.lang.Integer.MAX_VALUE
```

#### 4.2.2 With Imports (Clean)

```santa
// Top-level imports
import java.util.ArrayList
import java.lang.System
import java.awt.Point

// Constructor (looks like function call)
let list = ArrayList()

// Instance method (dot syntax)
list.add(item)
"hello".toUpperCase()

// Static method
System.getProperty("java.vm.version")

// Field access
point.x
```

#### 4.2.3 Pipeline Integration

```santa
"hello, world"
  |> .toUpperCase()
  |> .split(", ")
  |> .first()
```

Or with explicit java prefix:

```santa
"hello"
  |> .java:toUpperCase()
  |> .java:substring(0, 3)
```

### 4.3 Type Conversions

#### Santa-Lang to Java

| Santa Type | Java Type |
|------------|-----------|
| IntValue | long (unboxed) or Long |
| DecimalValue | double (unboxed) or Double |
| StringValue | String |
| BoolValue | boolean (unboxed) or Boolean |
| NilValue | null |
| ListValue | List<Object> (via converter) |
| SetValue | Set<Object> (via converter) |
| DictValue | Map<Object, Object> (via converter) |
| FunctionValue | (Cannot convert directly; wrap in SAM if needed) |
| JavaObjectValue | The wrapped Object |

#### Java to Santa-Lang

| Java Type | Santa Type |
|-----------|------------|
| byte, short, int, long | IntValue |
| float, double | DecimalValue |
| String | StringValue |
| boolean | BoolValue |
| null | NilValue |
| List<?> | ListValue (deep conversion) |
| Set<?> | SetValue (deep conversion) |
| Map<?, ?> | DictValue (deep conversion) |
| Other objects | JavaObjectValue |

---

## Part 5: Implementation Plan

### Phase 1: JavaObjectValue Runtime Type

**Files to modify:**
- `runtime/src/main/kotlin/santa/runtime/value/Value.kt`

**New code:**

```kotlin
/**
 * Wrapper for Java objects from interop (LANG.txt future extension).
 *
 * Enables santa-lang to hold references to arbitrary Java objects
 * and interact with them via reflection.
 */
class JavaObjectValue(val obj: Any?) : Value {
    override fun isTruthy(): Boolean = obj != null
    override fun isHashable(): Boolean = false  // Conservative - could check
    override fun typeName(): String = obj?.javaClass?.simpleName ?: "null"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is JavaObjectValue) return false
        return obj == other.obj
    }

    override fun hashCode(): Int = obj?.hashCode() ?: 0
}
```

### Phase 2: JavaInterop Runtime Support

**New file:** `runtime/src/main/kotlin/santa/runtime/JavaInterop.kt`

```kotlin
object JavaInterop {
    // Class loading and caching
    private val classCache = ConcurrentHashMap<String, Class<*>>()
    private val methodCache = ConcurrentHashMap<MethodKey, Method>()

    @JvmStatic
    fun loadClass(name: String): Class<*> {
        return classCache.getOrPut(name) {
            Class.forName(name)
        }
    }

    @JvmStatic
    fun construct(className: String, args: List<Value>): JavaObjectValue {
        val clazz = loadClass(className)
        val javaArgs = args.map { toJava(it) }.toTypedArray()
        // Find matching constructor via reflection
        // ...
    }

    @JvmStatic
    fun invokeMethod(target: Any?, methodName: String, args: List<Value>): Value {
        // Reflection-based method invocation
        // ...
    }

    @JvmStatic
    fun getField(target: Any?, fieldName: String): Value {
        // Reflection-based field access
        // ...
    }

    @JvmStatic
    fun toJava(value: Value): Any? = when (value) {
        is IntValue -> value.value
        is DecimalValue -> value.value
        is StringValue -> value.value
        is BoolValue -> value.value
        is NilValue -> null
        is JavaObjectValue -> value.obj
        is ListValue -> value.elements.map { toJava(it) }
        // ... etc
    }

    @JvmStatic
    fun fromJava(obj: Any?): Value = when (obj) {
        null -> NilValue
        is Long -> IntValue(obj)
        is Int -> IntValue(obj.toLong())
        is Double -> DecimalValue(obj)
        is Float -> DecimalValue(obj.toDouble())
        is String -> StringValue(obj)
        is Boolean -> BoolValue.box(obj)
        is List<*> -> ListValue(obj.mapNotNull { fromJava(it) }.toPersistentList())
        // ... etc
        else -> JavaObjectValue(obj)
    }
}
```

### Phase 3: Lexer Extensions

**Files to modify:**
- `compiler/src/main/kotlin/santa/compiler/lexer/TokenType.kt`
- `compiler/src/main/kotlin/santa/compiler/lexer/Lexer.kt`

**New tokens:**

```kotlin
// In TokenType.kt
IMPORT,          // import keyword
JAVA_COLON,      // java: prefix for explicit interop
DOT,             // . (if not already full support)
NEW,             // new keyword (optional, for explicit construction)
```

### Phase 4: Parser Extensions

**Files to modify:**
- `compiler/src/main/kotlin/santa/compiler/parser/Expr.kt`
- `compiler/src/main/kotlin/santa/compiler/parser/Parser.kt`

**New AST nodes:**

```kotlin
// Import declaration
data class ImportDecl(
    val className: String,  // e.g., "java.util.ArrayList"
    override val span: Span,
) : TopLevel

// Java method call
data class JavaMethodCallExpr(
    val target: Expr,           // Object to call method on
    val methodName: String,     // Method name
    val arguments: List<CallArgument>,
    override val span: Span,
) : Expr

// Java constructor call
data class JavaConstructorExpr(
    val className: String,      // Fully qualified or imported name
    val arguments: List<CallArgument>,
    override val span: Span,
) : Expr

// Java field access
data class JavaFieldAccessExpr(
    val target: Expr,
    val fieldName: String,
    override val span: Span,
) : Expr

// Java static method/field access
data class JavaStaticAccessExpr(
    val className: String,
    val memberName: String,
    val arguments: List<CallArgument>?,  // null for field, present for method
    override val span: Span,
) : Expr
```

### Phase 5: Resolver Extensions

**Files to modify:**
- `compiler/src/main/kotlin/santa/compiler/resolver/Resolver.kt`

**Changes:**
- Track imported class names
- Validate import paths
- Resolve short class names to fully qualified names

### Phase 6: Code Generator Extensions

**Files to modify:**
- `compiler/src/main/kotlin/santa/compiler/codegen/CodeGenerator.kt`

**Bytecode generation strategies:**

#### 6.1 Reflection-Based (Initial Implementation)

```kotlin
// For JavaMethodCallExpr
is JavaMethodCallExpr -> {
    // Push target value
    compileExpr(expr.target)
    // Push method name
    mv.visitLdcInsn(expr.methodName)
    // Build args list
    compileArgumentsList(expr.arguments)
    // Call JavaInterop.invokeMethod
    mv.visitMethodInsn(
        INVOKESTATIC,
        "santa/runtime/JavaInterop",
        "invokeMethod",
        "(Ljava/lang/Object;Ljava/lang/String;Ljava/util/List;)Lsanta/runtime/value/Value;",
        false
    )
}
```

#### 6.2 Direct Bytecode (Future Optimization)

When type information is available:

```kotlin
// Direct INVOKEVIRTUAL for known types
mv.visitTypeInsn(CHECKCAST, "java/lang/String")
mv.visitMethodInsn(
    INVOKEVIRTUAL,
    "java/lang/String",
    "toUpperCase",
    "()Ljava/lang/String;",
    false
)
// Wrap result back to Value
mv.visitMethodInsn(
    INVOKESTATIC,
    "santa/runtime/JavaInterop",
    "fromJava",
    "(Ljava/lang/Object;)Lsanta/runtime/value/Value;",
    false
)
```

---

## Part 6: Risk Analysis

### 6.1 Syntax Conflicts

**Risk**: Dot notation might conflict with future features
**Mitigation**: Use explicit `java:` prefix for unambiguous parsing

### 6.2 Performance

**Risk**: Reflection is slower than direct bytecode
**Mitigation**:
- Method handle caching
- Future: type inference for direct bytecode emission
- LambdaMetafactory for SAM conversions

### 6.3 Type Safety

**Risk**: Runtime type errors from Java interop
**Mitigation**:
- Clear error messages showing Java types
- Type conversion documentation
- Optional type hints for safety

### 6.4 Complexity

**Risk**: Significant changes to parser and codegen
**Mitigation**:
- Phased implementation starting with reflection
- Comprehensive test suite
- Feature flag to enable/disable interop

---

## Part 7: Testing Strategy

### 7.1 Unit Tests

```kotlin
@Test
fun `java string method call`() {
    eval("""
        import java.lang.String
        "hello".toUpperCase()
    """) shouldBe StringValue("HELLO")
}

@Test
fun `java constructor`() {
    eval("""
        import java.util.ArrayList
        let list = ArrayList()
        list.add(42)
        list.size()
    """) shouldBe IntValue(1)
}

@Test
fun `java static method`() {
    eval("""
        java.lang.Math.max(10, 20)
    """) shouldBe IntValue(20)
}
```

### 7.2 Integration Tests

- Test with real Java libraries (Guava, Apache Commons)
- Test with Kotlin stdlib
- Test exception handling across boundaries

---

## Part 8: Alternative Minimal Implementation

If the full design is too ambitious, a minimal viable implementation could use **built-in functions only**:

```santa
// Minimal interop via builtins
let list = java_new("java.util.ArrayList")
java_call(list, "add", 42)
let size = java_call(list, "size")
let max = java_static("java.lang.Math", "max", 10, 20)
```

**Pros**: Minimal parser changes, quick to implement
**Cons**: Poor ergonomics, verbose

---

## Part 9: Recommendations

### Recommended Approach: Phased Implementation

1. **Phase 1 (MVP)**: Built-in functions for basic interop
   - `java_new`, `java_call`, `java_static`, `java_field`
   - Reflection-based implementation
   - No parser changes

2. **Phase 2 (Ergonomic)**: Add import syntax and dot notation
   - New `import` keyword
   - Dot syntax for method calls on JavaObjectValue
   - Parser extensions

3. **Phase 3 (Performance)**: Direct bytecode generation
   - Type inference for known types
   - Method handle caching
   - Optional type hints

### Estimated Complexity

| Phase | Effort | Files Changed | New Files |
|-------|--------|---------------|-----------|
| Phase 1 | 2-3 days | 3 | 1 |
| Phase 2 | 1-2 weeks | 6 | 0 |
| Phase 3 | 2-4 weeks | 2 | 1 |

---

## Appendix A: Example Programs

### A.1 HTTP Request with OkHttp

```santa
import okhttp3.OkHttpClient
import okhttp3.Request

let client = OkHttpClient()
let request = Request.Builder()
    .url("https://api.example.com/data")
    .build()

let response = client.newCall(request).execute()
let body = response.body().string()

body |> lines |> map(int) |> sum
```

### A.2 JSON Parsing with Gson

```santa
import com.google.gson.Gson
import com.google.gson.JsonArray

let gson = Gson()
let json = read("data.json")
let array = gson.fromJson(json, JsonArray)

// Convert to santa-lang list
array |> map(|e| e.getAsInt()) |> sum
```

### A.3 File Processing with Java NIO

```santa
import java.nio.file.Files
import java.nio.file.Paths

let path = Paths.get("large_file.txt")
Files.lines(path)
    |> map(|line| line.java:trim())
    |> filter(|line| line.java:length() > 0)
    |> count
```
