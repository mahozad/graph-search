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
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit.DAYS
import java.util.function.BiPredicate
import javax.xml.parsers.SAXParserFactory

@ExperimentalStdlibApi
fun main() {
    // val startTime = Instant.now()
    // index()
    // val duration = Duration.between(startTime, Instant.now())
    // println("Time: ${duration.toMinutes()}m")

    query()
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
}

fun search(terms: List<String>, docs: Map<Int, Boolean>) {
    val directory: Directory = MMapDirectory(Path.of("E:/index-standard/"))
    val reader = DirectoryReader.open(directory)
    val searcher = IndexSearcher(reader)
    searcher.similarity = BM25Similarity() // Use BM25 algorithm instead of TF.IDF for scoring documents
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
    val hits = searcher.search(query, 20).scoreDocs

    val precision = hits
        .filter { docs.containsKey(getDocId(searcher, it.doc)) }
        .map { if (docs.getValue(getDocId(searcher, it.doc))) 1.0 else 0.0 }
        .fold(0.0) { acc, b -> acc + b }
        .div(hits.size)

    println("${precision * 100}%")

    reader.close()
    directory.close()
}

fun getDocId(searcher: IndexSearcher, docNumber: Int): Int {
    return searcher.doc(docNumber).getField("DOCID").numericValue().toInt()
}
