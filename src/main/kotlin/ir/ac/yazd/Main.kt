package ir.ac.yazd

import com.github.junrar.Archive
import java.io.File
import java.time.Duration
import java.time.Instant
import javax.xml.parsers.SAXParserFactory

fun main() {
    val sourceFiles = arrayOf(File("data/WIR-Part1.rar"), File("data/WIR-Part2.rar"))
    val parser = SAXParserFactory.newInstance().newSAXParser()
    val handler = ParseHandler()
    val startTime = Instant.now()

    for (sourceFile in sourceFiles) {
        val archive = Archive(sourceFile, null)
        for (fileHeader in archive) {
            val inputStream = archive.getInputStream(fileHeader)
            parser.parse(inputStream, handler)
        }
    }
    handler.close()

    println("Time: ${Duration.between(startTime, Instant.now()).toMinutes()}m")
}
