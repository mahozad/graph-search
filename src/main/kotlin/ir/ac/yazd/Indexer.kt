package ir.ac.yazd

import org.apache.lucene.analysis.standard.StandardAnalyzer
import org.apache.lucene.document.Document
import org.apache.lucene.document.Field.Store
import org.apache.lucene.document.StoredField
import org.apache.lucene.document.TextField
import org.apache.lucene.index.IndexWriter
import org.apache.lucene.index.IndexWriterConfig
import org.apache.lucene.index.IndexWriterConfig.OpenMode.CREATE
import org.apache.lucene.store.MMapDirectory
import java.nio.file.Path

private val indexPath = Path.of("E:/index")

class Indexer {

    private val directory = MMapDirectory.open(indexPath)
    private val config = IndexWriterConfig(StandardAnalyzer()).apply { openMode = CREATE }
    private val indexWriter = IndexWriter(directory, config)

    fun index(docId: Int, url: String, title: String, body: String) {
        val document = Document().apply {
            add(StoredField("DOCID", docId)) // If you want to search on it, use IntPoint field instead
            add(StoredField("URL", url))
            add(TextField("TITLE", title, Store.NO))
            add(TextField("BODY", body, Store.NO))
        }
        indexWriter.addDocument(document)
    }

    fun close() = indexWriter.close()
}
