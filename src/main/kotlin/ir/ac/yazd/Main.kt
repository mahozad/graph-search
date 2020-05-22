package ir.ac.yazd

import com.github.junrar.Archive
import org.apache.lucene.analysis.standard.StandardAnalyzer
import org.apache.lucene.document.Document
import org.apache.lucene.index.DirectoryReader
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser
import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.store.Directory
import org.apache.lucene.store.MMapDirectory
import java.io.File
import java.nio.file.Path
import javax.xml.parsers.SAXParserFactory

@ExperimentalStdlibApi
fun main() {
    // val startTime = Instant.now()
    // index()
    // val endTime = Instant.now()
    //
    // val duration = Duration.between(startTime, endTime)
    // println("Time: ${duration.toMinutes()}m")

    search()
}

@ExperimentalStdlibApi
fun index() {
    val sourceFiles = arrayOf(
        File("data/WIR-Part1.rar"),
        File("data/WIR-Part2.rar")
    )
    val parser = SAXParserFactory.newInstance().newSAXParser()
    val handler = ParseHandler()

    for (sourceFile in sourceFiles) {
        val archive = Archive(sourceFile, null)
        for (fileHeader in archive) {
            val inputStream = archive.getInputStream(fileHeader)
            parser.parse(inputStream, handler)
        }
    }

    handler.close() // Required
}

fun search() {
    val directory: Directory = MMapDirectory(Path.of("E:/index"))
    val reader = DirectoryReader.open(directory)
    val searcher = IndexSearcher(reader)
    val analyzer = StandardAnalyzer()

    val input = "پدر و مادر"

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
    for (i in hits.indices) {
        val hitDoc: Document = searcher.doc(hits[i].doc)
        println(hitDoc)
    }

    reader.close()
    directory.close()
}
