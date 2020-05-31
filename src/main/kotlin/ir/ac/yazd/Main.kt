package ir.ac.yazd

import com.github.junrar.Archive
import org.apache.lucene.analysis.standard.StandardAnalyzer
import org.apache.lucene.index.DirectoryReader
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser
import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.search.similarities.BM25Similarity
import org.apache.lucene.store.Directory
import org.apache.lucene.store.MMapDirectory
import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import java.time.Duration
import java.time.Instant
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
                var currentDocid=0
                val docs = mutableMapOf<Int,Boolean>()
                val terms = mutableListOf<String>()

                override fun startElement(uri: String?, localName: String?, qName: String, attributes: Attributes?) {
                    currentElement = qName
                }

                override fun characters(ch: CharArray, start: Int, length: Int) {
                    if (ch.concatToString(start, start + length)=="\n") return
                    else if (currentElement == "word") terms.add(ch.concatToString(start, start + length))
                    else if (currentElement=="docid") currentDocid = ch.concatToString(start, start + length).toInt()
                    else if (currentElement == "label") docs[currentDocid] = ch.concatToString(start,start+length)=="1"
                }

                override fun endDocument() {
                    println(terms)
                    // search(terms)
                }
            }
            parser.parse(Files.newInputStream(it), handler)
        }
}

fun search() {
    val directory: Directory = MMapDirectory(Path.of("E:/index"))
    val reader = DirectoryReader.open(directory)
    val searcher = IndexSearcher(reader)
    searcher.similarity = BM25Similarity() // Use BM25 algorithm instead of TF.IDF for scoring documents
    val analyzer = StandardAnalyzer()

    val input = "زنبور عسل"

    // OR TermsQuery class
    val query = MultiFieldQueryParser(arrayOf("TITLE", "BODY"), analyzer).parse(input)
    // OR
    // val titleQuery = PhraseQuery(5, "TITLE", input)
    // val bodyQuery = PhraseQuery(5, "BODY", input)
    // val query: Query = BooleanQuery.Builder()
    //     .add(titleQuery, BooleanClause.Occur.SHOULD)
    //     .add(bodyQuery, BooleanClause.Occur.MUST)
    //     .build()
    // OR
    // val parser = QueryParser("TITLE", analyzer)
    // val query = parser.parse(input)
    val hits = searcher.search(query, 10).scoreDocs

    // Iterate through the results:
    for (hit in hits) println(searcher.doc(hit.doc))

    reader.close()
    directory.close()
}
