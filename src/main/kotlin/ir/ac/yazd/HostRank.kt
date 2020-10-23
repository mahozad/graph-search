package ir.ac.yazd

import com.github.junrar.Archive
import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.LocalTime
import javax.xml.parsers.SAXParserFactory
import kotlin.math.absoluteValue

private lateinit var hostGraph: MutableMap<Int, Set<Int>>
private lateinit var hostGraphReverse: MutableMap<Int, Set<Int>>
private val docIdToHostNum = mutableMapOf<Int, Int>()
private val hostUrlToHostNum = mutableMapOf<String, Int>()
private val allNodes = mutableSetOf<Int>()

@ExperimentalStdlibApi
fun main() {
    // constructHostGraph()
    // pageRankTheHosts()



    val hostRanks = Files.newBufferedReader(Path.of("scores_hosts.txt")).lineSequence()
        .associate { it.substringBefore(" ").toInt() to it.substringAfter(" ").toDouble() }
    val path = Path.of("../graph-analysis/src/main/resources/graph.txt")
    Files.newBufferedReader(path)
        .lineSequence()
        .forEach { line -> allNodes.addAll(line.split(" ").map { it.toInt() }) }

    constructDocIdToHost()

    val resultPath = Path.of("page-ranks_hosted.txt")
    Files.deleteIfExists(resultPath)
    val writer = Files.newBufferedWriter(resultPath)
    for (node in allNodes) {
        val nodeHostNum = docIdToHostNum.getValue(node)
        val hostPageCount = docIdToHostNum.filterValues { it == nodeHostNum }.count()
        writer.write("$node ${hostRanks.getValue(nodeHostNum) / hostPageCount}")
        writer.newLine()
    }
    writer.close()
}

@ExperimentalStdlibApi
fun constructHostGraph() {
    val sourceFilePath = Path.of("../graph-analysis/src/main/resources/graph.txt")
    val pageGraph = Files.newBufferedReader(sourceFilePath)
        .lineSequence()
        .onEach { line -> allNodes.addAll(line.split(" ").map { it.toInt() }) }
        .groupBy({ it.substringBefore(" ").toInt() }, { it.substringAfter(" ").toInt() })
        .toMutableMap()

    val pageGraphR = Files.newBufferedReader(sourceFilePath)
        .lineSequence()
        .groupBy({ it.substringAfter(" ").toInt() }, { it.substringBefore(" ").toInt() })
        .toMutableMap()

    for (node in allNodes) {
        pageGraph.putIfAbsent(node, emptyList())
        pageGraphR.putIfAbsent(node, emptyList())
    }

    constructDocIdToHost()

    hostGraph = pageGraph.toList().groupBy({ pair: Pair<Int, List<Int>> -> docIdToHostNum.getValue(pair.first) },
        { pair: Pair<Int, List<Int>> -> pair.second.map { docIdToHostNum.getValue(it) } })
        .mapValues { it.value.flatten().toSet() }
        .toMutableMap()

    hostGraphReverse = pageGraphR.toList().groupBy({ pair: Pair<Int, List<Int>> -> docIdToHostNum.getValue(pair.first) },
            { pair: Pair<Int, List<Int>> -> pair.second.map { docIdToHostNum.getValue(it) } })
            .mapValues { it.value.flatten().toSet() }
            .toMutableMap()

    pageGraph.clear()
    pageGraphR.clear()
}

fun pageRankTheHosts() {
    hostGraph.keys.forEach { scores[it] = 1.0 / hostGraph.size }
    val dampingFactor = 0.85
    val epsilon = 1.0 / (1.0E10 * hostGraph.size) // == score of each node (on average) should change more than 1E10
    var change = 1.0

    while (change > epsilon) {
        val previousScores = scores.values.sum()
        for (host in hostGraph.keys) {
            scores[host] = (1 - dampingFactor) / hostGraph.size +
                    dampingFactor * hostGraphReverse.getOrDefault(host, emptySet())
                .sumOf { scores[it]!! / hostGraph[it]!!.size }
        }
        change = (scores.values.sum() - previousScores).absoluteValue
        println("change: $change, time: ${LocalTime.now()}")
    }

    // Multiply value by nodes.size because Lucene FeatureField stores only 9 bits (has precision of about 1E-3)
    val result = scores.map { "${it.key} ${it.value * hostGraph.size}" }.joinToString("\r\n")
    val resultPath = Path.of("scores_hosts.txt")
    Files.deleteIfExists(resultPath)
    val bufferedWriter = Files.newBufferedWriter(resultPath, StandardOpenOption.CREATE)
    bufferedWriter.write(result)
    bufferedWriter.close()
}

@ExperimentalStdlibApi
fun constructDocIdToHost() {
    val sourceFiles = arrayOf(File("data/WIR-Part1.rar"), File("data/WIR-Part2.rar"))
    val handler = ParseHandler2()
    for (sourceFile in sourceFiles) {
        val archive = Archive(sourceFile, null)
        for (fileHeader in archive) {
            val inputStream = archive.getInputStream(fileHeader)
            val parser = SAXParserFactory.newInstance().newSAXParser()
            parser.parse(inputStream, handler)
        }
    }
}

@ExperimentalStdlibApi
class ParseHandler2 : DefaultHandler() {

    private var hostNumber = 0
    private var currentElement = "Not set yet"
    private var docId = -1
    private var url = "Not set yet"

    override fun startElement(uri: String, localName: String, name: String, attrs: Attributes) {
        currentElement = name.toUpperCase()
    }

    override fun characters(chars: CharArray, start: Int, length: Int) {
        when (currentElement) {
            "DOCID" -> docId = chars.concatToString(start, start + length).toInt()
            "URL" -> url = chars.concatToString(start, start + length)
        }
    }

    override fun endElement(uri: String, localName: String, name: String) {
        if (name.toUpperCase() == "DOC") {
            val host = url
                .removePrefix("https://")
                .removePrefix("http://")
                .removePrefix("www.")
                .replaceAfter("/", "")
                .removeSuffix("/")
            if (host !in hostUrlToHostNum) {
                hostNumber++
                hostUrlToHostNum[host] = hostNumber
            }
            docIdToHostNum[docId] = hostUrlToHostNum.getValue(host)
        }
    }
}
