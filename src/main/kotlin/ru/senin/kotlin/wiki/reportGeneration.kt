package ru.senin.kotlin.wiki

import java.util.concurrent.atomic.AtomicIntegerArray

fun getMostFrequentWords(counts: Map<String, Int>, number: Int): String {
    data class WordStat(val word: String, val count: Int)

    val result = sortedSetOf(compareByDescending<WordStat> { it.count }.thenBy { it.word })
    for ((word, count) in counts) {
        result.add(WordStat(word, count))
        if (result.size > number)
            result.remove(result.last())
    }
    return result.joinToString("") {
        "${it.count} ${it.word}\n"
    }
}

fun getNonZeroSegment(array: AtomicIntegerArray): String {
    var firstNonZeroValue = array.length()
    var lastNonZeroValue = -1
    for (i in 0 until array.length()) {
        if (array.get(i) != 0) {
            firstNonZeroValue = Integer.min(i, firstNonZeroValue)
            lastNonZeroValue = i
        }
    }
    return (firstNonZeroValue..lastNonZeroValue).joinToString("") {
        "$it ${array.get(it)}\n"
    }
}


fun generateReport(stats: Stats): String {
    val wordNumber = 300
    return buildString {
        appendLine("Топ-$wordNumber слов в заголовках статей:")
        appendLine(getMostFrequentWords(stats.titleWordCount, wordNumber))
        appendLine("Топ-$wordNumber слов в статьях:")
        appendLine(getMostFrequentWords(stats.textWordCount, wordNumber))
        appendLine("Распределение статей по размеру:")
        appendLine(getNonZeroSegment(stats.sizeCount))
        appendLine("Распределение статей по времени:")
        append(getNonZeroSegment(stats.yearCount))
    }
}