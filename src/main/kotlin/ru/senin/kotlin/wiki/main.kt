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

fun main(args: Array<String>) {
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
