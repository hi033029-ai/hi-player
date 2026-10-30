package com.example.ui.screens

import com.example.model.FileItem
import com.example.model.FileType
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class RootFileSectionsTest {
    private fun item(name: String, type: FileType = FileType.OTHER) = FileItem(File("/root/$name"), name, "/root/$name",
        type == FileType.DIRECTORY, fileType = type)
    @Test fun eachPathBelongsToOneSectionAndRootApksAreNotLost() {
        val zip = item("archive.zip", FileType.ARCHIVE)
        val apk = item("app.apk")
        val doc = item("notes.pdf", FileType.DOCUMENT)
        val folder = item("Download", FileType.DIRECTORY)
        val other = item("file.bin")
        val sections = RootFileSections.from(listOf(zip), listOf(doc), listOf(zip, apk, doc, folder, other))
        assertEquals(listOf(zip, apk), sections.archives)
        assertEquals(listOf(doc), sections.documents)
        assertEquals(listOf(folder, other), sections.folders)
        val all = SelectAllScope.entries.flatMap { sections.items(it) }
        assertEquals(5, all.size)
        assertEquals(all.size, all.map { it.path }.toSet().size)
    }
}
