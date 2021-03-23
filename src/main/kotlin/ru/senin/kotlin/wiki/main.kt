package ru.senin.kotlin.wiki

import com.apurebase.arkenv.Arkenv
import com.apurebase.arkenv.argument
import com.apurebase.arkenv.parse
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import org.jetbrains.annotations.NotNull
import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler
import java.io.File
import java.lang.Integer.min
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import java.util.concurrent.atomic.AtomicIntegerArray
import javax.xml.parsers.SAXParserFactory
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract
import kotlin.properties.Delegates
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty
import kotlin.time.measureTime

class Parameters : Arkenv() {
    val inputs: List<File> by argument("--inputs") {
        description = "Path(s) to bzip2 archived XML file(s) with WikiMedia dump. Comma separated."
        mapping = {
            it.split(",").map { name -> File(name) }
        }
        validate("File does not exist or cannot be read") {
            it.all { file -> file.exists() && file.isFile && file.canRead() }
        }
    }

    val output: String by argument("--output") {
        description = "Report output file"
        defaultValue = { "statistics.txt" }
    }

    val threads: Int by argument("--threads") {
        description = "Number of threads"
        defaultValue = { 4 }
        validate("Number of threads must be in 1..32") {
            it in 1..32
        }
    }
}

object Stats {
    const val sizeCountSize = 10
    const val yearCountSize = 3000

    val sizeCount = AtomicIntegerArray(sizeCountSize)
    val yearCount = AtomicIntegerArray(yearCountSize)
    val titleWordCount = ConcurrentHashMap<String, Int>()
    val textWordCount = ConcurrentHashMap<String, Int>()
}

lateinit var parameters: Parameters

class Page {
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
            stringBuilder.isNotEmpty() -> {
                val word = stringBuilder.toString()
                counts.putIfAbsent(word, 0)
                counts.computeIfPresent(word) { _, value -> value + 1 }
                stringBuilder.clear()
            }
        }
    }
    if (stringBuilder.isNotEmpty()) {
        val word = stringBuilder.toString()
        counts.putIfAbsent(word, 0)
        counts.computeIfPresent(word) { _, value -> value + 1 }
    }
}

class PageHandler : DefaultHandler() {
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
        Stats.sizeCount.incrementAndGet(requireNotNull(page.sizeLog))
        Stats.yearCount.incrementAndGet(requireNotNull(page.year))
        countWords(page.title, Stats.titleWordCount)
        countWords(page.text, Stats.textWordCount)
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

fun process(inputs: List<File>, output: String, threads: Int) {
    inputs.forEach { file ->
        val inputStream = BZip2CompressorInputStream(file.inputStream())
        val parser = SAXParserFactory.newInstance().newSAXParser()
        val handler = PageHandler()
        parser.parse(inputStream, handler)
        inputStream.close()
    }
    val result = generateReport()
}

fun main(args: Array<String>) {
    val a by Delegates.notNull<Int>()
    try {
        parameters = Parameters().parse(args)

        if (parameters.help) {
            println(parameters.toString())
            return
        }

        val duration = measureTime {
            process(parameters.inputs, parameters.output, parameters.threads)
        }
        println("Time: ${duration.inMilliseconds} ms")

    } catch (e: Exception) {
        println("Error! ${e.message}")
        throw e
    }
}
