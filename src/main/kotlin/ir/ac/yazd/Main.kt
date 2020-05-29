package ir.ac.yazd

import com.github.junrar.Archive
import org.apache.lucene.analysis.standard.StandardAnalyzer
import org.apache.lucene.index.DirectoryReader
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser
import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.store.Directory
import org.apache.lucene.store.MMapDirectory
import java.io.File
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit.DAYS
import javax.xml.parsers.SAXParserFactory

@ExperimentalStdlibApi
fun main() {
    val startTime = Instant.now()
    index()
    val duration = Duration.between(startTime, Instant.now())
    println("Time: ${duration.toMinutes()}m")

    // search()
}

@ExperimentalStdlibApi
fun index() {
    val sourceFiles = arrayOf(
        File("data/WIR-Part1.rar"),
        File("data/WIR-Part2.rar")
    )
    // NOTE: The IndexWriter class is fully thread-safe
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

fun search() {
    val directory: Directory = MMapDirectory(Path.of("E:/index"))
    val reader = DirectoryReader.open(directory)
    val searcher = IndexSearcher(reader)
    val analyzer = StandardAnalyzer()

    val input = "زنبور عسل"

    // Parse a simple query that searches for "text":
    val query = MultiFieldQueryParser(arrayOf("TITLE", "BODY"), analyzer).parse(input)
    // OR
    // val titleQuery = PhraseQuery(5, "TITLE", input)
    // val bodyQuery = PhraseQuery(5, "BODY", input)
    // val query: Query = BooleanQuery.Builder()
    //     .add(titleQuery, BooleanClause.Occur.SHOULD)
    //     .add(bodyQuery, BooleanClause.Occur.MUST)
    //     .build()
    // OR
    // val parser = QueryParser("fieldname", analyzer)
    // val query = parser.parse(input)
    // OR
    // val parser = MultiFieldQueryParser(arrayOf("TITLE", "BODY"), analyzer)
    // val query = parser.parse(input)
    val hits = searcher.search(query, 10).scoreDocs

    // Iterate through the results:
    for (hit in hits) println(searcher.doc(hit.doc))

    reader.close()
    directory.close()
}
