package ir.ac.yazd

import com.github.junrar.Archive
import org.apache.lucene.analysis.standard.StandardAnalyzer
import org.apache.lucene.document.Document
import org.apache.lucene.document.Field
import org.apache.lucene.document.TextField
import org.apache.lucene.index.IndexWriter
import org.apache.lucene.index.IndexWriterConfig
import org.apache.lucene.store.FSDirectory
import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler
import java.io.File
import java.nio.file.Path
import javax.xml.parsers.SAXParserFactory

fun main() {
    val sourceFiles = arrayOf(
        File("C:/Users/Mahdi/Desktop/New folder/data/WIR-Part1.rar"),
        File("C:/Users/Mahdi/Desktop/New folder/data/WIR-Part2.rar")
    )

    val parser = SAXParserFactory.newInstance().newSAXParser()
    val handler = object : DefaultHandler() {

        private lateinit var currentElement: String
        private var docid = 0
        private var title = ""
        private var url = ""
        private var body = ""

        val analyzer = StandardAnalyzer()
        val indexPath = Path.of("index")
        val directory = FSDirectory.open(indexPath)
        val config = IndexWriterConfig(analyzer)
        val indexWriter = IndexWriter(directory, config)

        override fun startElement(uri: String, localName: String, qName: String, attributes: Attributes) {
            currentElement = qName.toUpperCase()
        }

        override fun characters(chars: CharArray, start: Int, length: Int) {
            when (currentElement) {
                "DOCID" -> docid = String(chars.copyOfRange(start, start + length)).toInt()
                "URL" -> url = String(chars.copyOfRange(start, start + length))
                "TITLE" -> title = String(chars.copyOfRange(start, start + length))
                "BODY" -> {
                    body = String(chars.copyOfRange(start, start + length))
                    val document = Document()
                    // document.add(Field("DOCID", docid, IntPoint))
                    document.add(Field("URL", url, TextField.TYPE_STORED))
                    document.add(Field("TITLE", title, TextField.TYPE_STORED))
                    document.add(Field("BODY", body, TextField.TYPE_STORED))
                    indexWriter.addDocument(document)
                }
            }
        }

        fun close() {
            indexWriter.close()
        }
    }

    for (sourceFile in sourceFiles) {
        val archive = Archive(sourceFile, null)
        for (file in archive) {
            val inputStream = archive.getInputStream(file)
            parser.parse(inputStream, handler)
        }
    }

    handler.close()
}
