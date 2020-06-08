package ir.ac.yazd

import com.github.junrar.Archive
import org.apache.lucene.analysis.standard.StandardAnalyzer
import org.apache.lucene.document.FeatureField
import org.apache.lucene.index.DirectoryReader
import org.apache.lucene.index.Term
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
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit.DAYS
import java.util.function.BiPredicate
import javax.xml.parsers.SAXParserFactory
import kotlin.math.absoluteValue

val nodes = mutableSetOf<Int>()
lateinit var graph: MutableMap<Int, List<Int>>
lateinit var graphReverse: MutableMap<Int, List<Int>>
var scores: MutableMap<Int, Double> = mutableMapOf()
val precisions = mutableMapOf(5 to 0.0, 10 to 0.0, 20 to 0.0)

enum class ScoreStrategy {
    WITH_PAGE_RANK,
    WITHOUT_PAGE_RANK
}

@ExperimentalStdlibApi
fun main() {
    // val startTime = Instant.now()
    // index(ScoreStrategy.WITHOUT_PAGE_RANK)
    // val duration = Duration.between(startTime, Instant.now())
    // println("Time: ${duration.toMinutes()}m")

    // createPageRank()

    query(ScoreStrategy.WITHOUT_PAGE_RANK)
}

@ExperimentalStdlibApi
fun index(scoreStrategy: ScoreStrategy) {
    val sourceFiles = arrayOf(
        File("data/WIR-Part1.rar"),
        File("data/WIR-Part2.rar")
    )
    // NOTE: Lucene IndexWriter is fully thread-safe
    val executorService = Executors.newFixedThreadPool(4)
    val indexer = Indexer(scoreStrategy)

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
fun query(scoreStrategy: ScoreStrategy) {
    val startTime = Instant.now()

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
                    search(terms, labels, scoreStrategy)
                }
            }
            parser.parse(Files.newInputStream(it), handler)
        }

    println(precisions.map { "P@${it.key}: ${it.value / 50}" /* 50=number of queries */ })
    println("Time: ${Duration.between(startTime, Instant.now()).toSeconds()}s")
}

fun search(terms: List<String>, docs: Map<Int, Boolean>, scoreStrategy: ScoreStrategy) {
    val indexPath = if (scoreStrategy == ScoreStrategy.WITH_PAGE_RANK) Path.of("E:/index-pageranked") else Path.of("E:index")
    val directory: Directory = MMapDirectory(indexPath)
    val reader = DirectoryReader.open(directory)
    val searcher = IndexSearcher(reader)
    // NOTE: This should be same as the one used when indexing
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
    val query: Query
    if (scoreStrategy == ScoreStrategy.WITH_PAGE_RANK) {
        val titleQuery = PhraseQuery(10, "TITLE", *terms.toTypedArray())
        val bodyQuery = PhraseQuery(10, "BODY", *terms.toTypedArray())
        val originalQuery: Query = BooleanQuery.Builder()
            .add(titleQuery, Occur.SHOULD)
            .add(bodyQuery, Occur.SHOULD)
            .build()
        val featureQuery = FeatureField.newSaturationQuery("Features", "PageRank")
        query = BooleanQuery.Builder()
            .add(originalQuery, Occur.MUST)
            .add(BoostQuery(featureQuery, 0.7f), Occur.SHOULD)
            .build()
    } else {
        // use FuzzyLikeThisQuery

        val titleFuzzyQueries = terms.map { FuzzyQuery(Term("TITLE", it),0) }
        val titleB = BooleanQuery.Builder()
        titleFuzzyQueries.forEach { titleB.add(it, Occur.SHOULD) }
        val titleFQ = titleB.build()

        val bodyFuzzyQueries = terms.map { FuzzyQuery(Term("BODY", it),1) }
        val bodyB = BooleanQuery.Builder()
        bodyFuzzyQueries.forEach { bodyB.add(it, Occur.MUST) }
        val bodyFQ = bodyB.build()

        val queryF = BooleanQuery.Builder()
            .add(titleFQ, Occur.SHOULD)
            .add(bodyFQ, Occur.MUST)
            .build()

        val titlePQ = PhraseQuery(11, "TITLE", *terms.toTypedArray())
        val bodyPQ = PhraseQuery(32, "BODY", *terms.toTypedArray())
        val queryP = BooleanQuery.Builder()
            .add(titlePQ, Occur.SHOULD)
            .add(bodyPQ, Occur.MUST)
            .build()

        query = BooleanQuery.Builder()
            .add(queryF, Occur.SHOULD)
            .add(queryP, Occur.SHOULD)
            .build()


        // val titleFuzzyQueries = terms.map { FuzzyQuery(Term("TITLE", it)) }
        // val titleB = BooleanQuery.Builder()
        // titleFuzzyQueries.forEach { titleB.add(it, Occur.SHOULD) }
        // val titleQ = titleB.build()
        //
        // val bodyFuzzyQueries = terms.map { FuzzyQuery(Term("BODY", it)) }
        // val bodyB = BooleanQuery.Builder()
        // bodyFuzzyQueries.forEach { bodyB.add(it, Occur.SHOULD) }
        // val bodyQ = bodyB.build()
        //
        // query = BooleanQuery.Builder()
        //     .add(titleQ, Occur.SHOULD)
        //     .add(bodyQ, Occur.SHOULD)
        //     .build()


        // val titleQuery = PhraseQuery(11, "TITLE", *terms.toTypedArray())
        // val bodyQuery = PhraseQuery(32, "BODY", *terms.toTypedArray())
        // query = BooleanQuery.Builder()
        //     .add(titleQuery, Occur.SHOULD)
        //     .add(bodyQuery, Occur.MUST)
        //     .build()
    }

    for (n in precisions.keys) {
        val hits = searcher.search(query, n).scoreDocs
        val precision = hits
            .filter { docs.containsKey(getDocId(searcher, it.doc)) }
            .map { if (docs.getValue(getDocId(searcher, it.doc))) 1.0 else 0.0 }
            .sum()
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
    val startTime = Instant.now()

    constructGraphs()
    graph.keys.forEach { scores[it] = 1.0 / nodes.size }
    val dampingFactor = 0.85
    val epsilon = 1.0 / (1.0E6 * nodes.size) // == score of each node (on average) should change more than 1E6
    var change = 1.0

    // initially PageRank of all pages is equal to 1/N (N = number of nodes in graph)
    // while the change in scores is greater than epsilon (approximation error) e.g. 1/1000000)
    //     for every node in graph
    //         PageRank(node) = (1 - d) / N + d * ∑PageRank(q)/outDegree(q)

    while (change > epsilon) {
        val previousScores = scores.values.sum()
        for (node in nodes) {
            scores[node] = (1 - dampingFactor) / nodes.size +
                    dampingFactor * graphReverse.getOrDefault(node, emptyList())
                .sumByDouble { scores[it]!! / graph[it]!!.size }
        }
        change = (scores.values.sum() - previousScores).absoluteValue
        println("change: $change, time: ${LocalTime.now()}")
    }

    val result = scores.map { "${it.key} ${it.value}" }.joinToString("\r\n") { it }
    val resultPath = Path.of("scores.txt")
    Files.deleteIfExists(resultPath)
    val bufferedWriter = Files.newBufferedWriter(resultPath, StandardOpenOption.CREATE)
    bufferedWriter.write(result).also { bufferedWriter.close() }

    println("Time: ${Duration.between(startTime, Instant.now()).toMinutes()}m")
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
