package ir.ac.yazd

import com.github.junrar.Archive
import org.apache.lucene.analysis.standard.StandardAnalyzer
import org.apache.lucene.index.DirectoryReader
import org.apache.lucene.search.*
import org.apache.lucene.search.BooleanClause.Occur
import org.apache.lucene.search.similarities.BM25Similarity
import org.apache.lucene.store.Directory
import org.apache.lucene.store.MMapDirectory
import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.LocalTime
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit.DAYS
import java.util.function.BiPredicate
import javax.xml.parsers.SAXParserFactory
import kotlin.math.absoluteValue

val nodes = mutableSetOf<Int>()
lateinit var graph: MutableMap<Int, List<Int>>
lateinit var graphReverse: MutableMap<Int, List<Int>>
var ranks: MutableMap<Int, Double> = mutableMapOf()
val precisions = mutableMapOf(5 to 0.0, 10 to 0.0, 20 to 0.0)

@ExperimentalStdlibApi
fun main() {
    // val startTime = Instant.now()
    // index()
    // val duration = Duration.between(startTime, Instant.now())
    // println("Time: ${duration.toMinutes()}m")

    // query()

    createPageRank()
}

@ExperimentalStdlibApi
fun index() {
    val sourceFiles = arrayOf(
        File("data/WIR-Part1.rar"),
        File("data/WIR-Part2.rar")
    )
    // NOTE: Lucene IndexWriter is fully thread-safe
    val executorService = Executors.newFixedThreadPool(4)
    val indexer = Indexer()

    for (sourceFile in sourceFiles) {
        val archive = Archive(sourceFile, null)
        for (fileHeader in archive) {
            val inputStream = archive.getInputStream(fileHeader)
            executorService.submit(object : Runnable {
                val parser = SAXParserFactory.newInstance().newSAXParser()
                val handler = ParseHandler(indexer)
                override fun run() = parser.parse(inputStream, handler)
            })
        }
    }

    executorService.shutdown()
    executorService.awaitTermination(1, DAYS)
    indexer.close() // Required
}

@ExperimentalStdlibApi
fun query() {
    val path = Path.of("data/queries")
    Files.find(path, 1, BiPredicate { f, _ -> f.fileName.toString().startsWith("query") })
        .forEach {
            val parser = SAXParserFactory.newInstance().newSAXParser()
            val handler = object : DefaultHandler() {
                var currentElement = ""
                var currentDocId = 0
                val terms = mutableListOf<String>()
                val labels = mutableMapOf<Int, Boolean>()

                override fun startElement(uri: String, localName: String, name: String, attrs: Attributes) {
                    currentElement = name
                }

                override fun characters(ch: CharArray, start: Int, length: Int) {
                    if (ch.concatToString(start, start + length) == "\n") return
                    else if (currentElement == "word") terms.add(ch.concatToString(start, start + length))
                    else if (currentElement == "docid") currentDocId = ch.concatToString(start, start + length).toInt()
                    else if (currentElement == "label") labels[currentDocId] = ch.concatToString(start, start + length) == "1"
                }

                override fun endDocument() {
                    search(terms, labels)
                }
            }
            parser.parse(Files.newInputStream(it), handler)
        }

    println(precisions.map { it.value / 50 /* 50=number of queries */ })
}

fun search(terms: List<String>, docs: Map<Int, Boolean>) {
    val directory: Directory = MMapDirectory(Path.of("E:/index-standard/"))
    val reader = DirectoryReader.open(directory)
    val searcher = IndexSearcher(reader)
    searcher.similarity = BM25Similarity() // Use BM25 algorithm instead of TF.IDF for ranking docs
    val analyzer = StandardAnalyzer()

    // TermsQuery class
    // OR
    // val query = MultiFieldQueryParser(arrayOf("TITLE", "BODY"), analyzer).parse(input)
    // OR
    // val query = BooleanQuery.Builder()
    //     .add(TermInSetQuery("TITLE", terms.map { BytesRef(it) }), Occur.SHOULD)
    //     .add(TermInSetQuery("BODY", terms.map { BytesRef(it) }), Occur.SHOULD)
    //     .build()
    // OR
    // val parser = QueryParser("TITLE", analyzer)
    // val query = parser.parse(input)
    // OR
    val titleQuery = PhraseQuery(10, "TITLE", *terms.toTypedArray())
    val bodyQuery = PhraseQuery(10, "BODY", *terms.toTypedArray())
    val query: Query = BooleanQuery.Builder()
        .add(titleQuery, Occur.SHOULD)
        .add(bodyQuery, Occur.SHOULD)
        .build()

    for (n in precisions.keys) {
        val hits = searcher.search(query, n).scoreDocs
        val precision = hits
            .filter { docs.containsKey(getDocId(searcher, it.doc)) }
            .map { if (docs.getValue(getDocId(searcher, it.doc))) 1.0 else 0.0 }
            .fold(0.0) { acc, b -> acc + b }
            .div(hits.size)
        precisions.merge(n, precision) { old, new -> old + if (new.isNaN()) 0.0 else new }
        println("P@$n: ${precision * 100}%")
    }
    println("----------------")

    reader.close()
    directory.close()
}

fun getDocId(searcher: IndexSearcher, docNumber: Int): Int {
    return searcher.doc(docNumber).getField("DOCID").numericValue().toInt()
}

fun createPageRank() {
    constructGraphs()
    graph.keys.forEach { ranks[it] = 1.0 / nodes.size }
    val dampingFactor = 0.85
    val epsilon = 1.0 / (1.0E6 * nodes.size) // == score of each node (on average) should change more than 1E6
    var change = 1.0

    // initially PageRank of all pages is equal to 1/n (n = number of nodes in graph)
    // while true (while the change is greater than epsilon=approximation error e.g. 1/1000000)
    //      for every node in graph
    //          PageRank(node) = ∑PageRank(q)/outDegree(q)

    var previousRanks: Double
    while (change > epsilon) {
        previousRanks = ranks.values.sum()

        for (node in nodes) {
            ranks[node] = (1 - dampingFactor) / nodes.size +
                    dampingFactor * graphReverse.getOrDefault(node, emptyList()).sumByDouble { ranks[it]!!/graph.getValue(it).size }
        }
        change = (ranks.values.sum() - previousRanks).absoluteValue
        println("change: $change, time: ${LocalTime.now()}")
    }
    val result = ranks.map { "${it.key} ${it.value}" }.joinToString("\r\n") { it }
    val resultPath = Path.of("ranks.txt")
    Files.deleteIfExists(resultPath)
    val bufferedWriter = Files.newBufferedWriter(resultPath, StandardOpenOption.CREATE)
    bufferedWriter.write(result).also { bufferedWriter.close() }

    // val startNode = nodes.random()
    // fun calculate(node: Int) {
    //     if (change < epsilon) return
    //     ranks[node] = graphReverse.getOrDefault(node, emptyList()).fold(0.0, { acc, i -> acc + ranks[i]!! / graph.getValue(i).size })
    //     for (child in graph.getValue(node)) calculate(child)
    // }
    // calculate(startNode)

    // while (change > epsilon) {
    //     graph.keys.asSequence()
    //         .map { graphReverse.getOrDefault(it, emptyList()).fold(0.0, { acc, i -> acc + ranks[i]!!/graph.getValue(i).size }) }
    //         .forEachIndexed {
    //                 index, rank -> ranks[index] = rank }
    // }
}

fun constructGraphs() {
    val sourceFilePath = Path.of("../graph-analysis/src/main/resources/sample-graph.txt")

    graph = Files.newBufferedReader(sourceFilePath)
        .lineSequence()
        .onEach { line -> nodes.addAll(line.split(" ").map { it.toInt() }) }
        .groupBy({ it.substringBefore(" ").toInt() }, { it.substringAfter(" ").toInt() })
        .toMutableMap()

    graphReverse = Files.newBufferedReader(sourceFilePath)
        .lineSequence()
        .groupBy({ it.substringAfter(" ").toInt() }, { it.substringBefore(" ").toInt() })
        .toMutableMap()

    for (node in nodes) {
        graph.putIfAbsent(node, emptyList())
        graphReverse.putIfAbsent(node, emptyList())
    }
}
