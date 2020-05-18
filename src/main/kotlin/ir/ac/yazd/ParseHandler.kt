package ir.ac.yazd

import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler

@ExperimentalStdlibApi // For concatToString()
class ParseHandler : DefaultHandler() {

    private var currentElement = "Not set yet"
    private var docId = -1
    private var title = "Not set yet"
    private var body = "Not set yet"
    private var url = "Not set yet"
    private val indexer = Indexer()

    override fun startElement(uri: String, localName: String, name: String, attrs: Attributes) {
        currentElement = name.toUpperCase()
    }

    override fun characters(chars: CharArray, start: Int, length: Int) {
        when (currentElement) {
            "DOCID" -> docId = chars.concatToString(start, start + length).toInt()
            "TITLE" -> title = chars.concatToString(start, start + length)
            "URL" -> url = chars.concatToString(start, start + length)
            "BODY" -> body = chars.concatToString(start, start + length)
        }
    }

    override fun endElement(uri: String, localName: String, name: String) {
        if (name.toUpperCase() == "DOC") indexer.index(docId, url, title, body)
    }

    fun close() = indexer.close()
}
