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
        assertEquals(listOf(), getMostFrequentWords(Companion.wordCounts, 0))
    }

    @Test
    fun `get most frequent words`() {
        assertEquals(
            listOf(WordStat("частое", 10), WordStat("словарь", 2)),
            getMostFrequentWords(Companion.wordCounts, 2)
        )
    }

    @Test
    fun `many most frequent words`() {
        assertEquals(
            listOf(
                WordStat("частое", 10),
                WordStat("словарь", 2),
                WordStat("слово", 2),
                WordStat("редкое", 1)
            ),
            getMostFrequentWords(Companion.wordCounts, 1000)
        )
    }

    @Test
    fun `zero array segment`() {
        assertEquals(listOf(), getNonZeroSegment(AtomicIntegerArray(intArrayOf(0, 0, 0, 0))))
    }

    @Test
    fun `zero-border array segment`() {
        assertEquals(
            listOf(IndexedValue(1, 2), IndexedValue(2, 3)),
            getNonZeroSegment(AtomicIntegerArray(intArrayOf(0, 2, 3, 0)))
        )
    }

    @Test
    fun `zero-center array segment`() {
        assertEquals(listOf(1, 0, 0, 3).withIndex().toList(), getNonZeroSegment(AtomicIntegerArray(intArrayOf(1, 0, 0, 3))))
    }

    @Test
    fun `non-zero array segment`() {
        assertEquals(listOf(39, 28, 62).withIndex().toList(), getNonZeroSegment(AtomicIntegerArray(intArrayOf(39, 28, 62))))
    }

    @Test
    fun countWords() {
        val counts = ConcurrentHashMap<String, Int>()
        val text = StringBuilder("слово, не_слово, СлОвО    jjjjjj йй йй й ффф\nффф")
        countWords(text, counts)
        assertEquals(mapOf("слово" to 3, "ффф" to 2), counts)
    }
}