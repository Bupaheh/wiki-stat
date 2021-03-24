package ru.senin.kotlin.wiki

import org.junit.jupiter.api.Test
import java.lang.StringBuilder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicIntegerArray
import kotlin.test.assertEquals

class UnitTest {
    companion object {
        private val wordCounts = mapOf(
            "словарь" to 2,
            "частое" to 10,
            "слово" to 2,
            "редкое" to 1
        )
    }

    @Test
    fun `zero most frequent words`() {
        assertEquals("", getMostFrequentWords(Companion.wordCounts, 0))
    }

    @Test
    fun `get most frequent words`() {
        assertEquals("10 частое\n2 словарь\n", getMostFrequentWords(Companion.wordCounts, 2))
    }

    @Test
    fun `many most frequent words`() {
        assertEquals("10 частое\n2 словарь\n2 слово\n1 редкое\n", getMostFrequentWords(Companion.wordCounts, 1000))
    }

    @Test
    fun `zero array segment`() {
        assertEquals("", getNonZeroSegment(AtomicIntegerArray(intArrayOf(0, 0, 0, 0))))
    }

    @Test
    fun `zero-border array segment`() {
        assertEquals("1 2\n2 3\n", getNonZeroSegment(AtomicIntegerArray(intArrayOf(0, 2, 3, 0))))
    }

    @Test
    fun `zero-center array segment`() {
        assertEquals("0 1\n1 0\n2 0\n3 3\n", getNonZeroSegment(AtomicIntegerArray(intArrayOf(1, 0, 0, 3))))
    }

    @Test
    fun `non-zero array segment`() {
        assertEquals("0 39\n1 28\n2 62\n", getNonZeroSegment(AtomicIntegerArray(intArrayOf(39, 28, 62))))
    }

    @Test
    fun countWords() {
        val counts = ConcurrentHashMap<String, Int>()
        val text = StringBuilder("слово, не_слово, СлОвО    jjjjjj йй йй й ффф\nффф")
        countWords(text, counts)
        assertEquals(mapOf("слово" to 3, "ффф" to 2), counts)
    }
}