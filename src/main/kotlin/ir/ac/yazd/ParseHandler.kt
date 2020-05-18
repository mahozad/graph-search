package ir.ac.yazd

import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler

class ParseHandler : DefaultHandler() {

    private var currentElement = ""
    private var docid = 0
    private var title = ""
    private var url = ""
    private var body = ""
    private val indexer = Indexer()

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
                indexer.index(docid, url, title, body)
            }
        }
    }

    fun close() = indexer.close()
}
