package ru.senin.kotlin.wiki

import java.util.concurrent.atomic.AtomicIntegerArray

private data class WordStat(val word: String, val count: Int)

private fun getMostFrequentWords(counts: MutableMap<String, Int>, number: Int) : List<WordStat> {
    val result = sortedSetOf(compareByDescending<WordStat> { it.count }.thenBy { it.word })
    for ((word, count) in counts) {
        result.add(WordStat(word, count))
        if (result.size > number)
            result.remove(result.last())
    }
    return result.toList()
}

private fun getNonZeroSegment(array: AtomicIntegerArray, size: Int): String {
    var firstNonZeroValue = size
    var lastNonZeroValue = -1
    for (i in 0 until size) {
        if (array.get(i) != 0) {
            firstNonZeroValue = Integer.min(i, firstNonZeroValue)
            lastNonZeroValue = i
        }
    }
    return buildString {
        for (i in firstNonZeroValue..lastNonZeroValue)
            appendLine("$i ${array.get(i)}")
    }
}


fun generateReport(): String {
    val wordNumber = 300
    return buildString {
        appendLine("Топ-$wordNumber слов в заголовках статей:")
        appendLine(getMostFrequentWords(Stats.titleWordCount, wordNumber).joinToString("\n") { "${it.count} ${it.word}" })
        appendLine("Топ-$wordNumber слов в текстах статей:")
        appendLine(getMostFrequentWords(Stats.textWordCount, wordNumber).joinToString("\n") { "${it.count} ${it.word}" })
        appendLine("Распределение статей по размеру:")
        appendLine(getNonZeroSegment(Stats.sizeCount, Stats.sizeCountSize))
        appendLine("Распределение статей по времени:")
        appendLine(getNonZeroSegment(Stats.yearCount, Stats.yearCountSize))
    }
}