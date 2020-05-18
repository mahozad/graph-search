package ir.ac.yazd

import com.github.junrar.Archive
import org.apache.lucene.analysis.Analyzer
import org.apache.lucene.analysis.standard.StandardAnalyzer
import org.apache.lucene.document.Document
import org.apache.lucene.document.Field
import org.apache.lucene.document.TextField
import org.apache.lucene.index.IndexWriter
import org.apache.lucene.index.IndexWriterConfig
import org.apache.lucene.store.FSDirectory
import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.nio.file.Path
import javax.xml.parsers.SAXParserFactory

fun main() {
    val sourceFiles = arrayOf(
        File("C:/Users/Mahdi/Desktop/New folder/data/WIR-Part1.rar"),
        File("C:/Users/Mahdi/Desktop/New folder/data/WIR-Part2.rar")
    )

    for (sourceFile in sourceFiles) {
        val archive = Archive(sourceFile, null)
        for (fileHeader in archive) {
            val inputStream = archive.getInputStream(fileHeader)
            val file = BufferedReader(InputStreamReader(inputStream))

            val handler: DefaultHandler = object : DefaultHandler() {

                private lateinit var currentElement: String
                private var docid = 0
                private var title = ""
                private var url = ""
                private var body = ""

                private var indexed = 0

                val analyzer: Analyzer
                val indexPath: Path
                val directory: FSDirectory
                val config: IndexWriterConfig
                val indexWriter: IndexWriter

                init {
                    analyzer = StandardAnalyzer()
                    indexPath = /*Files.createTempDirectory("index")*/ Path.of("index")
                    directory = FSDirectory.open(indexPath)
                    config = IndexWriterConfig(analyzer)
                    indexWriter = IndexWriter(directory, config)
                }

                override fun startElement(uri: String?, localName: String, qName: String, attributes: Attributes) {
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

                            indexed++
                            println("Indexed: $indexed")
                        }
                    }
                }

                override fun endElement(uri: String, localName: String, qName: String) {
                    if (qName.toUpperCase() == "WEBIR") indexWriter.close()
                }
            }

            val parser = SAXParserFactory.newInstance().newSAXParser()
            parser.parse(inputStream, handler)
        }
    }
}
