package santa.runtime.value

import com.ibm.icu.text.BreakIterator
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList

/**
 * Sealed interface representing all runtime values in santa-lang.
 *
 * Values are the fundamental data types that flow through compiled santa programs.
 * Each value type defines its own truthiness, hashability, and type name semantics
 * according to the LANG.txt specification.
 */
sealed interface Value {
    /** Returns true if this value is truthy (LANG.txt §14.1) */
    fun isTruthy(): Boolean

    /** Returns true if this value can be used as a Set element or Dictionary key (LANG.txt §3.11) */
    fun isHashable(): Boolean

    /** Returns the type name for error messages and debugging */
    fun typeName(): String
}

/** 64-bit signed integer value (LANG.txt §3.1) */
data class IntValue(val value: Long) : Value {
    constructor(value: Int) : this(value.toLong())

    override fun isTruthy(): Boolean = value != 0L
    override fun isHashable(): Boolean = true
    override fun typeName(): String = "Integer"

    companion object {
        /** Factory method for bytecode generation - boxes a primitive long. */
        @JvmStatic
        fun box(value: Long): IntValue = IntValue(value)
    }
}

/** 64-bit floating-point value (LANG.txt §3.2) */
data class DecimalValue(val value: Double) : Value {
    override fun isTruthy(): Boolean = value != 0.0
    override fun isHashable(): Boolean = true
    override fun typeName(): String = "Decimal"

    companion object {
        /** Factory method for bytecode generation - boxes a primitive double. */
        @JvmStatic
        fun box(value: Double): DecimalValue = DecimalValue(value)
    }
}

/** UTF-8 encoded string with grapheme-cluster indexing (LANG.txt §3.3) */
data class StringValue(val value: String) : Value {
    override fun isTruthy(): Boolean = value.isNotEmpty()
    override fun isHashable(): Boolean = true
    override fun typeName(): String = "String"

    /** Returns the number of grapheme clusters in this string */
    fun graphemeLength(): Int {
        if (value.isEmpty()) return 0
        val iter = BreakIterator.getCharacterInstance()
        iter.setText(value)
        var count = 0
        while (iter.next() != BreakIterator.DONE) {
            count++
        }
        return count
    }

    /**
     * Returns the grapheme cluster at the given index, or null if out of bounds.
     * Negative indices count from the end (-1 is the last grapheme).
     */
    fun graphemeAt(index: Int): String? {
        val graphemes = toGraphemeList()
        val normalizedIndex = if (index < 0) graphemes.size + index else index
        return graphemes.getOrNull(normalizedIndex)
    }

    /**
     * Returns a substring from startIndex (inclusive) to endIndex (exclusive),
     * where indices refer to grapheme cluster positions.
     * Negative indices count from the end.
     */
    fun graphemeSlice(startIndex: Int, endIndex: Int): String {
        val graphemes = toGraphemeList()
        val normalizedStart = if (startIndex < 0) graphemes.size + startIndex else startIndex
        val normalizedEnd = if (endIndex < 0) graphemes.size + endIndex else endIndex
        val clampedStart = normalizedStart.coerceIn(0, graphemes.size)
        val clampedEnd = normalizedEnd.coerceIn(0, graphemes.size)
        return if (clampedStart >= clampedEnd) {
            ""
        } else {
            graphemes.subList(clampedStart, clampedEnd).joinToString("")
        }
    }

    private fun toGraphemeList(): List<String> {
        if (value.isEmpty()) return emptyList()
        val iter = BreakIterator.getCharacterInstance()
        iter.setText(value)
        val graphemes = mutableListOf<String>()
        var start = 0
        var end = iter.next()
        while (end != BreakIterator.DONE) {
            graphemes.add(value.substring(start, end))
            start = end
            end = iter.next()
        }
        return graphemes
    }

    companion object {
        /** Factory method for bytecode generation - boxes a String. */
        @JvmStatic
        fun box(value: String): StringValue = StringValue(value)
    }
}

/** Boolean value (LANG.txt §2.5) */
data class BoolValue(val value: Boolean) : Value {
    override fun isTruthy(): Boolean = value
    override fun isHashable(): Boolean = true
    override fun typeName(): String = "Boolean"

    companion object {
        /** Singleton true value for bytecode generation. */
        @JvmField
        val TRUE: BoolValue = BoolValue(true)

        /** Singleton false value for bytecode generation. */
        @JvmField
        val FALSE: BoolValue = BoolValue(false)

        /** Factory method for bytecode generation - returns cached singleton. */
        @JvmStatic
        fun box(value: Boolean): BoolValue = if (value) TRUE else FALSE
    }
}

/** Singleton representing the absence of a value (LANG.txt §3.10) */
data object NilValue : Value {
    override fun isTruthy(): Boolean = false
    override fun isHashable(): Boolean = true
    override fun typeName(): String = "Nil"
}

/** Ordered, heterogeneous, persistent list (LANG.txt §3.5) */
data class ListValue(val elements: PersistentList<Value>) : Value {
    override fun isTruthy(): Boolean = elements.isNotEmpty()
    override fun isHashable(): Boolean = elements.all { it.isHashable() }
    override fun typeName(): String = "List"

    fun size(): Int = elements.size

    /** Get element at index. Negative indices count from end. Out of bounds returns NilValue. */
    fun get(index: Int): Value {
        val normalizedIndex = if (index < 0) elements.size + index else index
        return if (normalizedIndex in 0 until elements.size) {
            elements[normalizedIndex]
        } else {
            NilValue
        }
    }

    /** Concatenate two lists: [1, 2] + [3, 4] -> [1, 2, 3, 4] */
    fun concat(other: ListValue): ListValue =
        ListValue(elements.addAll(other.elements))

    /** Repeat list n times: [1, 2] * 3 -> [1, 2, 1, 2, 1, 2] */
    fun repeat(n: Int): ListValue {
        if (n <= 0) return ListValue(persistentListOf())
        var result = elements
        repeat(n - 1) { result = result.addAll(elements) }
        return ListValue(result)
    }

    /** Slice from startIndex (inclusive) to endIndex (exclusive). Negative indices count from end. */
    fun slice(startIndex: Int, endIndex: Int): ListValue {
        val normalizedStart = if (startIndex < 0) elements.size + startIndex else startIndex
        val normalizedEnd = if (endIndex < 0) elements.size + endIndex else endIndex
        val clampedStart = normalizedStart.coerceIn(0, elements.size)
        val clampedEnd = normalizedEnd.coerceIn(0, elements.size)
        return if (clampedStart >= clampedEnd) {
            ListValue(persistentListOf())
        } else {
            ListValue(elements.subList(clampedStart, clampedEnd).toPersistentList())
        }
    }

    companion object {
        /** Factory method for bytecode generation - wraps a PersistentList. */
        @JvmStatic
        fun box(elements: PersistentList<Value>): ListValue = ListValue(elements)
    }
}

/** Unordered collection of unique elements (LANG.txt §3.6) */
data class SetValue(val elements: PersistentSet<Value>) : Value {
    override fun isTruthy(): Boolean = elements.isNotEmpty()
    override fun isHashable(): Boolean = true
    override fun typeName(): String = "Set"

    fun size(): Int = elements.size
    fun contains(value: Value): Boolean = elements.contains(value)

    /** Add an element to the set (LANG.txt §3.11 - enforces hashability) */
    fun add(value: Value): SetValue {
        require(value.isHashable()) {
            "Cannot add ${value.typeName()} to Set: value is not hashable"
        }
        return SetValue(elements.add(value))
    }

    /** Union of two sets: {1, 2} + {2, 3} -> {1, 2, 3} */
    fun union(other: SetValue): SetValue =
        SetValue(elements.addAll(other.elements))

    /** Difference of two sets: {1, 2, 3} - {2} -> {1, 3} */
    fun difference(other: SetValue): SetValue =
        SetValue(elements.removeAll(other.elements))

    companion object {
        @JvmStatic
        fun box(elements: PersistentSet<Value>): SetValue = SetValue(elements)
    }
}

/** Unordered key-value mapping (LANG.txt §3.7) */
data class DictValue(val entries: PersistentMap<Value, Value>) : Value {
    override fun isTruthy(): Boolean = entries.isNotEmpty()
    override fun isHashable(): Boolean = false  // Dictionaries are not hashable
    override fun typeName(): String = "Dictionary"

    fun size(): Int = entries.size

    /** Get value for key, or NilValue if not found (LANG.txt §3.7) */
    fun get(key: Value): Value = entries[key] ?: NilValue

    /** Put a key-value pair (LANG.txt §3.11 - enforces key hashability) */
    fun put(key: Value, value: Value): DictValue {
        require(key.isHashable()) {
            "Cannot use ${key.typeName()} as Dictionary key: value is not hashable"
        }
        return DictValue(entries.put(key, value))
    }

    /** Merge two dictionaries (right takes precedence): #{a: 1} + #{b: 2} -> #{a: 1, b: 2} */
    fun merge(other: DictValue): DictValue =
        DictValue(entries.putAll(other.entries))

    /** Get all keys as a list (order not specified) */
    fun keys(): ListValue =
        ListValue(entries.keys.toPersistentList())

    /** Get all values as a list (order not specified) */
    fun values(): ListValue =
        ListValue(entries.values.toPersistentList())

    companion object {
        @JvmStatic
        fun box(entries: PersistentMap<Value, Value>): DictValue = DictValue(entries)
    }
}

/**
 * Range of integers with lazy evaluation (LANG.txt §3.4).
 *
 * Ranges can be:
 * - Exclusive: start..end (end not included)
 * - Inclusive: start..=end (end included)
 * - Unbounded: start.. (infinite sequence)
 */
class RangeValue private constructor(
    private val start: Long,
    private val endExclusive: Long?, // null for unbounded
    private val step: Long
) : Value {

    override fun isTruthy(): Boolean = true  // Lazy sequences always truthy
    override fun isHashable(): Boolean = false  // Lazy sequences not hashable
    override fun typeName(): String = "Range"

    fun isUnbounded(): Boolean = endExclusive == null

    /** Get the start of the range. */
    fun getStart(): Long = start

    /** Get the exclusive end of the range (null for unbounded). */
    fun getEndExclusive(): Long? = endExclusive

    /** Convert bounded range to list. Throws for unbounded ranges. */
    fun toList(): List<Long> {
        require(endExclusive != null) { "Cannot convert unbounded range to list" }
        return generateSequence(start) { it + step }
            .takeWhile { if (step > 0) it < endExclusive else it > endExclusive }
            .toList()
    }

    /** Take first n elements from range. */
    fun take(n: Int): List<Long> {
        return generateSequence(start) { it + step }
            .take(n)
            .toList()
    }

    /** Get a sequence of IntValue elements for zip/iteration. */
    fun asSequence(): Sequence<Value> {
        val baseSeq = generateSequence(start) { it + step }
        val boundedSeq = if (endExclusive != null) {
            baseSeq.takeWhile { if (step > 0) it < endExclusive else it > endExclusive }
        } else {
            baseSeq
        }
        return boundedSeq.map { IntValue(it) }
    }

    companion object {
        /** Create exclusive range: start..end */
        @JvmStatic
        fun exclusive(start: Long, end: Long): RangeValue {
            val step = if (start <= end) 1L else -1L
            return RangeValue(start, end, step)
        }

        /** Create inclusive range: start..=end */
        @JvmStatic
        fun inclusive(start: Long, end: Long): RangeValue {
            val step = if (start <= end) 1L else -1L
            val endExclusive = if (step > 0) end + 1 else end - 1
            return RangeValue(start, endExclusive, step)
        }

        /** Create unbounded range: start.. */
        @JvmStatic
        fun unbounded(start: Long): RangeValue {
            return RangeValue(start, null, 1L)
        }
    }
}

/**
 * Lazy sequence with deferred computation (LANG.txt §3.8).
 *
 * Provides infinite sequences computed on-demand.
 */
class LazySequenceValue private constructor(
    private val generator: () -> Sequence<Value>
) : Value {

    override fun isTruthy(): Boolean = true  // Always truthy
    override fun isHashable(): Boolean = false  // Not hashable
    override fun typeName(): String = "LazySequence"

    /** Take first n elements. */
    fun take(n: Int): List<Value> = generator().take(n).toList()

    companion object {
        /** Create sequence by repeatedly applying function: iterate(f, init) -> init, f(init), f(f(init)), ... */
        fun iterate(initial: Value, f: (Value) -> Value): LazySequenceValue {
            return LazySequenceValue {
                generateSequence(initial) { f(it) }
            }
        }

        /** Create sequence repeating same value infinitely: repeat(x) -> x, x, x, ... */
        fun repeat(value: Value): LazySequenceValue {
            return LazySequenceValue {
                generateSequence { value }
            }
        }

        /** Create sequence cycling through list: cycle([a, b]) -> a, b, a, b, ... */
        fun cycle(values: List<Value>): LazySequenceValue {
            return LazySequenceValue {
                if (values.isEmpty()) {
                    emptySequence()
                } else {
                    generateSequence(0) { (it + 1) % values.size }
                        .map { values[it] }
                }
            }
        }

        /**
         * Zip multiple collections into tuples (LANG.txt §11.12).
         *
         * Stops at shortest collection.
         * Returns List if ANY collection is finite, LazySequence if ALL are infinite.
         */
        fun zip(collections: List<Value>): Value {
            if (collections.isEmpty()) return ListValue(persistentListOf())

            // Check if all collections are infinite (unbounded ranges or lazy sequences)
            val allInfinite = collections.all { isInfinite(it) }

            // Convert each collection to a sequence
            val sequences = collections.map { toSequence(it) }

            // Zip the sequences together
            val zippedSequence = zipSequences(sequences)

            return if (allInfinite) {
                // All infinite -> return LazySequence
                LazySequenceValue { zippedSequence }
            } else {
                // At least one finite -> materialize to List
                ListValue(zippedSequence.toList().toPersistentList())
            }
        }

        private fun isInfinite(value: Value): Boolean = when (value) {
            is RangeValue -> value.isUnbounded()
            is LazySequenceValue -> true  // Lazy sequences are conceptually infinite
            else -> false
        }

        private fun toSequence(value: Value): Sequence<Value> = when (value) {
            is ListValue -> value.elements.asSequence()
            is SetValue -> value.elements.asSequence()
            is RangeValue -> value.asSequence()
            is LazySequenceValue -> value.generator()
            is StringValue -> {
                // Convert string to sequence of single-character strings (grapheme clusters)
                val graphemes = mutableListOf<Value>()
                val iter = com.ibm.icu.text.BreakIterator.getCharacterInstance()
                iter.setText(value.value)
                var start = 0
                var end = iter.next()
                while (end != com.ibm.icu.text.BreakIterator.DONE) {
                    graphemes.add(StringValue(value.value.substring(start, end)))
                    start = end
                    end = iter.next()
                }
                graphemes.asSequence()
            }
            else -> emptySequence()
        }

        private fun zipSequences(sequences: List<Sequence<Value>>): Sequence<Value> = sequence {
            val iterators = sequences.map { it.iterator() }
            while (iterators.all { it.hasNext() }) {
                val tuple = iterators.map { it.next() }
                yield(ListValue(tuple.toPersistentList()))
            }
        }

        /** Create a LazySequenceValue from an existing sequence. */
        fun fromSequence(seq: Sequence<Value>): LazySequenceValue {
            return LazySequenceValue { seq }
        }
    }

    // Expose generator for zip to use
    internal fun generator(): Sequence<Value> = generator.invoke()
}

/**
 * First-class function value (LANG.txt §3.9).
 *
 * Functions are compiled to synthetic classes implementing this interface.
 * Captures are stored as fields and invocation happens via the invoke method.
 *
 * @property arity The number of required arguments, or -1 for variadic functions
 */
abstract class FunctionValue(val arity: Int = 0) : Value {
    /**
     * Self-reference for recursive functions.
     * Set after construction for self-referential closures.
     */
    @JvmField
    protected var selfRef: Value? = null

    override fun isTruthy(): Boolean = true  // Always truthy
    override fun isHashable(): Boolean = false  // Not hashable
    override fun typeName(): String = "Function"

    /** Invoke this function with the given arguments. */
    abstract fun invoke(args: List<Value>): Value

    /** Returns true if this function accepts variable number of arguments. */
    fun isVariadic(): Boolean = arity < 0

    /**
     * Sets the self-reference for recursive functions.
     * Called by codegen after the function is stored in its binding slot.
     */
    open fun setSelfRef(self: Value) {
        this.selfRef = self
    }
}

/**
 * Memoized function wrapper (LANG.txt §11.16).
 *
 * Wraps another function and caches results based on argument lists.
 */
class MemoizedFunctionValue(private val wrapped: FunctionValue) : FunctionValue(wrapped.arity) {
    private val cache = mutableMapOf<List<Value>, Value>()

    override fun invoke(args: List<Value>): Value {
        return cache.getOrPut(args) { wrapped.invoke(args) }
    }
}

/**
 * Composed function: f >> g (LANG.txt §4.8).
 *
 * Applies first function then second: (f >> g)(x) = g(f(x))
 */
class ComposedFunctionValue(
    private val first: FunctionValue,
    private val second: FunctionValue
) : FunctionValue(first.arity) {

    override fun invoke(args: List<Value>): Value {
        val intermediate = first.invoke(args)
        return second.invoke(listOf(intermediate))
    }
}

/**
 * Control flow exception for early return from functions (LANG.txt §7.3).
 *
 * This exception is thrown by compiled `return` statements and caught at
 * function boundaries to implement non-local returns.
 *
 * Using an exception provides clean stack unwinding through nested blocks.
 * It extends Throwable directly (not Exception) and has no stack trace
 * for performance since it's used for normal control flow.
 */
class ReturnException(@JvmField val value: Value) : Throwable(null, null, false, false)

/**
 * Control flow exception for early break from iteration (LANG.txt §7.4).
 *
 * This exception is thrown by compiled `break` statements and caught by
 * iteration builtins (reduce, fold, fold_s, scan, each) to implement early exit.
 *
 * The break value becomes the result of the enclosing iteration expression.
 */
class BreakException(@JvmField val value: Value) : Throwable(null, null, false, false)
