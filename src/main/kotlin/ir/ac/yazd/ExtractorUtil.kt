package ir.ac.yazd

import com.github.junrar.Archive
import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler
import java.io.File
import java.io.FileOutputStream
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.parsers.SAXParserFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult


@ExperimentalStdlibApi
fun main() {
    val sourceFiles = arrayOf(
        File("data/WIR-Part1.rar"),
        File("data/WIR-Part2.rar")
    )

    val docBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder()
    val doc = docBuilder.newDocument()
    val rootElement = doc.createElement("DOCS")
    doc.appendChild(rootElement)

    val parser = SAXParserFactory.newInstance().newSAXParser()
    val handler = object : DefaultHandler() {
        private var docsProcessed = 0

        private var currentElement = "Not set yet"
        private var docId = -1
        private var title = "Not set yet"
        private var body = "Not set yet"
        private var url = "Not set yet"

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
            if (name.toUpperCase() == "BODY") {
                val element = doc.createElement("DOC").apply {
                    appendChild(doc.createElement("DOCID").apply { textContent = docId.toString() })
                    appendChild(doc.createElement("TITLE").apply { textContent = title })
                    appendChild(doc.createElement("URL").apply { textContent = url })
                    appendChild(doc.createElement("BODY").apply { textContent = body })
                }
                rootElement.appendChild(element)
            }
        }

        override fun endDocument() {
            val tr = TransformerFactory.newInstance().newTransformer()
            tr.setOutputProperty(OutputKeys.INDENT, "yes")
            tr.setOutputProperty(OutputKeys.METHOD, "xml")
            tr.setOutputProperty(OutputKeys.ENCODING, "UTF-8")
            tr.setOutputProperty(OutputKeys.DOCTYPE_SYSTEM, "webir.dtd")
            tr.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2")
            // send DOM to file
            tr.transform(
                DOMSource(doc),
                StreamResult(FileOutputStream("E:/pure/webir-essentials.xml", true))
            )
            docsProcessed++
            print("\rProcessed ~${docsProcessed * 2200}")
        }
    }

    for (sourceFile in sourceFiles) {
        val archive = Archive(sourceFile, null)
        for (fileHeader in archive) {
            val inputStream = archive.getInputStream(fileHeader)
            parser.parse(inputStream, handler)
        }
    }
}
