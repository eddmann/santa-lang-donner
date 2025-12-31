package santa.runtime.value

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Tests for grapheme-cluster string indexing per LANG.txt §3.3.
 *
 * String indexing uses grapheme clusters (visual characters), not bytes or code points.
 * This means emoji sequences like 👨‍👩‍👧‍👦 count as a single index position.
 */
class StringValueGraphemeTest {

    @Nested
    inner class GraphemeLength {
        @Test
        fun `ascii string length`() {
            assertEquals(5, StringValue("hello").graphemeLength())
        }

        @Test
        fun `empty string length`() {
            assertEquals(0, StringValue("").graphemeLength())
        }

        @Test
        fun `simple emoji length`() {
            // Two separate emoji: heart and pizza
            assertEquals(2, StringValue("❤🍕").graphemeLength())
        }

        @Test
        fun `family emoji is single grapheme cluster`() {
            // Family emoji (man, woman, girl, boy) with ZWJ joiners is one grapheme
            assertEquals(1, StringValue("👨‍👩‍👧‍👦").graphemeLength())
        }

        @Test
        fun `flag emoji is single grapheme cluster`() {
            // Regional indicator symbols form one grapheme
            assertEquals(1, StringValue("🇺🇸").graphemeLength())
        }

        @Test
        fun `combined accent is single grapheme`() {
            // 'e' with combining acute accent is one grapheme
            assertEquals(1, StringValue("é").graphemeLength()) // composed: U+0065 U+0301
        }

        @Test
        fun `skin tone emoji is single grapheme`() {
            // Hand with skin tone modifier is one grapheme
            assertEquals(1, StringValue("👋🏽").graphemeLength())
        }
    }

    @Nested
    inner class GraphemeIndexing {
        @Test
        fun `index ascii string`() {
            val str = StringValue("hello")
            assertEquals("h", str.graphemeAt(0))
            assertEquals("e", str.graphemeAt(1))
            assertEquals("o", str.graphemeAt(4))
        }

        @Test
        fun `index emoji string`() {
            val str = StringValue("❤🍕")
            assertEquals("❤", str.graphemeAt(0))
            assertEquals("🍕", str.graphemeAt(1))
        }

        @Test
        fun `index family emoji returns entire cluster`() {
            val str = StringValue("👨‍👩‍👧‍👦")
            assertEquals("👨‍👩‍👧‍👦", str.graphemeAt(0))
        }

        @Test
        fun `negative index from end`() {
            val str = StringValue("hello")
            assertEquals("o", str.graphemeAt(-1))
            assertEquals("l", str.graphemeAt(-2))
            assertEquals("h", str.graphemeAt(-5))
        }

        @Test
        fun `out of bounds returns null`() {
            val str = StringValue("hello")
            assertEquals(null, str.graphemeAt(5) as String?)
            assertEquals(null, str.graphemeAt(10) as String?)
            assertEquals(null, str.graphemeAt(-6) as String?)
        }
    }

    @Nested
    inner class GraphemeSlicing {
        @Test
        fun `slice ascii string`() {
            val str = StringValue("hello")
            assertEquals("ell", str.graphemeSlice(1, 4))
        }

        @Test
        fun `slice to end`() {
            val str = StringValue("hello")
            assertEquals("llo", str.graphemeSlice(2, 5))
        }

        @Test
        fun `slice from start`() {
            val str = StringValue("hello")
            assertEquals("hel", str.graphemeSlice(0, 3))
        }

        @Test
        fun `slice emoji string`() {
            val str = StringValue("a❤🍕b")
            assertEquals("❤🍕", str.graphemeSlice(1, 3))
        }

        @Test
        fun `slice with negative indices`() {
            val str = StringValue("hello")
            assertEquals("ell", str.graphemeSlice(-4, -1))
        }

        @Test
        fun `empty slice`() {
            val str = StringValue("hello")
            assertEquals("", str.graphemeSlice(2, 2))
        }
    }
}
