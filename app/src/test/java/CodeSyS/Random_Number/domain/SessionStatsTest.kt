package CodeSyS.Random_Number.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionStatsTest {

    @Test
    fun `empty list - returns EMPTY stats`() {
        val stats = computeSessionStats(emptyList())

        assertEquals(0, stats.count)
        assertEquals(0, stats.unique)
        assertEquals(0, stats.duplicates)
        assertNull(stats.min)
        assertNull(stats.max)
        assertNull(stats.average)
        assertNull(stats.median)
        assertNull(stats.mostFrequent)
        assertNull(stats.leastFrequent)
    }

    @Test
    fun `single value - count, range and mode`() {
        val stats = computeSessionStats(listOf(7))

        assertEquals(1, stats.count)
        assertEquals(1, stats.unique)
        assertEquals(7, stats.min)
        assertEquals(7, stats.max)
        assertEquals(7.0, stats.average!!, 0.0001)
        assertEquals(7.0, stats.median!!, 0.0001)
        assertEquals(SessionStats.Frequent(7, 1), stats.mostFrequent)
    }

    @Test
    fun `two values - median is the average of the middle pair`() {
        val stats = computeSessionStats(listOf(1, 5))

        assertEquals(3.0, stats.median!!, 0.0001)
        assertEquals(3.0, stats.average!!, 0.0001)
    }

    @Test
    fun `odd count - median is the middle value`() {
        val stats = computeSessionStats(listOf(9, 1, 5))

        assertEquals(5.0, stats.median!!, 0.0001)
        assertEquals(1, stats.min)
        assertEquals(9, stats.max)
    }

    @Test
    fun `duplicates - counted and reflected in unique`() {
        val stats = computeSessionStats(listOf(3, 3, 3, 4, 4, 5))

        assertEquals(6, stats.count)
        assertEquals(3, stats.unique)
        assertEquals(3, stats.duplicates)
    }

    @Test
    fun `most and least frequent - ties resolved by smaller value`() {
        // 1 и 2 встречаются одинаково часто, 3 — реже всех.
        val stats = computeSessionStats(listOf(1, 1, 2, 2, 3))

        assertEquals(SessionStats.Frequent(1, 2), stats.mostFrequent)
        assertEquals(SessionStats.Frequent(3, 1), stats.leastFrequent)
    }

    @Test
    fun `average - rounded to two decimals`() {
        val stats = computeSessionStats(listOf(1, 1, 2))

        assertEquals(1.33, stats.average!!, 0.0001)
    }

    @Test
    fun `negative values - min max and average handled`() {
        val stats = computeSessionStats(listOf(-10, -4, 0))

        assertEquals(-10, stats.min)
        assertEquals(0, stats.max)
        assertEquals(-4.67, stats.average!!, 0.0001)
    }

    @Test
    fun `all equal values - min equals max and mode is the value`() {
        val stats = computeSessionStats(List(10) { 42 })

        assertEquals(42, stats.min)
        assertEquals(42, stats.max)
        assertEquals(1, stats.unique)
        assertEquals(9, stats.duplicates)
        assertEquals(SessionStats.Frequent(42, 10), stats.mostFrequent)
    }

    @Test
    fun `long history - computation stays correct`() {
        // 0..99 повторяется 10 раз.
        val values = (1..1_000).map { it % 100 }
        val stats = computeSessionStats(values)

        assertEquals(1_000, stats.count)
        assertEquals(100, stats.unique)
        assertEquals(0, stats.min)
        assertEquals(99, stats.max)
        assertEquals(900, stats.duplicates)
        assertEquals(10, stats.mostFrequent!!.count)
    }

    @Test
    fun `most frequent never null for non empty list`() {
        val stats = computeSessionStats(listOf(1, 2, 3))
        assertTrue(stats.mostFrequent != null)
    }
}
