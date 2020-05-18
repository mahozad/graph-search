package ir.ac.yazd

import org.apache.lucene.analysis.standard.StandardAnalyzer
import org.apache.lucene.document.Document
import org.apache.lucene.document.Field
import org.apache.lucene.document.TextField
import org.apache.lucene.index.IndexWriter
import org.apache.lucene.index.IndexWriterConfig
import org.apache.lucene.store.FSDirectory
import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler
import java.nio.file.Path

class ParseHandler : DefaultHandler() {

    private var currentElement = ""
    private var docid = 0
    private var title = ""
    private var url = ""
    private var body = ""

    private val analyzer = StandardAnalyzer()
    private val indexPath = Path.of("index")
    private val directory = FSDirectory.open(indexPath)
    private val config = IndexWriterConfig(analyzer)
    private val indexWriter = IndexWriter(directory, config)

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
