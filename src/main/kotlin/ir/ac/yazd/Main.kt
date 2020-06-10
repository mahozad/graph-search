package ir.ac.yazd

import com.github.junrar.Archive
import ir.ac.yazd.ScoreStrategy.WITHOUT_PAGE_RANK
import ir.ac.yazd.ScoreStrategy.WITH_PAGE_RANK
import org.apache.lucene.document.FeatureField
import org.apache.lucene.index.DirectoryReader
import org.apache.lucene.index.Term
import org.apache.lucene.search.*
import org.apache.lucene.search.BooleanClause.Occur
import org.apache.lucene.search.similarities.BM25Similarity
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

val scoreStrategy = WITHOUT_PAGE_RANK
val nodes = mutableSetOf<Int>()
lateinit var graph: MutableMap<Int, List<Int>>
lateinit var graphReverse: MutableMap<Int, List<Int>>
var scores: MutableMap<Int, Double> = mutableMapOf()
val precisionSums = mutableMapOf(5 to 0.0, 10 to 0.0, 20 to 0.0)
lateinit var searcher: IndexSearcher

enum class ScoreStrategy {
    WITH_PAGE_RANK,
    WITHOUT_PAGE_RANK
}

@ExperimentalStdlibApi
fun main() {
    // val startTime = Instant.now()
    // index(WITHOUT_PAGE_RANK)
    // val duration = Duration.between(startTime, Instant.now())
    // println("Time: ${duration.toMinutes()}m")

    // createPageRank()

    val indexPath = if (scoreStrategy == WITH_PAGE_RANK) Path.of("E:index-pageranked") else Path.of("E:index")
    val directory = MMapDirectory(indexPath)
    val reader = DirectoryReader.open(directory)
    searcher = IndexSearcher(reader)
    // NOTE: This should be same as the one used when indexing
    searcher.similarity = BM25Similarity() // Use BM25 algorithm instead of TF.IDF for ranking docs

    query()

    reader.close()
    directory.close()
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
fun query() {
    val path = Path.of("data/queries")
    val parser = SAXParserFactory.newInstance().newSAXParser()
    val queries = mutableListOf<Query>()
    Files
        .find(path, 1, BiPredicate { f, _ -> f.fileName.toString().startsWith("query") })
        .sorted { f1, f2 -> fileNumber(f1) - fileNumber(f2)}
        .forEach {
            val handler = object : DefaultHandler() {
                var element = ""
                var docId = 0
                val terms = mutableListOf<String>()
                val labels = mutableMapOf<Int, Int>() // docId -> label

                override fun startElement(uri: String, localName: String, name: String, attrs: Attributes) {
                    element = name
                }

                override fun characters(ch: CharArray, start: Int, length: Int) {
                    if (ch.concatToString(start, start + length) == "\n") return
                    else if (element == "word") terms.add(ch.concatToString(start, start + length))
                    else if (element == "docid") docId = ch.concatToString(start, start + length).toInt()
                    else if (element == "label") labels[docId] = ch.concatToString(start, start + length).toInt()
                }

                override fun endDocument() {
                    queries.add(Query(fileNumber(it), terms, labels))
                }
            }
            parser.parse(Files.newInputStream(it), handler)
        }

    val startTime = Instant.now()

    for (query in queries) search(query)

    println(precisionSums.map { "P@${it.key}: ${it.value / 50}" /* 50=number of queries */ })
    println("Time: ${Duration.between(startTime, Instant.now()).toMillis()}ms")
}

fun fileNumber(path:Path) = path.fileName.toString().removePrefix("query-").removeSuffix(".xml").toInt()

class Query(
    val number: Int,
    val terms: List<String>,
    val labels: Map<Int, Int>
)

fun search(q: Query) {
    // TermsQuery class OR FuzzyLikeThisQuery class
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
    val query: org.apache.lucene.search.Query
    if (scoreStrategy == WITH_PAGE_RANK) {
        val titleQuery = PhraseQuery(11, "TITLE", *q.terms.toTypedArray())
        val bodyQuery = PhraseQuery(32, "BODY", *q.terms.toTypedArray())
        val originalQuery = BooleanQuery.Builder()
            .add(titleQuery, Occur.SHOULD)
            .add(bodyQuery, Occur.MUST)
            .build()
        val featureQuery = FeatureField.newSaturationQuery("Features", "PageRank")
        query = BooleanQuery.Builder()
            .add(originalQuery, Occur.MUST)
            .add(BoostQuery(featureQuery, 2f), Occur.SHOULD)
            .build()
    } else {
        // val titleFuzzyQueries = q.terms.map { FuzzyQuery(Term("TITLE", it), 2) }
        // val titleB = BooleanQuery.Builder()
        // titleFuzzyQueries.forEach { titleB.add(it, Occur.MUST) }
        // val titleFQ = titleB.build()
        //
        // val bodyFuzzyQueries = q.terms.map { FuzzyQuery(Term("BODY", it), 0) }
        // val bodyB = BooleanQuery.Builder()
        // bodyFuzzyQueries.forEach { bodyB.add(it, Occur.SHOULD) }
        // val bodyFQ = bodyB.build()
        //
        // val queryF = BooleanQuery.Builder()
        //     .add(titleFQ, Occur.SHOULD)
        //     .add(bodyFQ, Occur.MUST)
        //     .build()
        //
        // val titlePQ = PhraseQuery(11, "TITLE", *q.terms.toTypedArray())
        // val bodyPQ = PhraseQuery(32, "BODY", *q.terms.toTypedArray())
        // val queryP = BooleanQuery.Builder()
        //     .add(titlePQ, Occur.SHOULD)
        //     .add(bodyPQ, Occur.MUST)
        //     .build()
        //
        // query = BooleanQuery.Builder()
        //     .add(queryF, Occur.SHOULD)
        //     .add(BoostQuery(queryP,0.8f), Occur.SHOULD)
        //     .build()



        val titleFuzzyQueries = q.terms.map { FuzzyQuery(Term("TITLE", it), 0) }
        val titleBuilder = BooleanQuery.Builder()
        titleFuzzyQueries.forEach { titleBuilder.add(it, Occur.MUST) }
        val titleQuery = titleBuilder.build()

        val bodyFuzzyQueries = q.terms.map { FuzzyQuery(Term("BODY", it), 0) }
        val bodyBuilder = BooleanQuery.Builder()
        bodyFuzzyQueries.forEach { bodyBuilder.add(it, Occur.SHOULD) }
        val bodyQuery = bodyBuilder.build()

        query = BooleanQuery.Builder()
            .add(titleQuery, Occur.SHOULD)
            .add(BoostQuery(bodyQuery, 10.0f), Occur.SHOULD)
            .build()



        // val titleQuery = PhraseQuery(11, "TITLE", *q.terms.toTypedArray())
        // val bodyQuery = PhraseQuery(32, "BODY", *q.terms.toTypedArray())
        // query = BooleanQuery.Builder()
        //     .add(titleQuery, Occur.SHOULD)
        //     .add(bodyQuery, Occur.MUST)
        //     .build()
    }

    println("Query ${q.number}:")
    // Retrieve 10 additional results so if some of them are not in query docs we can compensate for them
    val hits = searcher.search(query, precisionSums.keys.max()!! + 10).scoreDocs
    // println(searcher.explain(query, 209882))
    for (n in precisionSums.keys) {
        val precision = hits
            .filter { q.labels.containsKey(it.docId) }
            .take(n)
            .sumBy { q.labels.getValue(it.docId) }
            .toDouble()
            .div(n)
        precisionSums.merge(n, precision, Double::plus)
        println("P@$n: ${precision * 100}%")
    }
    println("----------------")
}

val ScoreDoc.docId get() = searcher.doc(doc).get("DOCID").toInt()

fun createPageRank() {
    val startTime = Instant.now()

    constructGraphs()
    graph.keys.forEach { scores[it] = 1.0 / nodes.size }
    val dampingFactor = 0.85
    val epsilon = 1.0 / (1.0E10 * nodes.size) // == score of each node (on average) should change more than 1E6
    var change = 1.0

    // initially PageRank of all pages is equal to 1/N (N = number of nodes in graph)
    // while the change in scores is greater than epsilon (approximation error) e.g. 1/1000000)
    //     for every node in graph
    //         PageRank(node) = (1 - d) / N + d * ∑PageRank(q)/outDegree(q)

    while (change > epsilon) {
        val previousScores = scores.values.sum()
        for (node in nodes) {
            // / nodes.size has no effect on final scoring
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
