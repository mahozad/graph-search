package ir.ac.yazd

import ir.ac.yazd.ScoreStrategy.WITH_PAGE_RANK
import org.apache.lucene.analysis.standard.StandardAnalyzer
import org.apache.lucene.document.Document
import org.apache.lucene.document.FeatureField
import org.apache.lucene.document.Field.Store
import org.apache.lucene.document.StoredField
import org.apache.lucene.document.TextField
import org.apache.lucene.index.IndexWriter
import org.apache.lucene.index.IndexWriterConfig
import org.apache.lucene.index.IndexWriterConfig.OpenMode.CREATE
import org.apache.lucene.search.similarities.BM25Similarity
import org.apache.lucene.store.MMapDirectory
import java.nio.file.Files
import java.nio.file.Path

class Indexer(private val scoreStrategy: ScoreStrategy) {

    private var indexPath = if (scoreStrategy == WITH_PAGE_RANK) Path.of("E:/index-pageranked") else Path.of("E:index")

    init {
        if (Files.exists(indexPath)) Files.list(indexPath).forEach(Files::delete)
    }

    private val directory = MMapDirectory.open(indexPath)
    // NOTE: StandardAnalyzer seems to work better than PersianAnalyzer or SimpleAnalyzer
    private val analyzer = StandardAnalyzer()
    private val config = IndexWriterConfig(analyzer)
    private val indexWriter = IndexWriter(directory, config)
    private lateinit var pageRankScores: Map<Int, Float>

    init {
        config.openMode = CREATE
        config.ramBufferSizeMB = 128.0 // To increase performance
        config.similarity = BM25Similarity() // NOTE: This should be same as the one used when searching
        if (scoreStrategy == WITH_PAGE_RANK) {
            pageRankScores = Files.newBufferedReader(Path.of("scores.txt")).lineSequence()
                .associate { it.substringBefore(" ").toInt() to it.substringAfter(" ").toFloat() }
        }
    }

    fun index(docId: Int, url: String, title: String, body: String) {
        val document = Document().apply {
            add(StoredField("DOCID", docId)) // If you want to search on it, use IntPoint field instead
            add(StoredField("URL", url))
            add(TextField("TITLE", title, Store.NO))
            add(TextField("BODY", body, Store.NO))
            // FIXME: By adding this feature field, number of stored docs decreases from about 997K to 370K
            if (scoreStrategy == WITH_PAGE_RANK) {
                add(FeatureField("Features", "PageRank", pageRankScores.getValue(docId)))
            }
        }
        indexWriter.addDocument(document)
    }

    fun close() = indexWriter.close()
}
