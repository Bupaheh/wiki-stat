package ru.senin.kotlin.wiki

import com.apurebase.arkenv.Arkenv
import com.apurebase.arkenv.argument
import com.apurebase.arkenv.parse
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import org.jetbrains.annotations.NotNull
import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler
import java.io.File
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

lateinit var parameters: Parameters

class Page {
    // List<Char> instead of String to avoid copying in PageHandler.characters
    lateinit var title: List<Char>
    lateinit var text: List<Char>
    var sizeLog: Int? = null
    var year: Int? = null

    fun isInitialized() =
        this::title.isInitialized && this::text.isInitialized && sizeLog != null && year != null
}


fun countWords(text: List<Char>, counts: MutableMap<String, Int>) {
    val stringBuilder = StringBuilder()
    for (i in text.indices) {
        when {
            text[i] in 'а'..'я' -> stringBuilder.append(text[i])
            text[i] in 'А'..'Я' -> stringBuilder.append(text[i] - ('А' - 'a'))
            stringBuilder.isNotEmpty() -> {
                val word = stringBuilder.toString()
                counts[word] = counts.getOrDefault(word, 0) + 1
                stringBuilder.clear()
            }
        }
    }
    if (stringBuilder.isNotEmpty()) {
        val word = stringBuilder.toString()
        counts[word] = counts.getOrDefault(word, 0) + 1
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
    private val sizeCount = IntArray(10)
    private val yearCount = IntArray(3000)
    private val titleWordCount = mutableMapOf<String, Int>()
    private val textWordCount = mutableMapOf<String, Int>()

    private fun processPage(page: Page) {
        if (!page.isInitialized())
            return
        sizeCount[requireNotNull(page.sizeLog)]++
        yearCount[requireNotNull(page.year)]++
        countWords(page.title, titleWordCount)
        countWords(page.text, textWordCount)
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
        val list = ch?.asList()?.subList(start, start + length) ?: listOf()
        when (tagStack.lastOrNull()) {
            Tag.TITLE -> lastPage?.title = list
            Tag.TEXT -> lastPage?.text = list
            Tag.TIMESTAMP -> lastPage?.year = list.takeWhile { it.isDigit() }.joinToString("").toInt()
        }
    }
}

fun process(inputs: List<File>, output: String, threads: Int) {
    inputs.map { file ->
        val inputStream = BZip2CompressorInputStream(file.inputStream())
        val parser = SAXParserFactory.newInstance().newSAXParser()
        val handler = PageHandler()
        parser.parse(inputStream, handler)
    }
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
