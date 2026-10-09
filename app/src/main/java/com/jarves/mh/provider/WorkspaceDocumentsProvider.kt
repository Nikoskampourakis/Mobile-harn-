package com.jarves.mh.provider

import android.database.Cursor
import android.database.MatrixCursor
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract.Document
import android.provider.DocumentsContract.Root
import android.provider.DocumentsProvider
import android.webkit.MimeTypeMap
import com.jarves.mh.R
import com.jarves.mh.data.AppPreferences
import java.io.File
import java.io.FileNotFoundException

/**
 * Storage Access Framework DocumentsProvider exposing PocketDev projects and workspace files
 * to the Android system Files app, Acode, and other external code editors/file managers.
 */
class WorkspaceDocumentsProvider : DocumentsProvider() {

    companion object {
        const val ROOT_ID = "workspaces"
        const val ROOT_DOC_ID = "root"

        private val DEFAULT_ROOT_PROJECTION = arrayOf(
            Root.COLUMN_ROOT_ID,
            Root.COLUMN_DOCUMENT_ID,
            Root.COLUMN_TITLE,
            Root.COLUMN_SUMMARY,
            Root.COLUMN_FLAGS,
            Root.COLUMN_MIME_TYPES,
            Root.COLUMN_ICON,
            Root.COLUMN_AVAILABLE_BYTES,
        )

        private val DEFAULT_DOCUMENT_PROJECTION = arrayOf(
            Document.COLUMN_DOCUMENT_ID,
            Document.COLUMN_MIME_TYPE,
            Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_LAST_MODIFIED,
            Document.COLUMN_FLAGS,
            Document.COLUMN_SIZE,
        )
    }

    private fun getBaseDir(): File {
        val dir = File(context?.filesDir ?: throw IllegalStateException("Context is null"), "workspaces")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    override fun onCreate(): Boolean = true

    override fun queryRoots(projection: Array<String>?): Cursor {
        val result = MatrixCursor(projection ?: DEFAULT_ROOT_PROJECTION)
        val baseDir = getBaseDir()
        val row = result.newRow()
        row.add(Root.COLUMN_ROOT_ID, ROOT_ID)
        row.add(Root.COLUMN_DOCUMENT_ID, ROOT_DOC_ID)
        row.add(Root.COLUMN_TITLE, context?.getString(R.string.app_name) ?: "PocketDev")
        row.add(Root.COLUMN_SUMMARY, "Projects & Workspace Files (Acode compatible)")
        row.add(
            Root.COLUMN_FLAGS,
            Root.FLAG_SUPPORTS_CREATE or Root.FLAG_SUPPORTS_IS_CHILD or Root.FLAG_LOCAL_ONLY
        )
        row.add(Root.COLUMN_MIME_TYPES, "*/*")
        row.add(Root.COLUMN_ICON, R.mipmap.ic_launcher)
        row.add(Root.COLUMN_AVAILABLE_BYTES, baseDir.freeSpace)
        return result
    }

    override fun queryDocument(documentId: String, projection: Array<String>?): Cursor {
        val result = MatrixCursor(projection ?: DEFAULT_DOCUMENT_PROJECTION)
        val file = getFileForDocId(documentId)
        includeFile(result, documentId, file)
        return result
    }

    override fun queryChildDocuments(
        parentDocumentId: String,
        projection: Array<String>?,
        sortOrder: String?
    ): Cursor {
        val result = MatrixCursor(projection ?: DEFAULT_DOCUMENT_PROJECTION)
        val parent = getFileForDocId(parentDocumentId)
        val children = parent.listFiles() ?: return result
        children
            .filter { !it.name.startsWith(".git_tmp") }
            .sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
            .forEach { child ->
                val childDocId = getDocIdForFile(child)
                includeFile(result, childDocId, child)
            }
        return result
    }

    override fun openDocument(
        documentId: String,
        mode: String,
        signal: CancellationSignal?
    ): ParcelFileDescriptor {
        val file = getFileForDocId(documentId)
        val accessMode = ParcelFileDescriptor.parseMode(mode)
        return ParcelFileDescriptor.open(file, accessMode)
    }

    override fun createDocument(
        parentDocumentId: String,
        mimeType: String,
        displayName: String
    ): String {
        val parent = getFileForDocId(parentDocumentId)
        val newFile = File(parent, displayName)
        if (mimeType == Document.MIME_TYPE_DIR) {
            if (!newFile.mkdirs()) {
                throw FileNotFoundException("Failed to create directory $displayName in $parentDocumentId")
            }
        } else {
            if (!newFile.createNewFile()) {
                throw FileNotFoundException("Failed to create file $displayName in $parentDocumentId")
            }
        }
        return getDocIdForFile(newFile)
    }

    override fun deleteDocument(documentId: String) {
        val file = getFileForDocId(documentId)
        if (!file.deleteRecursively()) {
            throw FileNotFoundException("Failed to delete document $documentId")
        }
    }

    override fun renameDocument(documentId: String, displayName: String): String {
        val file = getFileForDocId(documentId)
        val newFile = File(file.parentFile, displayName)
        if (!file.renameTo(newFile)) {
            throw FileNotFoundException("Failed to rename document $documentId to $displayName")
        }
        return getDocIdForFile(newFile)
    }

    override fun isChildDocument(parentDocumentId: String, documentId: String): Boolean {
        val parent = getFileForDocId(parentDocumentId).canonicalFile
        val child = getFileForDocId(documentId).canonicalFile
        return child.path.startsWith(parent.path)
    }

    private fun getFileForDocId(docId: String): File {
        val base = getBaseDir().canonicalFile
        if (docId == ROOT_DOC_ID || docId.isBlank()) {
            return base
        }
        val clean = docId.removePrefix("path:").trimStart('/')
        val target = File(base, clean).canonicalFile
        if (!target.path.startsWith(base.path)) {
            throw FileNotFoundException("Security check failed: Document outside workspaces: $docId")
        }
        if (!target.exists()) {
            // Auto-create directories if querying a known workspace root
            if (clean.split('/').size == 1) {
                target.mkdirs()
            } else {
                throw FileNotFoundException("Document not found: $docId")
            }
        }
        return target
    }

    private fun getDocIdForFile(file: File): String {
        val base = getBaseDir().canonicalFile
        val canon = file.canonicalFile
        if (canon == base) return ROOT_DOC_ID
        val rel = canon.toRelativeString(base)
        return "path:$rel"
    }

    private fun includeFile(result: MatrixCursor, docId: String, file: File) {
        var flags = 0
        if (file.isDirectory) {
            flags = flags or Document.FLAG_DIR_SUPPORTS_CREATE
        } else {
            flags = flags or Document.FLAG_SUPPORTS_WRITE or
                Document.FLAG_SUPPORTS_DELETE or
                Document.FLAG_SUPPORTS_RENAME or
                Document.FLAG_SUPPORTS_COPY
        }

        var displayName = file.name
        if (docId == ROOT_DOC_ID) {
            displayName = "Workspaces"
        } else if (file.parentFile?.canonicalPath == getBaseDir().canonicalPath) {
            val ctx = context
            if (ctx != null) {
                val projects = runCatching { AppPreferences(ctx).loadProjects() }.getOrDefault(emptyList())
                val matchingProject = projects.firstOrNull { it.id == file.name }
                if (matchingProject != null) {
                    displayName = matchingProject.name
                }
            }
        }

        val row = result.newRow()
        row.add(Document.COLUMN_DOCUMENT_ID, docId)
        row.add(Document.COLUMN_DISPLAY_NAME, displayName)
        row.add(Document.COLUMN_SIZE, if (file.isDirectory) 0L else file.length())
        row.add(Document.COLUMN_MIME_TYPE, getTypeForFile(file))
        row.add(Document.COLUMN_LAST_MODIFIED, file.lastModified())
        row.add(Document.COLUMN_FLAGS, flags)
    }

    private fun getTypeForFile(file: File): String {
        if (file.isDirectory) {
            return Document.MIME_TYPE_DIR
        }
        val name = file.name
        val lastDot = name.lastIndexOf('.')
        if (lastDot >= 0) {
            val extension = name.substring(lastDot + 1).lowercase()
            when (extension) {
                "kt", "kts" -> return "text/x-kotlin"
                "java" -> return "text/x-java-source"
                "py" -> return "text/x-python"
                "js", "mjs", "cjs" -> return "text/javascript"
                "ts", "tsx" -> return "text/typescript"
                "html", "htm" -> return "text/html"
                "css" -> return "text/css"
                "json" -> return "application/json"
                "xml" -> return "text/xml"
                "md", "markdown" -> return "text/markdown"
                "txt", "log" -> return "text/plain"
                "sh", "bash" -> return "application/x-sh"
                "yaml", "yml" -> return "text/yaml"
                "png" -> return "image/png"
                "jpg", "jpeg" -> return "image/jpeg"
                "svg" -> return "image/svg+xml"
                "webp" -> return "image/webp"
                "gif" -> return "image/gif"
                "zip" -> return "application/zip"
            }
            val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
            if (mime != null) return mime
        }
        return "text/plain"
    }
}
