package ru.senin.kotlin.wiki

import jetbrains.letsPlot.export.ggsave
import jetbrains.letsPlot.geom.*
import jetbrains.letsPlot.*
import jetbrains.letsPlot.label.ggtitle
import jetbrains.letsPlot.label.xlab
import jetbrains.letsPlot.label.ylab
import jetbrains.letsPlot.lets_plot
import jetbrains.letsPlot.scale.scale_x_discrete
import jetbrains.letsPlot.scale.scale_y_discrete
import jetbrains.letsPlot.tooltips.layer_tooltips
import java.util.concurrent.atomic.AtomicIntegerArray
import java.io.File

data class WordStat(val word: String, val count: Int)

fun getMostFrequentWords(counts: Map<String, Int>, number: Int): List<WordStat> {

    val result = sortedSetOf(compareByDescending<WordStat> { it.count }.thenBy { it.word })
    for ((word, count) in counts) {
        result.add(WordStat(word, count))
        if (result.size > number)
            result.remove(result.last())
    }
    return result.toList()
}

fun getMostFrequentWordsString(counts: Map<String, Int>, number: Int): String {
    return getMostFrequentWords(counts, number).joinToString("") {
        "${it.count} ${it.word}\n"
    }
}

fun getNonZeroSegment(array: AtomicIntegerArray): List<IndexedValue<Int>> {
    var firstNonZeroValue = array.length()
    var lastNonZeroValue = -1
    for (i in 0 until array.length()) {
        if (array.get(i) != 0) {
            firstNonZeroValue = Integer.min(i, firstNonZeroValue)
            lastNonZeroValue = i
        }
    }
    return (firstNonZeroValue..lastNonZeroValue).map {
        IndexedValue(it, array.get(it))
    }
}

fun getNonZeroSegmentString(array: AtomicIntegerArray): String {
    return getNonZeroSegment(array).joinToString("") { "${it.index} ${it.value}\n" }
}


fun generateReportString(stats: Stats): String {
    val wordNumber = 300
    return buildString {
        appendLine("Топ-$wordNumber слов в заголовках статей:")
        appendLine(getMostFrequentWordsString(stats.titleWordCount, wordNumber))
        appendLine("Топ-$wordNumber слов в статьях:")
        appendLine(getMostFrequentWordsString(stats.textWordCount, wordNumber))
        appendLine("Распределение статей по размеру:")
        appendLine(getNonZeroSegmentString(stats.sizeCount))
        appendLine("Распределение статей по времени:")
        append(getNonZeroSegmentString(stats.yearCount))
    }
}

private fun getWordPlot(textWords: List<WordStat>) = lets_plot(mapOf(
    "word" to textWords.map { it.word },
    "count" to textWords.map { it.count }
)) + geom_histogram(stat = Stat.identity, tooltips = layer_tooltips().line("@word @count")) {
    x = "word"; y = "count"
} + scale_x_discrete(breaks = listOf()) + scale_y_discrete() + xlab("слово") + ylab("количество")

fun generateReportHtml(stats: Stats, fileName: String) {
    val wordNumber = 300
    val bunch = GGBunch()
    val sizes = getNonZeroSegment(stats.sizeCount)
    val labels = sizes.map { it.index }.plus(sizes.last().index + 1).map { "1" + "0".repeat(it) }
    bunch.addPlot(
        lets_plot(
            mapOf(
                "bytes" to sizes.map { it.index },
                "count" to sizes.map { it.value })
        ) + geom_histogram(stat = Stat.identity) { x = "bytes"; y = "count" } + scale_x_discrete(
            breaks = listOf(0.5) + sizes.map { it.index + 0.5 },
            labels = labels,
            format = ",d"
        ) + scale_y_discrete()
                + xlab("размер, байт") + ylab("число статей") + ggtitle("Распределение статей по размеру"),
        0, 0, 600, 400
    )
    val years = getNonZeroSegment(stats.yearCount)
    bunch.addPlot(
        lets_plot(
            mapOf(
                "year" to years.map { it.index },
                "count" to years.map { it.value })
        ) + geom_histogram(stat = Stat.identity) { x = "year"; y = "count" }
                + scale_x_discrete(format = "d") + xlab("год") + ylab("число статей")
                + scale_y_discrete()
                + ggtitle("Распределение статей по времени"), 0, 400, 600, 400
    )
    val titleWords = getMostFrequentWords(stats.titleWordCount, wordNumber)
    bunch.addPlot(
        getWordPlot(titleWords) + ggtitle("Топ-$wordNumber слов в заголовках статей:"), 0, 800, titleWords.size * 5, 400
    )
    val textWords = getMostFrequentWords(stats.textWordCount, wordNumber)
    bunch.addPlot(
        getWordPlot(textWords) + ggtitle("Топ-$wordNumber слов в статьях:"), 0, 1200, textWords.size * 5, 400
    )
    val file = File(fileName)
    ggsave(bunch, file.name, path = "./data")
}
