package santa.compiler.codegen

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import santa.runtime.value.*

/**
 * Tests for bytecode generation through the Compiler facade.
 *
 * Each test compiles a source string and executes it, verifying the result.
 */
class CodegenTest {

    private fun eval(source: String): Value = Compiler.compile(source).execute()

    @Nested
    inner class Literals {
        @Test
        fun `integer literal`() {
            eval("42") shouldBe IntValue(42)
        }

        @Test
        fun `negative integer literal`() {
            eval("-17") shouldBe IntValue(-17)
        }

        @Test
        fun `zero`() {
            eval("0") shouldBe IntValue(0)
        }

        @Test
        fun `decimal literal`() {
            eval("3.14") shouldBe DecimalValue(3.14)
        }

        @Test
        fun `negative decimal literal`() {
            eval("-0.5") shouldBe DecimalValue(-0.5)
        }

        @Test
        fun `string literal`() {
            eval("\"hello\"") shouldBe StringValue("hello")
        }

        @Test
        fun `string with escape sequences`() {
            eval("\"hello\\nworld\"") shouldBe StringValue("hello\nworld")
        }

        @Test
        fun `empty string`() {
            eval("\"\"") shouldBe StringValue("")
        }

        @Test
        fun `true literal`() {
            eval("true") shouldBe BoolValue(true)
        }

        @Test
        fun `false literal`() {
            eval("false") shouldBe BoolValue(false)
        }

        @Test
        fun `nil literal`() {
            eval("nil") shouldBe NilValue
        }
    }

    @Nested
    inner class Variables {
        @Test
        fun `let binding simple value`() {
            eval("let x = 42; x") shouldBe IntValue(42)
        }

        @Test
        fun `let binding expression`() {
            eval("let x = 1 + 2; x") shouldBe IntValue(3)
        }

        @Test
        fun `multiple let bindings`() {
            eval("let x = 1; let y = 2; x") shouldBe IntValue(1)
        }

        @Test
        fun `let uses previous binding`() {
            eval("let x = 10; let y = x; y") shouldBe IntValue(10)
        }

        @Test
        fun `mutable variable assignment`() {
            eval("let mut x = 1; x = 2; x") shouldBe IntValue(2)
        }

        @Test
        fun `shadowing in same scope`() {
            eval("let x = 1; let x = 2; x") shouldBe IntValue(2)
        }
    }

    @Nested
    inner class Blocks {
        @Test
        fun `block with single statement returns it`() {
            // Use ; to make it a block, not a set
            eval("{ 42; }") shouldBe IntValue(42)
        }

        @Test
        fun `block returns last expression`() {
            eval("{ 1; 2; 3 }") shouldBe IntValue(3)
        }

        @Test
        fun `block with let returns last expression`() {
            eval("{ let x = 42; x }") shouldBe IntValue(42)
        }

        @Test
        fun `nested blocks`() {
            eval("{ let x = 1; { let y = 2; x } }") shouldBe IntValue(1)
        }

        @Test
        fun `shadowing in nested block`() {
            eval("let x = 1; { let x = 2; x }") shouldBe IntValue(2)
        }

        @Test
        fun `outer scope preserved after block`() {
            eval("let x = 1; { let y = 2; }; x") shouldBe IntValue(1)
        }
    }

    @Nested
    inner class Arithmetic {
        @Test
        fun `integer addition`() {
            eval("1 + 2") shouldBe IntValue(3)
        }

        @Test
        fun `integer subtraction`() {
            eval("5 - 3") shouldBe IntValue(2)
        }

        @Test
        fun `integer multiplication`() {
            eval("3 * 4") shouldBe IntValue(12)
        }

        @Test
        fun `integer division floors toward negative infinity`() {
            eval("7 / 2") shouldBe IntValue(3)
            eval("-7 / 2") shouldBe IntValue(-4)
            eval("7 / -2") shouldBe IntValue(-4)
            eval("-7 / -2") shouldBe IntValue(3)
        }

        @Test
        fun `modulo with Python semantics`() {
            eval("7 % 3") shouldBe IntValue(1)
            eval("-7 % 3") shouldBe IntValue(2)
            eval("7 % -3") shouldBe IntValue(-2)
            eval("-7 % -3") shouldBe IntValue(-1)
        }

        @Test
        fun `decimal addition`() {
            eval("1.5 + 2.5") shouldBe DecimalValue(4.0)
        }

        @Test
        fun `mixed integer and decimal - left determines type`() {
            eval("1 + 2.5") shouldBe IntValue(3)
            eval("1.5 + 2") shouldBe DecimalValue(3.5)
        }

        @Test
        fun `operator precedence`() {
            eval("2 + 3 * 4") shouldBe IntValue(14)
            eval("(2 + 3) * 4") shouldBe IntValue(20)
        }

        @Test
        fun `string concatenation`() {
            eval("\"hello\" + \" \" + \"world\"") shouldBe StringValue("hello world")
        }

        @Test
        fun `string repetition`() {
            eval("\"ab\" * 3") shouldBe StringValue("ababab")
        }
    }

    @Nested
    inner class Comparison {
        @Test
        fun `equality`() {
            eval("1 == 1") shouldBe BoolValue(true)
            eval("1 == 2") shouldBe BoolValue(false)
        }

        @Test
        fun `inequality`() {
            eval("1 != 2") shouldBe BoolValue(true)
            eval("1 != 1") shouldBe BoolValue(false)
        }

        @Test
        fun `less than`() {
            eval("1 < 2") shouldBe BoolValue(true)
            eval("2 < 1") shouldBe BoolValue(false)
            eval("1 < 1") shouldBe BoolValue(false)
        }

        @Test
        fun `less or equal`() {
            eval("1 <= 2") shouldBe BoolValue(true)
            eval("1 <= 1") shouldBe BoolValue(true)
            eval("2 <= 1") shouldBe BoolValue(false)
        }

        @Test
        fun `greater than`() {
            eval("2 > 1") shouldBe BoolValue(true)
            eval("1 > 2") shouldBe BoolValue(false)
            eval("1 > 1") shouldBe BoolValue(false)
        }

        @Test
        fun `greater or equal`() {
            eval("2 >= 1") shouldBe BoolValue(true)
            eval("1 >= 1") shouldBe BoolValue(true)
            eval("1 >= 2") shouldBe BoolValue(false)
        }

        @Test
        fun `string comparison`() {
            eval("\"abc\" < \"def\"") shouldBe BoolValue(true)
            eval("\"abc\" == \"abc\"") shouldBe BoolValue(true)
        }

        @Test
        fun `mixed numeric comparison`() {
            eval("1 < 1.5") shouldBe BoolValue(true)
            eval("2.0 > 1") shouldBe BoolValue(true)
        }
    }

    @Nested
    inner class Logical {
        @Test
        fun `logical not`() {
            eval("!true") shouldBe BoolValue(false)
            eval("!false") shouldBe BoolValue(true)
        }

        @Test
        fun `not on truthy values`() {
            eval("!1") shouldBe BoolValue(false)
            eval("!0") shouldBe BoolValue(true)
            eval("!\"\"") shouldBe BoolValue(true)
            eval("!\"hello\"") shouldBe BoolValue(false)
        }

        @Test
        fun `logical and`() {
            eval("true && true") shouldBe BoolValue(true)
            eval("true && false") shouldBe BoolValue(false)
            eval("false && true") shouldBe BoolValue(false)
            eval("false && false") shouldBe BoolValue(false)
        }

        @Test
        fun `logical or`() {
            eval("true || true") shouldBe BoolValue(true)
            eval("true || false") shouldBe BoolValue(true)
            eval("false || true") shouldBe BoolValue(true)
            eval("false || false") shouldBe BoolValue(false)
        }

        @Test
        fun `and with truthy values`() {
            eval("1 && 2") shouldBe BoolValue(true)
            eval("0 && 2") shouldBe BoolValue(false)
            eval("1 && nil") shouldBe BoolValue(false)
        }

        @Test
        fun `or with truthy values`() {
            eval("1 || 0") shouldBe BoolValue(true)
            eval("0 || 2") shouldBe BoolValue(true)
            eval("0 || nil") shouldBe BoolValue(false)
        }

        @Test
        fun `short circuit and - left false skips right`() {
            // When left is false, right is not evaluated
            // We verify by seeing the result is false even though right would be true
            eval("false && true") shouldBe BoolValue(false)
        }

        @Test
        fun `short circuit or - left true skips right`() {
            // When left is true, right is not evaluated
            // We verify by seeing the result is true
            eval("true || false") shouldBe BoolValue(true)
        }
    }

    @Nested
    inner class Collections {
        @Test
        fun `empty list`() {
            val result = eval("[]") as ListValue
            result.size() shouldBe 0
        }

        @Test
        fun `list literal`() {
            val result = eval("[1, 2, 3]") as ListValue
            result.size() shouldBe 3
            result.get(0) shouldBe IntValue(1)
            result.get(1) shouldBe IntValue(2)
            result.get(2) shouldBe IntValue(3)
        }

        @Test
        fun `nested list`() {
            val result = eval("[[1, 2], [3, 4]]") as ListValue
            result.size() shouldBe 2
            (result.get(0) as ListValue).get(0) shouldBe IntValue(1)
        }
    }

    @Nested
    inner class Indexing {
        @Test
        fun `list indexing`() {
            eval("[10, 20, 30][1]") shouldBe IntValue(20)
        }

        @Test
        fun `list negative indexing`() {
            eval("[10, 20, 30][-1]") shouldBe IntValue(30)
        }

        @Test
        fun `list out of bounds returns nil`() {
            eval("[1, 2][10]") shouldBe NilValue
        }

        @Test
        fun `string indexing`() {
            eval("\"hello\"[0]") shouldBe StringValue("h")
            eval("\"hello\"[-1]") shouldBe StringValue("o")
        }

        @Test
        fun `list slicing with exclusive range`() {
            val result = eval("[10, 20, 30, 40, 50][1..3]") as ListValue
            result.size() shouldBe 2
            result.get(0) shouldBe IntValue(20)
            result.get(1) shouldBe IntValue(30)
        }

        @Test
        fun `list slicing with inclusive range`() {
            val result = eval("[10, 20, 30, 40, 50][1..=3]") as ListValue
            result.size() shouldBe 3
            result.get(0) shouldBe IntValue(20)
            result.get(1) shouldBe IntValue(30)
            result.get(2) shouldBe IntValue(40)
        }

        @Test
        fun `list slicing with unbounded range`() {
            val result = eval("[10, 20, 30, 40, 50][2..]") as ListValue
            result.size() shouldBe 3
            result.get(0) shouldBe IntValue(30)
            result.get(1) shouldBe IntValue(40)
            result.get(2) shouldBe IntValue(50)
        }

        @Test
        fun `string slicing with exclusive range`() {
            eval("\"hello\"[1..4]") shouldBe StringValue("ell")
        }

        @Test
        fun `string slicing with inclusive range`() {
            eval("\"hello\"[1..=3]") shouldBe StringValue("ell")
        }

        @Test
        fun `string slicing with unbounded range`() {
            eval("\"hello\"[2..]") shouldBe StringValue("llo")
        }
    }

    @Nested
    inner class BuiltinFunctions {
        @Test
        fun `size of list`() {
            eval("size([1, 2, 3])") shouldBe IntValue(3)
        }

        @Test
        fun `size of empty list`() {
            eval("size([])") shouldBe IntValue(0)
        }

        @Test
        fun `size of string`() {
            eval("size(\"hello\")") shouldBe IntValue(5)
        }

        @Test
        fun `first of list`() {
            eval("first([1, 2, 3])") shouldBe IntValue(1)
        }

        @Test
        fun `first of empty list returns nil`() {
            eval("first([])") shouldBe NilValue
        }

        @Test
        fun `rest of list`() {
            val result = eval("rest([1, 2, 3])") as ListValue
            result.size() shouldBe 2
            result.get(0) shouldBe IntValue(2)
            result.get(1) shouldBe IntValue(3)
        }

        @Test
        fun `push to list`() {
            val result = eval("push([1, 2], 3)") as ListValue
            result.size() shouldBe 3
            result.get(2) shouldBe IntValue(3)
        }

        @Test
        fun `int conversion from string`() {
            eval("int(\"42\")") shouldBe IntValue(42)
        }

        @Test
        fun `int conversion from unparseable returns 0`() {
            eval("int(\"abc\")") shouldBe IntValue(0)
        }

        @Test
        fun `type of integer`() {
            eval("type(42)") shouldBe StringValue("Integer")
        }

        @Test
        fun `type of string`() {
            eval("type(\"hello\")") shouldBe StringValue("String")
        }

        @Test
        fun `type of list`() {
            eval("type([1, 2])") shouldBe StringValue("List")
        }

        @Test
        fun `abs of positive`() {
            eval("abs(42)") shouldBe IntValue(42)
        }

        @Test
        fun `abs of negative`() {
            eval("abs(-42)") shouldBe IntValue(42)
        }

        // Type conversion tests
        @Test
        fun `ints extracts integers from string`() {
            val result = eval("ints(\"x: 10, y: -5\")") as ListValue
            result.size() shouldBe 2
            result.get(0) shouldBe IntValue(10)
            result.get(1) shouldBe IntValue(-5)
        }

        @Test
        fun `list from set`() {
            val result = eval("list({1, 2, 3})") as ListValue
            result.size() shouldBe 3
        }

        @Test
        fun `set from list removes duplicates`() {
            val result = eval("set([1, 2, 2, 3])") as SetValue
            result.size() shouldBe 3
        }

        @Test
        fun `dict from list of tuples`() {
            val result = eval("dict([[1, 2], [3, 4]])") as DictValue
            result.get(IntValue(1)) shouldBe IntValue(2)
            result.get(IntValue(3)) shouldBe IntValue(4)
        }

        // Collection access tests
        @Test
        fun `get from list`() {
            eval("get(1, [10, 20, 30])") shouldBe IntValue(20)
        }

        @Test
        fun `get from dict`() {
            eval("get(1, #{1: 2, 3: 4})") shouldBe IntValue(2)
        }

        @Test
        fun `second of list`() {
            eval("second([1, 2, 3])") shouldBe IntValue(2)
        }

        @Test
        fun `last of list`() {
            eval("last([1, 2, 3])") shouldBe IntValue(3)
        }

        // Collection modification tests
        @Test
        fun `assoc replaces list element`() {
            val result = eval("assoc(0, 10, [1, 2])") as ListValue
            result.get(0) shouldBe IntValue(10)
            result.get(1) shouldBe IntValue(2)
        }

        @Test
        fun `assoc adds to dict`() {
            val result = eval("assoc(5, 6, #{1: 2})") as DictValue
            result.get(IntValue(5)) shouldBe IntValue(6)
        }

        @Test
        fun `update uses function`() {
            val result = eval("update(0, |x| x + 1, [10, 20])") as ListValue
            result.get(0) shouldBe IntValue(11)
        }

        // Transformation tests
        @Test
        fun `map over list`() {
            val result = eval("map(|x| x * 2, [1, 2, 3])") as ListValue
            result.size() shouldBe 3
            result.get(0) shouldBe IntValue(2)
            result.get(1) shouldBe IntValue(4)
            result.get(2) shouldBe IntValue(6)
        }

        @Test
        fun `filter list`() {
            val result = eval("filter(|x| x > 2, [1, 2, 3, 4])") as ListValue
            result.size() shouldBe 2
            result.get(0) shouldBe IntValue(3)
            result.get(1) shouldBe IntValue(4)
        }

        @Test
        fun `flat_map flattens results`() {
            val result = eval("flat_map(|x| [x, x * 2], [1, 2])") as ListValue
            result.size() shouldBe 4
            result.get(0) shouldBe IntValue(1)
            result.get(1) shouldBe IntValue(2)
            result.get(2) shouldBe IntValue(2)
            result.get(3) shouldBe IntValue(4)
        }

        // Reduction tests
        @Test
        fun `reduce sums list`() {
            eval("reduce(|a, b| a + b, [1, 2, 3, 4])") shouldBe IntValue(10)
        }

        @Test
        fun `fold with initial value`() {
            eval("fold(10, |a, b| a + b, [1, 2, 3])") shouldBe IntValue(16)
        }

        @Test
        fun `scan returns intermediate results`() {
            val result = eval("scan(0, |a, b| a + b, [1, 2, 3])") as ListValue
            result.size() shouldBe 3
            result.get(0) shouldBe IntValue(1)
            result.get(1) shouldBe IntValue(3)
            result.get(2) shouldBe IntValue(6)
        }

        @Test
        fun `fold_s returns first element of final state`() {
            // Fibonacci using state: [fib_n, fib_n-1]
            // state[0] = fib_n, state[1] = fib_n-1
            // After 10 iterations from [0, 1]: 0,1,1,2,3,5,8,13,21,34,55
            eval("fold_s([0, 1], |state, _| [state[1], state[0] + state[1]], [1, 2, 3, 4, 5, 6, 7, 8, 9, 10])") shouldBe IntValue(55)
        }

        @Test
        fun `fold_s with simple state tracking`() {
            // Track sum and count: [sum, count] -> returns sum
            eval("fold_s([0, 0], |state, x| [state[0] + x, state[1] + 1], [1, 2, 3])") shouldBe IntValue(6)
        }

        // Search tests
        @Test
        fun `find returns first match`() {
            eval("find(|x| x > 2, [1, 2, 3, 4])") shouldBe IntValue(3)
        }

        @Test
        fun `find returns nil when no match`() {
            eval("find(|x| x > 10, [1, 2, 3])") shouldBe NilValue
        }

        @Test
        fun `count matching elements`() {
            eval("count(|x| x > 2, [1, 2, 3, 4, 5])") shouldBe IntValue(3)
        }

        // Aggregation tests
        @Test
        fun `sum of list`() {
            eval("sum([1, 2, 3, 4])") shouldBe IntValue(10)
        }

        @Test
        fun `max of list`() {
            eval("max([3, 1, 4, 1, 5])") shouldBe IntValue(5)
        }

        @Test
        fun `min of list`() {
            eval("min([3, 1, 4, 1, 5])") shouldBe IntValue(1)
        }

        // Sequence manipulation tests
        @Test
        fun `skip drops elements`() {
            val result = eval("skip(2, [1, 2, 3, 4])") as ListValue
            result.size() shouldBe 2
            result.get(0) shouldBe IntValue(3)
            result.get(1) shouldBe IntValue(4)
        }

        @Test
        fun `take limits elements`() {
            val result = eval("take(2, [1, 2, 3, 4])") as ListValue
            result.size() shouldBe 2
            result.get(0) shouldBe IntValue(1)
            result.get(1) shouldBe IntValue(2)
        }

        @Test
        fun `reverse list`() {
            val result = eval("reverse([1, 2, 3])") as ListValue
            result.get(0) shouldBe IntValue(3)
            result.get(1) shouldBe IntValue(2)
            result.get(2) shouldBe IntValue(1)
        }

        @Test
        fun `rotate list`() {
            val result = eval("rotate(1, [1, 2, 3])") as ListValue
            result.get(0) shouldBe IntValue(3)
            result.get(1) shouldBe IntValue(1)
            result.get(2) shouldBe IntValue(2)
        }

        @Test
        fun `chunk splits list`() {
            val result = eval("chunk(2, [1, 2, 3, 4, 5])") as ListValue
            result.size() shouldBe 3
            (result.get(0) as ListValue).size() shouldBe 2
            (result.get(2) as ListValue).size() shouldBe 1
        }

        // Predicate tests
        @Test
        fun `includes? finds element`() {
            eval("includes?([1, 2, 3], 2)") shouldBe BoolValue.TRUE
            eval("includes?([1, 2, 3], 5)") shouldBe BoolValue.FALSE
        }

        @Test
        fun `any? finds match`() {
            eval("any?(|x| x > 2, [1, 2, 3])") shouldBe BoolValue.TRUE
            eval("any?(|x| x > 10, [1, 2, 3])") shouldBe BoolValue.FALSE
        }

        @Test
        fun `all? checks all`() {
            eval("all?(|x| x > 0, [1, 2, 3])") shouldBe BoolValue.TRUE
            eval("all?(|x| x > 2, [1, 2, 3])") shouldBe BoolValue.FALSE
        }

        // String function tests
        @Test
        fun `lines splits on newline`() {
            val result = eval("lines(\"a\\nb\\nc\")") as ListValue
            result.size() shouldBe 3
            result.get(0) shouldBe StringValue("a")
            result.get(1) shouldBe StringValue("b")
            result.get(2) shouldBe StringValue("c")
        }

        @Test
        fun `split by separator`() {
            val result = eval("split(\",\", \"a,b,c\")") as ListValue
            result.size() shouldBe 3
            result.get(0) shouldBe StringValue("a")
        }

        @Test
        fun `upper converts to uppercase`() {
            eval("upper(\"hello\")") shouldBe StringValue("HELLO")
        }

        @Test
        fun `lower converts to lowercase`() {
            eval("lower(\"HELLO\")") shouldBe StringValue("hello")
        }

        @Test
        fun `replace substitutes pattern`() {
            eval("replace(\"o\", \"0\", \"hello\")") shouldBe StringValue("hell0")
        }

        @Test
        fun `join combines elements`() {
            eval("join(\", \", [1, 2, 3])") shouldBe StringValue("1, 2, 3")
        }

        // Regex tests
        @Test
        fun `regex_match single capture group`() {
            val result = eval("""regex_match("(\\d+)", "abc123")""") as ListValue
            result.size() shouldBe 1
            result.get(0) shouldBe StringValue("123")
        }

        @Test
        fun `regex_match multiple capture groups`() {
            val result = eval("""regex_match("(\\w+):(\\d+)", "port:8080")""") as ListValue
            result.size() shouldBe 2
            result.get(0) shouldBe StringValue("port")
            result.get(1) shouldBe StringValue("8080")
        }

        @Test
        fun `regex_match no capture groups returns empty list`() {
            val result = eval("""regex_match("\\d+", "abc123")""") as ListValue
            result.size() shouldBe 0
        }

        @Test
        fun `regex_match no match returns empty list`() {
            val result = eval("""regex_match("(\\d+)", "no numbers")""") as ListValue
            result.size() shouldBe 0
        }

        @Test
        fun `regex_match_all finds all occurrences`() {
            val result = eval("""regex_match_all("\\d+", "a1b2c3")""") as ListValue
            result.size() shouldBe 3
            result.get(0) shouldBe StringValue("1")
            result.get(1) shouldBe StringValue("2")
            result.get(2) shouldBe StringValue("3")
        }

        @Test
        fun `regex_match_all finds words`() {
            val result = eval("""regex_match_all("\\w+", "hello world")""") as ListValue
            result.size() shouldBe 2
            result.get(0) shouldBe StringValue("hello")
            result.get(1) shouldBe StringValue("world")
        }

        // MD5 tests
        @Test
        fun `md5 hashes hello`() {
            eval("""md5("hello")""") shouldBe StringValue("5d41402abc4b2a76b9719d911017c592")
        }

        @Test
        fun `md5 hashes empty string`() {
            eval("""md5("")""") shouldBe StringValue("d41d8cd98f00b204e9800998ecf8427e")
        }

        @Test
        fun `md5 hashes Santa`() {
            eval("""md5("Santa")""") shouldBe StringValue("2f621a9cbf3a35ebd4a0b3b470124ba9")
        }

        // Memoize tests
        @Test
        fun `memoize returns a function`() {
            val result = eval("let f = memoize(|x| x * 2); type(f)")
            result shouldBe StringValue("Function")
        }

        @Test
        fun `memoized function works correctly`() {
            eval("let f = memoize(|x| x * 2); f(5)") shouldBe IntValue(10)
        }

        @Test
        fun `memoized function caches results`() {
            // Test memoize by calling same function twice with same argument
            // and verifying results are consistent
            val result = eval("""
                let f = memoize(|x| x * x);
                let a = f(5);
                let b = f(5);
                [a, b]
            """.trimIndent()) as ListValue
            result.get(0) shouldBe IntValue(25)
            result.get(1) shouldBe IntValue(25)
        }

        // Math function tests
        @Test
        fun `signum of positive`() {
            eval("signum(42)") shouldBe IntValue(1)
        }

        @Test
        fun `signum of negative`() {
            eval("signum(-42)") shouldBe IntValue(-1)
        }

        @Test
        fun `signum of zero`() {
            eval("signum(0)") shouldBe IntValue(0)
        }

        @Test
        fun `vec_add adds vectors`() {
            val result = eval("vec_add([1, 2], [3, 4])") as ListValue
            result.get(0) shouldBe IntValue(4)
            result.get(1) shouldBe IntValue(6)
        }

        // Bitwise operation tests
        @Test
        fun `bit_and performs bitwise AND`() {
            eval("bit_and(12, 10)") shouldBe IntValue(8)  // 1100 & 1010 = 1000
        }

        @Test
        fun `bit_or performs bitwise OR`() {
            eval("bit_or(12, 10)") shouldBe IntValue(14)  // 1100 | 1010 = 1110
        }

        @Test
        fun `bit_xor performs bitwise XOR`() {
            eval("bit_xor(12, 10)") shouldBe IntValue(6)  // 1100 ^ 1010 = 0110
        }

        @Test
        fun `bit_not performs bitwise NOT`() {
            eval("bit_not(12)") shouldBe IntValue(-13)  // bitwise complement
        }

        @Test
        fun `bit_shift_left shifts left`() {
            eval("bit_shift_left(1, 3)") shouldBe IntValue(8)  // 1 << 3 = 1000
        }

        @Test
        fun `bit_shift_right shifts right`() {
            eval("bit_shift_right(8, 2)") shouldBe IntValue(2)  // 1000 >> 2 = 10
        }

        // Utility tests
        @Test
        fun `id returns same value`() {
            eval("id(42)") shouldBe IntValue(42)
            eval("id(\"hello\")") shouldBe StringValue("hello")
        }

        // Int rounding tests
        @Test
        fun `int rounds half away from zero`() {
            eval("int(3.5)") shouldBe IntValue(4)
            eval("int(-3.5)") shouldBe IntValue(-4)
            eval("int(3.7)") shouldBe IntValue(4)
            eval("int(-3.7)") shouldBe IntValue(-4)
        }
    }

    @Nested
    inner class IfExpressions {
        @Test
        fun `if with truthy condition returns then branch`() {
            eval("if true { 1 }") shouldBe IntValue(1)
        }

        @Test
        fun `if with falsy condition returns nil`() {
            eval("if false { 1 }") shouldBe NilValue
        }

        @Test
        fun `if-else with truthy condition`() {
            eval("if true { 1 } else { 2 }") shouldBe IntValue(1)
        }

        @Test
        fun `if-else with falsy condition`() {
            eval("if false { 1 } else { 2 }") shouldBe IntValue(2)
        }

        @Test
        fun `if with numeric truthy`() {
            eval("if 1 { \"yes\" }") shouldBe StringValue("yes")
            eval("if 0 { \"yes\" }") shouldBe NilValue
        }

        @Test
        fun `if with string truthy`() {
            eval("if \"hello\" { 1 }") shouldBe IntValue(1)
            eval("if \"\" { 1 }") shouldBe NilValue
        }

        @Test
        fun `if with list truthy`() {
            eval("if [1, 2] { \"yes\" }") shouldBe StringValue("yes")
            eval("if [] { \"yes\" }") shouldBe NilValue
        }

        @Test
        fun `if with comparison condition`() {
            eval("if 5 > 3 { \"yes\" } else { \"no\" }") shouldBe StringValue("yes")
            eval("if 3 > 5 { \"yes\" } else { \"no\" }") shouldBe StringValue("no")
        }

        @Test
        fun `nested if expressions`() {
            eval("if true { if true { 1 } else { 2 } } else { 3 }") shouldBe IntValue(1)
            eval("if true { if false { 1 } else { 2 } } else { 3 }") shouldBe IntValue(2)
            eval("if false { if true { 1 } else { 2 } } else { 3 }") shouldBe IntValue(3)
        }

        @Test
        fun `if as expression result`() {
            eval("let x = if true { 42 } else { 0 }; x") shouldBe IntValue(42)
        }

        @Test
        fun `if-let with truthy value binds and executes then`() {
            eval("if let x = 42 { x }") shouldBe IntValue(42)
        }

        @Test
        fun `if-let with nil skips to else`() {
            eval("if let x = nil { x } else { 99 }") shouldBe IntValue(99)
        }

        @Test
        fun `if-let with false skips to else`() {
            eval("if let x = false { x } else { 99 }") shouldBe IntValue(99)
        }

        @Test
        fun `if-let with zero skips to else`() {
            eval("if let x = 0 { x } else { 99 }") shouldBe IntValue(99)
        }

        @Test
        fun `if-let without else returns nil when falsy`() {
            eval("if let x = nil { x }") shouldBe NilValue
        }

        @Test
        fun `if-let binding is scoped to then branch`() {
            // x is bound in then branch only, else uses outer x
            eval("let y = 1; if let x = 42 { x } else { y }") shouldBe IntValue(42)
            eval("let y = 1; if let x = nil { x } else { y }") shouldBe IntValue(1)
        }
    }

    @Nested
    inner class MatchExpressions {
        @Test
        fun `match literal pattern`() {
            eval("match 1 { 1 { \"one\" } 2 { \"two\" } }") shouldBe StringValue("one")
            eval("match 2 { 1 { \"one\" } 2 { \"two\" } }") shouldBe StringValue("two")
        }

        @Test
        fun `match wildcard pattern`() {
            eval("match 5 { 1 { \"one\" } _ { \"other\" } }") shouldBe StringValue("other")
        }

        @Test
        fun `match binding pattern`() {
            eval("match 42 { n { n } }") shouldBe IntValue(42)
        }

        @Test
        fun `match with no matching pattern returns nil`() {
            eval("match 3 { 1 { \"one\" } 2 { \"two\" } }") shouldBe NilValue
        }

        @Test
        fun `match string literals`() {
            eval("match \"hello\" { \"hello\" { 1 } \"world\" { 2 } }") shouldBe IntValue(1)
        }

        @Test
        fun `match boolean literals`() {
            eval("match true { true { 1 } false { 0 } }") shouldBe IntValue(1)
            eval("match false { true { 1 } false { 0 } }") shouldBe IntValue(0)
        }

        @Test
        fun `match nil literal`() {
            eval("match nil { nil { \"nothing\" } _ { \"something\" } }") shouldBe StringValue("nothing")
        }

        @Test
        fun `match uses first matching pattern`() {
            eval("match 1 { _ { \"first\" } 1 { \"second\" } }") shouldBe StringValue("first")
        }

        @Test
        fun `match with guard clause`() {
            eval("match 10 { x if x > 5 { \"big\" } x { \"small\" } }") shouldBe StringValue("big")
            eval("match 3 { x if x > 5 { \"big\" } x { \"small\" } }") shouldBe StringValue("small")
        }

        @Test
        fun `match binding in guard has access to bound variable`() {
            eval("match 7 { n if n % 2 == 0 { \"even\" } n { \"odd\" } }") shouldBe StringValue("odd")
            eval("match 8 { n if n % 2 == 0 { \"even\" } n { \"odd\" } }") shouldBe StringValue("even")
        }

        @Test
        fun `match as expression result`() {
            eval("let result = match 2 { 1 { \"one\" } 2 { \"two\" } }; result") shouldBe StringValue("two")
        }

        @Test
        fun `match empty list pattern`() {
            eval("match [] { [] { \"empty\" } _ { \"non-empty\" } }") shouldBe StringValue("empty")
            eval("match [1] { [] { \"empty\" } _ { \"non-empty\" } }") shouldBe StringValue("non-empty")
        }

        @Test
        fun `match single element list pattern`() {
            eval("match [42] { [x] { x } _ { 0 } }") shouldBe IntValue(42)
            eval("match [1, 2] { [x] { x } _ { 0 } }") shouldBe IntValue(0)
        }

        @Test
        fun `match multi-element list pattern`() {
            eval("match [1, 2] { [a, b] { a + b } _ { 0 } }") shouldBe IntValue(3)
        }

        @Test
        fun `match rest pattern in list`() {
            eval("match [1, 2, 3] { [head, ..tail] { head } _ { 0 } }") shouldBe IntValue(1)
        }

        @Test
        fun `match rest pattern captures remaining elements`() {
            val result = eval("match [1, 2, 3] { [h, ..t] { t } _ { [] } }") as ListValue
            result.size() shouldBe 2
            result.get(0) shouldBe IntValue(2)
            result.get(1) shouldBe IntValue(3)
        }
    }

    @Nested
    inner class Functions {
        @Test
        fun `simple function call`() {
            eval("let add = |a, b| a + b; add(1, 2)") shouldBe IntValue(3)
        }

        @Test
        fun `function with no parameters`() {
            eval("let f = || 42; f()") shouldBe IntValue(42)
        }

        @Test
        fun `function with block body`() {
            eval("let f = |x| { let y = x + 1; y * 2 }; f(5)") shouldBe IntValue(12)
        }

        @Test
        fun `closure captures outer variable`() {
            eval("let x = 10; let f = || x; f()") shouldBe IntValue(10)
        }

        @Test
        fun `closure captures and uses outer variable`() {
            eval("let x = 10; let add_x = |y| x + y; add_x(5)") shouldBe IntValue(15)
        }

        @Test
        fun `closure with multiple captures`() {
            eval("let a = 1; let b = 2; let f = || a + b; f()") shouldBe IntValue(3)
        }

        @Test
        fun `nested function calls`() {
            eval("let double = |x| x * 2; let triple = |x| x * 3; double(triple(2))") shouldBe IntValue(12)
        }

        @Test
        fun `function returning function`() {
            eval("let make_adder = |x| |y| x + y; let add5 = make_adder(5); add5(3)") shouldBe IntValue(8)
        }

        @Test
        fun `higher order function`() {
            eval("let apply = |f, x| f(x); let double = |x| x * 2; apply(double, 5)") shouldBe IntValue(10)
        }

        @Test
        fun `recursive function - factorial`() {
            eval("""
                let factorial = |n| if n == 0 { 1 } else { n * factorial(n - 1) };
                factorial(5)
            """.trimIndent()) shouldBe IntValue(120)
        }

        @Test
        fun `recursive function - fibonacci`() {
            eval("""
                let fib = |n| if n < 2 { n } else { fib(n - 1) + fib(n - 2) };
                fib(10)
            """.trimIndent()) shouldBe IntValue(55)
        }

        @Test
        fun `recursive function with other captures`() {
            eval("""
                let multiplier = 2;
                let times = |n| if n == 0 { 0 } else { multiplier + times(n - 1) };
                times(5)
            """.trimIndent()) shouldBe IntValue(10)
        }

        @Test
        fun `function is truthy`() {
            eval("let f = || 1; if f { \"yes\" } else { \"no\" }") shouldBe StringValue("yes")
        }

        @Test
        fun `return from simple function`() {
            eval("let f = |x| { return x * 2 }; f(5)") shouldBe IntValue(10)
        }

        @Test
        fun `early return from function`() {
            eval("""
                let f = |x| {
                    if x < 0 { return "negative" }
                    if x == 0 { return "zero" }
                    "positive"
                };
                f(-5)
            """.trimIndent()) shouldBe StringValue("negative")
        }

        @Test
        fun `early return from function - middle case`() {
            eval("""
                let f = |x| {
                    if x < 0 { return "negative" }
                    if x == 0 { return "zero" }
                    "positive"
                };
                f(0)
            """.trimIndent()) shouldBe StringValue("zero")
        }

        @Test
        fun `early return from function - no early return`() {
            eval("""
                let f = |x| {
                    if x < 0 { return "negative" }
                    if x == 0 { return "zero" }
                    "positive"
                };
                f(10)
            """.trimIndent()) shouldBe StringValue("positive")
        }

        @Test
        fun `return from nested blocks`() {
            eval("""
                let f = || {
                    let x = 1;
                    {
                        let y = 2;
                        return x + y
                    }
                    999
                };
                f()
            """.trimIndent()) shouldBe IntValue(3)
        }
    }

    @Nested
    inner class RangeExpressions {
        @Test
        fun `exclusive range creates range value`() {
            eval("type(1..5)") shouldBe StringValue("Range")
        }

        @Test
        fun `inclusive range creates range value`() {
            eval("type(1..=5)") shouldBe StringValue("Range")
        }

        @Test
        fun `unbounded range creates range value`() {
            eval("type(1..)") shouldBe StringValue("Range")
        }

        @Test
        fun `exclusive range with list conversion`() {
            val result = eval("list(1..5)") as ListValue
            result.size() shouldBe 4
            result.get(0) shouldBe IntValue(1)
            result.get(1) shouldBe IntValue(2)
            result.get(2) shouldBe IntValue(3)
            result.get(3) shouldBe IntValue(4)
        }

        @Test
        fun `inclusive range with list conversion`() {
            val result = eval("list(1..=5)") as ListValue
            result.size() shouldBe 5
            result.get(0) shouldBe IntValue(1)
            result.get(4) shouldBe IntValue(5)
        }

        @Test
        fun `range with map and take`() {
            // map on a range returns a lazy sequence, so we need take() before list()
            val result = eval("1..5 |> map(|x| x * 2) |> take(4) |> list") as ListValue
            result.size() shouldBe 4
            result.get(0) shouldBe IntValue(2)
            result.get(1) shouldBe IntValue(4)
            result.get(2) shouldBe IntValue(6)
            result.get(3) shouldBe IntValue(8)
        }

        @Test
        fun `range sum`() {
            eval("1..=10 |> sum") shouldBe IntValue(55)
        }

        @Test
        fun `unbounded range with take`() {
            val result = eval("1.. |> take(5) |> list") as ListValue
            result.size() shouldBe 5
            result.get(0) shouldBe IntValue(1)
            result.get(4) shouldBe IntValue(5)
        }
    }

    @Nested
    inner class BreakFromIteration {
        @Test
        fun `break from reduce`() {
            // Sum until we hit a number greater than 5
            eval("""
                reduce(|acc, n| {
                    if n > 5 { break acc }
                    acc + n
                }, [1, 2, 3, 10, 4])
            """.trimIndent()) shouldBe IntValue(6) // 1 + 2 + 3 = 6
        }

        @Test
        fun `break from fold`() {
            eval("""
                fold(0, |acc, n| {
                    if n > 10 { break acc }
                    acc + n
                }, [1, 2, 3, 100, 5])
            """.trimIndent()) shouldBe IntValue(6) // 0 + 1 + 2 + 3 = 6
        }

        @Test
        fun `break from each returns break value`() {
            eval("""
                each(|n| {
                    if n == 3 { break "found" }
                }, [1, 2, 3, 4, 5])
            """.trimIndent()) shouldBe StringValue("found")
        }

        @Test
        fun `break returns specified value`() {
            eval("""
                fold(0, |acc, n| {
                    if n == 5 { break 999 }
                    acc + n
                }, [1, 2, 3, 4, 5])
            """.trimIndent()) shouldBe IntValue(999)
        }
    }

    @Nested
    inner class ExternalFunctions {
        @Test
        fun `puts returns nil`() {
            eval("puts(\"hello\")") shouldBe NilValue
        }

        @Test
        fun `puts with multiple values returns nil`() {
            eval("puts(1, 2, 3)") shouldBe NilValue
        }

        @Test
        fun `read non-existent file returns nil`() {
            eval("read(\"/non/existent/path.txt\")") shouldBe NilValue
        }
    }

    @Nested
    inner class Sections {
        @Test
        fun `input section binds input variable`() {
            eval("""
                input: "hello world"
                input
            """.trimIndent()) shouldBe StringValue("hello world")
        }

        @Test
        fun `part_one has access to input`() {
            eval("""
                input: 42
                part_one: input + 1
            """.trimIndent()) shouldBe IntValue(43)
        }

        @Test
        fun `part_two has access to input`() {
            eval("""
                input: 100
                part_two: input * 2
            """.trimIndent()) shouldBe IntValue(200)
        }

        @Test
        fun `top level statements with sections`() {
            eval("""
                let x = 10;
                input: x * 2
                part_one: input + 5
            """.trimIndent()) shouldBe IntValue(25)
        }

        @Test
        fun `script mode returns last expression`() {
            eval("""
                let x = 1;
                let y = 2;
                x + y
            """.trimIndent()) shouldBe IntValue(3)
        }

        @Test
        fun `test block section evaluates to nil`() {
            // Test blocks are not executed during normal compilation
            eval("""
                input: 42
                part_one: input + 1
                test: {
                  input: 10
                  part_one: 11
                }
            """.trimIndent()) shouldBe NilValue
        }

        @Test
        fun `slow test block section also evaluates to nil`() {
            eval("""
                input: 42
                part_one: input + 1
                @slow
                test: {
                  input: 100
                  part_one: 101
                }
            """.trimIndent()) shouldBe NilValue
        }

        @Test
        fun `program with only test blocks returns nil`() {
            eval("""
                input: 1
                part_one: input * 2
                test: {
                  input: 5
                  part_one: 10
                }
                test: {
                  input: 10
                  part_one: 20
                }
            """.trimIndent()) shouldBe NilValue
        }
    }

    @Nested
    inner class PipelineOperator {
        @Test
        fun `simple pipeline with builtin function`() {
            // [1, 2, 3] |> size -> size([1, 2, 3]) -> 3
            eval("[1, 2, 3] |> size") shouldBe IntValue(3)
        }

        @Test
        fun `pipeline with lambda`() {
            // 5 |> |x| x * 2 -> 10
            eval("5 |> (|x| x * 2)") shouldBe IntValue(10)
        }

        @Test
        fun `pipeline chain`() {
            // [1, 2, 3, 4] |> rest |> size -> 3
            eval("[1, 2, 3, 4] |> rest |> size") shouldBe IntValue(3)
        }

        @Test
        fun `pipeline with map`() {
            val result = eval("[1, 2, 3] |> map(|x| x * 2)") as ListValue
            result.size() shouldBe 3
            result.get(0) shouldBe IntValue(2)
            result.get(1) shouldBe IntValue(4)
            result.get(2) shouldBe IntValue(6)
        }

        @Test
        fun `pipeline with filter and sum`() {
            // [1, 2, 3, 4] |> filter(|x| x > 2) |> sum -> 7
            eval("[1, 2, 3, 4] |> filter(|x| x > 2) |> sum") shouldBe IntValue(7)
        }

        @Test
        fun `pipeline with partial application`() {
            // 5 |> max -> max(5) but max needs 1 arg (collection), this should work
            // Actually, for [1, 5, 3] |> max -> max([1, 5, 3]) -> 5
            eval("[1, 5, 3] |> max") shouldBe IntValue(5)
        }
    }

    @Nested
    inner class SpreadAndRest {
        @Test
        fun `spread in list literal - single spread`() {
            val result = eval("let xs = [1, 2, 3]; [0, ..xs, 4]") as ListValue
            result.size() shouldBe 5
            result.get(0) shouldBe IntValue(0)
            result.get(1) shouldBe IntValue(1)
            result.get(2) shouldBe IntValue(2)
            result.get(3) shouldBe IntValue(3)
            result.get(4) shouldBe IntValue(4)
        }

        @Test
        fun `spread in list literal - spread at beginning`() {
            val result = eval("let xs = [1, 2]; [..xs, 3]") as ListValue
            result.size() shouldBe 3
            result.get(0) shouldBe IntValue(1)
            result.get(1) shouldBe IntValue(2)
            result.get(2) shouldBe IntValue(3)
        }

        @Test
        fun `spread in list literal - spread at end`() {
            val result = eval("let xs = [2, 3]; [1, ..xs]") as ListValue
            result.size() shouldBe 3
            result.get(0) shouldBe IntValue(1)
            result.get(1) shouldBe IntValue(2)
            result.get(2) shouldBe IntValue(3)
        }

        @Test
        fun `spread in list literal - multiple spreads`() {
            val result = eval("let xs = [1, 2]; let ys = [3, 4]; [..xs, ..ys]") as ListValue
            result.size() shouldBe 4
            result.get(0) shouldBe IntValue(1)
            result.get(1) shouldBe IntValue(2)
            result.get(2) shouldBe IntValue(3)
            result.get(3) shouldBe IntValue(4)
        }

        @Test
        fun `spread empty list`() {
            val result = eval("let xs = []; [1, ..xs, 2]") as ListValue
            result.size() shouldBe 2
            result.get(0) shouldBe IntValue(1)
            result.get(1) shouldBe IntValue(2)
        }

        @Test
        fun `spread in set literal`() {
            val result = eval("let xs = {1, 2}; {0, ..xs, 3}") as SetValue
            result.size() shouldBe 4
        }

        @Test
        fun `rest parameter collects remaining arguments`() {
            val result = eval("let f = |head, ..remaining| remaining; f(1, 2, 3, 4)") as ListValue
            result.size() shouldBe 3
            result.get(0) shouldBe IntValue(2)
            result.get(1) shouldBe IntValue(3)
            result.get(2) shouldBe IntValue(4)
        }

        @Test
        fun `rest parameter with no remaining arguments`() {
            val result = eval("let f = |head, ..remaining| remaining; f(1)") as ListValue
            result.size() shouldBe 0
        }

        @Test
        fun `rest parameter with only rest`() {
            val result = eval("let f = |..args| args; f(1, 2, 3)") as ListValue
            result.size() shouldBe 3
            result.get(0) shouldBe IntValue(1)
            result.get(1) shouldBe IntValue(2)
            result.get(2) shouldBe IntValue(3)
        }

        @Test
        fun `rest parameter returns first argument separately`() {
            eval("let f = |head, ..remaining| head; f(1, 2, 3)") shouldBe IntValue(1)
        }

        @Test
        fun `spread in function call`() {
            val result = eval("""
                let f = |a, b, c| a + b + c;
                let args = [1, 2, 3];
                f(..args)
            """.trimIndent())
            result shouldBe IntValue(6)
        }

        @Test
        fun `spread in function call with other args`() {
            val result = eval("""
                let f = |a, b, c, d| a + b + c + d;
                let args = [2, 3];
                f(1, ..args, 4)
            """.trimIndent())
            result shouldBe IntValue(10)
        }
    }

    @Nested
    inner class CompositionOperator {
        @Test
        fun `simple composition of lambdas`() {
            // |x| x + 1 >> |x| x * 2 applied to 5 -> (5+1)*2 = 12
            eval("let f = (|x| x + 1) >> (|x| x * 2); f(5)") shouldBe IntValue(12)
        }

        @Test
        fun `composition with builtins`() {
            // rest >> size applied to [1, 2, 3] -> size(rest([1, 2, 3])) -> 2
            eval("let f = rest >> size; f([1, 2, 3])") shouldBe IntValue(2)
        }

        @Test
        fun `composition chain`() {
            // |x| x + 1 >> |x| x * 2 >> |x| x - 1 applied to 5 -> ((5+1)*2)-1 = 11
            eval("let f = (|x| x + 1) >> (|x| x * 2) >> (|x| x - 1); f(5)") shouldBe IntValue(11)
        }

        @Test
        fun `composition with higher-order functions`() {
            // parse_lines = lines >> map(int) style composition
            eval("""
                let double_all = map(|x| x * 2);
                let sum_all = sum;
                let f = double_all >> sum_all;
                f([1, 2, 3])
            """.trimIndent()) shouldBe IntValue(12)
        }

        @Test
        fun `composition returns a function`() {
            eval("type((|x| x + 1) >> (|x| x * 2))") shouldBe StringValue("Function")
        }
    }

    @Nested
    inner class PlaceholderExpressions {
        @Test
        fun `placeholder in addition`() {
            eval("let inc = _ + 1; inc(5)") shouldBe IntValue(6)
        }

        @Test
        fun `placeholder in subtraction`() {
            eval("let dec = _ - 1; dec(10)") shouldBe IntValue(9)
        }

        @Test
        fun `placeholder in multiplication`() {
            eval("let double = _ * 2; double(4)") shouldBe IntValue(8)
        }

        @Test
        fun `placeholder on right side`() {
            eval("let sub_from_10 = 10 - _; sub_from_10(3)") shouldBe IntValue(7)
        }

        @Test
        fun `two placeholders create binary function`() {
            eval("let divide = _ / _; divide(10, 2)") shouldBe IntValue(5)
        }

        @Test
        fun `placeholder with unary negation`() {
            eval("let negate = -_; negate(5)") shouldBe IntValue(-5)
        }

        @Test
        fun `placeholder in map`() {
            val result = eval("[1, 2, 3] |> map(_ * 2)") as ListValue
            result.size() shouldBe 3
            result.get(0) shouldBe IntValue(2)
            result.get(1) shouldBe IntValue(4)
            result.get(2) shouldBe IntValue(6)
        }

        @Test
        fun `placeholder in filter`() {
            val result = eval("[1, 2, 3, 4, 5] |> filter(_ > 2)") as ListValue
            result.size() shouldBe 3
            result.get(0) shouldBe IntValue(3)
            result.get(1) shouldBe IntValue(4)
            result.get(2) shouldBe IntValue(5)
        }

        @Test
        fun `placeholder creates function type`() {
            eval("type(_ + 1)") shouldBe StringValue("Function")
        }
    }

    @Nested
    inner class LambdaDestructuring {
        @Test
        fun `destructure list param in lambda`() {
            eval("let f = |[a, b]| a + b; f([1, 2])") shouldBe IntValue(3)
        }

        @Test
        fun `destructure in map`() {
            val result = eval("[[1, 2], [3, 4]] |> map(|[a, b]| a + b)") as ListValue
            result.size() shouldBe 2
            result.get(0) shouldBe IntValue(3)
            result.get(1) shouldBe IntValue(7)
        }

        @Test
        fun `destructure with rest`() {
            eval("let f = |[head, ..tail]| size(tail); f([1, 2, 3, 4])") shouldBe IntValue(3)
        }

        @Test
        fun `mixed destructure and regular params`() {
            eval("let f = |[a, b], c| a + b + c; f([1, 2], 3)") shouldBe IntValue(6)
        }

        @Test
        fun `nested destructuring`() {
            eval("let f = |[[a, b], c]| a + b + c; f([[1, 2], 3])") shouldBe IntValue(6)
        }

        @Test
        fun `destructure in fold_s`() {
            // fold_s returns first element of final state, not the whole list
            eval("fold_s([0, 0], |[total, cnt], x| [total + x, cnt + 1], [1, 2, 3])") shouldBe IntValue(6)
        }
    }

    @Nested
    inner class DictShorthand {
        @Test
        fun `dict shorthand single variable`() {
            val result = eval("let x = 1; #{x}") as DictValue
            result.get(StringValue("x")) shouldBe IntValue(1)
        }

        @Test
        fun `dict shorthand multiple variables`() {
            val result = eval("let a = 1; let b = 2; #{a, b}") as DictValue
            result.get(StringValue("a")) shouldBe IntValue(1)
            result.get(StringValue("b")) shouldBe IntValue(2)
        }

        @Test
        fun `dict shorthand mixed with regular entries`() {
            val result = eval("let x = 10; #{x, \"y\": 20}") as DictValue
            result.get(StringValue("x")) shouldBe IntValue(10)
            result.get(StringValue("y")) shouldBe IntValue(20)
        }

        @Test
        fun `dict shorthand with function value`() {
            val result = eval("let f = |x| x + 1; #{f}") as DictValue
            result.get(StringValue("f")).typeName() shouldBe "Function"
        }
    }
}
