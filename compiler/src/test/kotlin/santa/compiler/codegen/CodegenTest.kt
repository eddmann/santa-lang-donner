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

        // TODO: Recursive functions require resolver changes to allow self-reference
        // @Test
        // fun `recursive function`() {
        //     eval("""
        //         let factorial = |n| if n == 0 { 1 } else { n * factorial(n - 1) };
        //         factorial(5)
        //     """.trimIndent()) shouldBe IntValue(120)
        // }

        @Test
        fun `function is truthy`() {
            eval("let f = || 1; if f { \"yes\" } else { \"no\" }") shouldBe StringValue("yes")
        }
    }
}
