package ir.ac.yazd

import org.apache.lucene.analysis.standard.StandardAnalyzer
import org.apache.lucene.document.*
import org.apache.lucene.index.IndexWriter
import org.apache.lucene.index.IndexWriterConfig
import org.apache.lucene.store.MMapDirectory
import java.nio.file.Path

private val indexPath = Path.of("E:/index")

class Indexer {

    private val directory = MMapDirectory.open(indexPath)
    private val config = IndexWriterConfig(StandardAnalyzer())
    private val indexWriter = IndexWriter(directory, config)

    fun index(docId: Int, url: String, title: String, body: String) {
        val document = Document().apply {
            add(StoredField("DOCID", docId)) // If you want to search on it, use IntPoint field instead
            add(StringField("URL", url, Field.Store.NO))
            add(TextField("TITLE", title, Field.Store.NO))
            add(TextField("BODY", body, Field.Store.NO))
        }
        indexWriter.addDocument(document)
    }

    fun close() = indexWriter.close()
}
