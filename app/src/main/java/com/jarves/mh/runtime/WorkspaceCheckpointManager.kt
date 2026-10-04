package com.jarves.mh.runtime

import android.content.Context
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Manages lightweight workspace checkpoints so users can restore older builds
 * or revert code changes to previous points in the chat conversation.
 */
class WorkspaceCheckpointManager(private val context: Context) {
    private val checkpointsRoot: File
        get() = File(context.filesDir, "checkpoints").apply { mkdirs() }

    private val ignoredPaths = setOf(
        ".git",
        "node_modules",
        "build",
        ".gradle",
        "attachments",
        ".cxx",
        "bin",
        "obj",
    )

    fun hasCheckpoint(projectId: String, checkpointId: String): Boolean {
        val file = checkpointZipFile(projectId, checkpointId)
        return file.isFile && file.length() > 0L
    }

    fun saveCheckpoint(projectId: String, checkpointId: String, workspaceDir: File): Boolean {
        if (!workspaceDir.isDirectory) return false
        val zipFile = checkpointZipFile(projectId, checkpointId)
        val tempZip = File(zipFile.parentFile ?: checkpointsRoot, ".${zipFile.name}.tmp")
        return runCatching {
            tempZip.parentFile?.mkdirs()
            ZipOutputStream(FileOutputStream(tempZip).buffered()).use { zipOut ->
                val rootPath = workspaceDir.canonicalFile.toPath()
                workspaceDir.walkTopDown()
                    .onEnter { dir ->
                        val name = dir.name
                        name !in ignoredPaths && !name.startsWith(".")
                    }
                    .forEach { file ->
                        val relative = rootPath.relativize(file.canonicalFile.toPath()).toString()
                        if (relative.isNotBlank()) {
                            val segments = relative.split(File.separatorChar)
                            val isIgnored = segments.any { it in ignoredPaths }
                            if (!isIgnored) {
                                if (file.isDirectory) {
                                    val entryName = if (relative.endsWith("/")) relative else "$relative/"
                                    zipOut.putNextEntry(ZipEntry(entryName))
                                    zipOut.closeEntry()
                                } else if (file.isFile && file.length() < 10_000_000L) { // max 10MB per file
                                    zipOut.putNextEntry(ZipEntry(relative))
                                    FileInputStream(file).buffered().use { inStream ->
                                        inStream.copyTo(zipOut)
                                    }
                                    zipOut.closeEntry()
                                }
                            }
                        }
                    }
            }
            if (tempZip.renameTo(zipFile)) {
                true
            } else {
                tempZip.copyTo(zipFile, overwrite = true)
                tempZip.delete()
                true
            }
        }.getOrElse {
            tempZip.delete()
            false
        }
    }

    fun restoreCheckpoint(projectId: String, checkpointId: String, workspaceDir: File): Boolean {
        val zipFile = checkpointZipFile(projectId, checkpointId)
        if (!zipFile.isFile) return false
        return runCatching {
            workspaceDir.mkdirs()
            // Clean non-ignored files in workspace
            val rootPath = workspaceDir.canonicalFile.toPath()
            workspaceDir.walkBottomUp().forEach { file ->
                if (file != workspaceDir) {
                    val rel = rootPath.relativize(file.canonicalFile.toPath()).toString()
                    val segments = rel.split(File.separatorChar)
                    val isIgnored = segments.any { it in ignoredPaths }
                    if (!isIgnored) {
                        if (file.isFile) file.delete()
                        else if (file.isDirectory && file.listFiles().isNullOrEmpty()) file.delete()
                    }
                }
            }

            // Extract checkpoint files
            ZipInputStream(FileInputStream(zipFile).buffered()).use { zipIn ->
                var entry = zipIn.nextEntry
                while (entry != null) {
                    val outFile = File(workspaceDir, entry.name).canonicalFile
                    // Guard against Zip Slip
                    if (outFile.toPath().startsWith(rootPath)) {
                        if (entry.isDirectory) {
                            outFile.mkdirs()
                        } else {
                            outFile.parentFile?.mkdirs()
                            FileOutputStream(outFile).buffered().use { outStream ->
                                zipIn.copyTo(outStream)
                            }
                        }
                    }
                    zipIn.closeEntry()
                    entry = zipIn.nextEntry
                }
            }
            true
        }.getOrDefault(false)
    }

    fun deleteProjectCheckpoints(projectId: String) {
        File(checkpointsRoot, projectId).deleteRecursively()
    }

    private fun checkpointZipFile(projectId: String, checkpointId: String): File {
        val dir = File(checkpointsRoot, projectId).apply { mkdirs() }
        val safeId = checkpointId.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        return File(dir, "$safeId.zip")
    }
}
