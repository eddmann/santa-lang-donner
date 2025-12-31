package santa.runtime.value

import com.ibm.icu.text.BreakIterator
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.PersistentSet

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
}

/** 64-bit floating-point value (LANG.txt §3.2) */
data class DecimalValue(val value: Double) : Value {
    override fun isTruthy(): Boolean = value != 0.0
    override fun isHashable(): Boolean = true
    override fun typeName(): String = "Decimal"
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
}

/** Boolean value (LANG.txt §2.5) */
data class BoolValue(val value: Boolean) : Value {
    override fun isTruthy(): Boolean = value
    override fun isHashable(): Boolean = true
    override fun typeName(): String = "Boolean"
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
}

/** Unordered collection of unique elements (LANG.txt §3.6) */
data class SetValue(val elements: PersistentSet<Value>) : Value {
    override fun isTruthy(): Boolean = elements.isNotEmpty()
    override fun isHashable(): Boolean = true
    override fun typeName(): String = "Set"

    fun size(): Int = elements.size
    fun contains(value: Value): Boolean = elements.contains(value)
}

/** Unordered key-value mapping (LANG.txt §3.7) */
data class DictValue(val entries: PersistentMap<Value, Value>) : Value {
    override fun isTruthy(): Boolean = entries.isNotEmpty()
    override fun isHashable(): Boolean = false  // Dictionaries are not hashable
    override fun typeName(): String = "Dictionary"

    fun size(): Int = entries.size

    /** Get value for key, or NilValue if not found (LANG.txt §3.7) */
    fun get(key: Value): Value = entries[key] ?: NilValue
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

    companion object {
        /** Create exclusive range: start..end */
        fun exclusive(start: Long, end: Long): RangeValue {
            val step = if (start <= end) 1L else -1L
            return RangeValue(start, end, step)
        }

        /** Create inclusive range: start..=end */
        fun inclusive(start: Long, end: Long): RangeValue {
            val step = if (start <= end) 1L else -1L
            val endExclusive = if (step > 0) end + 1 else end - 1
            return RangeValue(start, endExclusive, step)
        }

        /** Create unbounded range: start.. */
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
    }
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

    override fun isTruthy(): Boolean = true  // Always truthy
    override fun isHashable(): Boolean = false  // Not hashable
    override fun typeName(): String = "Function"

    /** Invoke this function with the given arguments. */
    abstract fun invoke(args: List<Value>): Value

    /** Returns true if this function accepts variable number of arguments. */
    fun isVariadic(): Boolean = arity < 0
}
