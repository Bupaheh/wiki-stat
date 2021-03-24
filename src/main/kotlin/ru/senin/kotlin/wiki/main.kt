package ru.senin.kotlin.wiki

import com.apurebase.arkenv.Arkenv
import com.apurebase.arkenv.argument
import com.apurebase.arkenv.parse
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.File
import java.util.concurrent.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicIntegerArray
import javax.xml.parsers.SAXParserFactory
import kotlin.time.measureTime
import kotlinx.serialization.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.BufferedInputStream
import java.net.URL
import java.nio.channels.Channels
import java.nio.channels.ReadableByteChannel
import java.nio.file.Files

class Parameters : Arkenv() {
    val inputs: List<File>? by argument("--inputs") {
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
    val date: String? by argument("--date") {
        description = "Date to load dumps for"
    }
}

class Stats {
    val sizeCount = AtomicIntegerArray(sizeCountSize)
    val yearCount = AtomicIntegerArray(yearCountSize)
    val titleWordCount = ConcurrentHashMap<String, Int>()
    val textWordCount = ConcurrentHashMap<String, Int>()

    lateinit var threadPool: ExecutorService

    fun threadPoolInit(numberOfThreads: Int) {
        threadPool = Executors.newFixedThreadPool(numberOfThreads)
    }

    fun awaitTermination(numberOfMinutes: Long) {
        threadPool.shutdown()
        threadPool.awaitTermination(numberOfMinutes, TimeUnit.MINUTES)
    }

    companion object {
        const val sizeCountSize = 10
        const val yearCountSize = 3000
    }
}

lateinit var parameters: Parameters

fun process(inputs: List<File>, output: String, numberOfThreads: Int, waitTime: Long = 3) {
    val countDownLatch = CountDownLatch(inputs.size)
    val parsersThreadPool = Executors.newFixedThreadPool(numberOfThreads)
    val stats = Stats()
    stats.threadPoolInit(numberOfThreads)

    inputs.forEach { file ->
        parsersThreadPool.submit {
            try {
                BZip2CompressorInputStream(file.inputStream()).use { inputStream ->
                    val parser = SAXParserFactory.newInstance().newSAXParser()
                    val handler = PageHandler(stats)
                    parser.parse(inputStream, handler)
                }
            } catch (e: Exception) {
                println("Error! ${e.message}")
                throw e
            } finally {
                countDownLatch.countDown()
            }
        }
    }

    parsersThreadPool.shutdown()
    countDownLatch.await()
    stats.awaitTermination(waitTime)
    File(output).writeText(generateReport(stats))
}

fun downloadFiles(date: String): List<File> {
    try {
        val urls =
            BufferedInputStream(URL("https://dumps.wikimedia.org/ruwiki/$date/dumpstatus.json").openStream()).use { inputStream ->
                Json.parseToJsonElement(inputStream.readAllBytes().decodeToString())
                    .jsonObject["jobs"]!!.jsonObject["metacurrentdump"]!!.jsonObject["files"]!!.jsonObject.map {
                    "https://dumps.wikimedia.org${it.value.jsonObject["url"]!!.jsonPrimitive.content}"
                }
            }
        return urls.map { url ->
            BufferedInputStream(URL(url).openStream()).use { inputStream ->
                File("temp_test_data/" + url.takeLastWhile { it != '/' }).apply {
                    mkdirs()
                    outputStream().use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
            }
        }
    } catch (e: Throwable) {
        println("Invalid date: ${e.message}")
        throw e
    }
}

fun main(args: Array<String>) {
    try {
        parameters = Parameters().parse(args)

        if (parameters.help) {
            println(parameters.toString())
            return
        }

        val duration = measureTime {
            process(parameters.date?.let { downloadFiles(it) } ?: parameters.inputs ?: throw Exception("Inputs or date should be defined"),
                parameters.output,
                parameters.threads)
        }
        println("Time: ${duration.inMilliseconds} ms")

    } catch (e: Exception) {
        println("Error! ${e.message}")
        throw e
    }
}
