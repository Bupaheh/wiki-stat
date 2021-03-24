package ru.senin.kotlin.wiki

import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler
import java.util.concurrent.ConcurrentHashMap

private class Page {
    val title = StringBuilder()
    val text = StringBuilder()
    var sizeLog: Int? = null
    var year: Int? = null

    fun isInitialized() =
        title.isNotEmpty() && text.isNotEmpty() && sizeLog != null && year != null
}

fun countWords(text: StringBuilder, counts: ConcurrentHashMap<String, Int>) {
    val stringBuilder = StringBuilder()
    for (char in text) {
        when {
            char in 'а'..'я' -> stringBuilder.append(char)
            char in 'А'..'Я' -> stringBuilder.append(char.toLowerCase())
            stringBuilder.length >= 3 -> {
                val word = stringBuilder.toString()
                counts.putIfAbsent(word, 0)
                counts.computeIfPresent(word) { _, value -> value + 1 }
                stringBuilder.clear()
            }
            else -> stringBuilder.clear()
        }
    }
    if (stringBuilder.length >= 3) {
        val word = stringBuilder.toString()
        counts.putIfAbsent(word, 0)
        counts.computeIfPresent(word) { _, value -> value + 1 }
    }
}

class PageHandler(private val stats: Stats) : DefaultHandler() {
    private enum class Tag(val parent: Tag?) {
        MEDIAWIKI(null),
        PAGE(MEDIAWIKI),
        TITLE(PAGE),
        REVISION(PAGE),
        TEXT(REVISION),
        TIMESTAMP(REVISION)
    }

    private val tags = Tag.values().map { it.name.toLowerCase() to it }.toMap()
    private val tagStack = mutableListOf<Tag?>()
    private var lastPage: Page? = null

    private fun processPage(page: Page) {
        if (!page.isInitialized())
            return
        stats.threadPool.submit {
            stats.sizeCount.incrementAndGet(requireNotNull(page.sizeLog))
            stats.yearCount.incrementAndGet(requireNotNull(page.year))
            countWords(page.title, stats.titleWordCount)
            countWords(page.text, stats.textWordCount)
        }
    }

    override fun startElement(uri: String?, localName: String?, qName: String?, attributes: Attributes?) {
        val tag = tags[qName].takeIf { it?.parent == tagStack.lastOrNull() }
        tagStack.add(tag)
        when (tag) {
            Tag.TEXT -> attributes?.let { lastPage?.sizeLog = it.getValue("bytes").length - 1 }
            Tag.PAGE -> lastPage = Page()
        }
    }

    override fun endElement(uri: String?, localName: String?, qName: String?) {
        if (tagStack.last() == Tag.PAGE) {
            processPage(requireNotNull(lastPage))
            lastPage = null
        }
        tagStack.removeLast()
    }

    override fun characters(ch: CharArray?, start: Int, length: Int) {
        when (tagStack.lastOrNull()) {
            Tag.TITLE -> lastPage?.title?.append(ch, start, length)
            Tag.TEXT -> lastPage?.text?.append(ch, start, length)
            Tag.TIMESTAMP -> lastPage?.year = String(ch ?: charArrayOf(), start, length).takeWhile { it.isDigit() }.toInt()
        }
    }
}