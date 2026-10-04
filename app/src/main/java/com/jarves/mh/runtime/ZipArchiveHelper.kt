package com.jarves.mh.runtime

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class ZipEntryInfo(
    val name: String,
    val sizeBytes: Long,
    val compressedSizeBytes: Long,
    val isDirectory: Boolean,
    val crc: Long,
    val lastModifiedMillis: Long,
) {
    val formattedSize: String get() = when {
        sizeBytes < 1024 -> "$sizeBytes B"
        sizeBytes < 1024 * 1024 -> "%.1f KB".format(sizeBytes / 1024.0)
        else -> "%.1f MB".format(sizeBytes / (1024.0 * 1024.0))
    }
}

data class ZipArchiveSummary(
    val archiveName: String,
    val archiveSizeBytes: Long,
    val totalEntries: Int,
    val fileCount: Int,
    val dirCount: Int,
    val uncompressedSizeBytes: Long,
    val entries: List<ZipEntryInfo>,
)

object ZipArchiveHelper {

    fun inspectZip(zipFile: File): ZipArchiveSummary {
        require(zipFile.isFile) { "Zip file does not exist: ${zipFile.absolutePath}" }
        val entries = mutableListOf<ZipEntryInfo>()
        var fileCount = 0
        var dirCount = 0
        var totalUncompressed = 0L

        ZipFile(zipFile).use { zip ->
            val enumEntries = zip.entries()
            while (enumEntries.hasMoreElements()) {
                val entry = enumEntries.nextElement()
                val isDir = entry.isDirectory
                if (isDir) dirCount++ else fileCount++
                val size = if (entry.size >= 0) entry.size else 0L
                totalUncompressed += size
                entries.add(
                    ZipEntryInfo(
                        name = entry.name,
                        sizeBytes = size,
                        compressedSizeBytes = if (entry.compressedSize >= 0) entry.compressedSize else 0L,
                        isDirectory = isDir,
                        crc = entry.crc,
                        lastModifiedMillis = entry.time,
                    )
                )
            }
        }

        return ZipArchiveSummary(
            archiveName = zipFile.name,
            archiveSizeBytes = zipFile.length(),
            totalEntries = entries.size,
            fileCount = fileCount,
            dirCount = dirCount,
            uncompressedSizeBytes = totalUncompressed,
            entries = entries.sortedWith(compareBy({ !it.isDirectory }, { it.name })),
        )
    }

    /**
     * Safely extracts files from a zip archive.
     * Prevents Zip Slip vulnerabilities by verifying canonical output paths.
     */
    fun extractZip(
        zipFile: File,
        targetDir: File,
        entryPaths: Set<String>? = null,
    ): List<File> {
        require(zipFile.isFile) { "Zip file does not exist: ${zipFile.absolutePath}" }
        targetDir.mkdirs()
        val canonicalDest = targetDir.canonicalFile
        val extractedFiles = mutableListOf<File>()

        ZipInputStream(BufferedInputStream(FileInputStream(zipFile))).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val name = entry.name.removePrefix("./").trimStart('/')
                if (name.isNotBlank() && (entryPaths == null || entryPaths.contains(name) || entryPaths.contains(entry.name))) {
                    val destFile = File(canonicalDest, name)
                    val canonicalFile = destFile.canonicalFile
                    check(canonicalFile.path.startsWith(canonicalDest.path)) {
                        "Unsafe Zip Entry (path traversal): ${entry.name}"
                    }

                    if (entry.isDirectory) {
                        destFile.mkdirs()
                    } else {
                        destFile.parentFile?.mkdirs()
                        BufferedOutputStream(FileOutputStream(destFile)).use { output ->
                            zis.copyTo(output, 64 * 1024)
                        }
                        extractedFiles.add(destFile)
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        return extractedFiles
    }

    /**
     * Updates an existing zip file or creates a new one by adding, replacing, or deleting entries.
     */
    fun updateZip(
        zipFile: File,
        filesToAdd: Map<String, File>,
        pathsToRemove: Set<String> = emptySet(),
    ): File {
        val tempZip = File(zipFile.parentFile ?: File("."), "${zipFile.name}.tmp_${System.currentTimeMillis()}")
        val processedPaths = mutableSetOf<String>()

        ZipOutputStream(BufferedOutputStream(FileOutputStream(tempZip))).use { zos ->
            // Copy existing entries that aren't removed or overwritten
            if (zipFile.isFile) {
                ZipInputStream(BufferedInputStream(FileInputStream(zipFile))).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        val name = entry.name
                        if (name !in pathsToRemove && name !in filesToAdd) {
                            zos.putNextEntry(ZipEntry(name))
                            zis.copyTo(zos, 64 * 1024)
                            zos.closeEntry()
                            processedPaths.add(name)
                        }
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }
            }

            // Write added or updated files
            for ((relativePath, file) in filesToAdd) {
                val cleanPath = relativePath.removePrefix("/").replace('\\', '/')
                if (file.isDirectory) {
                    val dirEntry = if (cleanPath.endsWith('/')) cleanPath else "$cleanPath/"
                    zos.putNextEntry(ZipEntry(dirEntry))
                    zos.closeEntry()
                } else if (file.isFile) {
                    zos.putNextEntry(ZipEntry(cleanPath))
                    BufferedInputStream(FileInputStream(file)).use { fis ->
                        fis.copyTo(zos, 64 * 1024)
                    }
                    zos.closeEntry()
                }
                processedPaths.add(cleanPath)
            }
        }

        if (zipFile.exists()) zipFile.delete()
        check(tempZip.renameTo(zipFile)) { "Failed to replace ${zipFile.absolutePath}" }
        return zipFile
    }

    /**
     * Creates a new zip file containing the specified directory or files.
     */
    fun createZip(
        sourceDir: File,
        destinationZip: File,
        relativePaths: List<String>? = null,
    ): File {
        require(sourceDir.isDirectory) { "Source directory does not exist: ${sourceDir.absolutePath}" }
        destinationZip.parentFile?.mkdirs()
        val tempZip = File(destinationZip.parentFile, "${destinationZip.name}.tmp_${System.currentTimeMillis()}")

        ZipOutputStream(BufferedOutputStream(FileOutputStream(tempZip))).use { zos ->
            val filesToInclude = if (relativePaths != null) {
                relativePaths.map { File(sourceDir, it) }
            } else {
                sourceDir.walkTopDown().filter { it != sourceDir }.toList()
            }

            for (file in filesToInclude) {
                if (!file.exists()) continue
                val relative = file.relativeTo(sourceDir).invariantSeparatorsPath
                if (file.isDirectory) {
                    val entryName = if (relative.endsWith('/')) relative else "$relative/"
                    zos.putNextEntry(ZipEntry(entryName))
                    zos.closeEntry()
                } else if (file.isFile) {
                    zos.putNextEntry(ZipEntry(relative))
                    BufferedInputStream(FileInputStream(file)).use { fis ->
                        fis.copyTo(zos, 64 * 1024)
                    }
                    zos.closeEntry()
                }
            }
        }

        if (destinationZip.exists()) destinationZip.delete()
        check(tempZip.renameTo(destinationZip)) { "Failed to write ${destinationZip.absolutePath}" }
        return destinationZip
    }

    fun formatZipSummaryForAgent(summary: ZipArchiveSummary): String = buildString {
        appendLine("Archive: ${summary.archiveName} (${summary.totalEntries} entries, ${summary.fileCount} files, ${summary.dirCount} folders)")
        appendLine("Uncompressed size: %.1f KB".format(summary.uncompressedSizeBytes / 1024.0))
        appendLine("Entries:")
        summary.entries.take(50).forEach { entry ->
            val type = if (entry.isDirectory) "[DIR ]" else "[FILE]"
            appendLine("  $type ${entry.name} (${entry.formattedSize})")
        }
        if (summary.entries.size > 50) {
            appendLine("  ... and ${summary.entries.size - 50} more entries")
        }
    }
}
