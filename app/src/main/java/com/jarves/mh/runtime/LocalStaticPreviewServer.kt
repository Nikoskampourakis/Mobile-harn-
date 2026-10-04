package com.jarves.mh.runtime

import android.util.Log
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Lightweight in-app static preview server running on loopback (127.0.0.1).
 * Automatically serves HTML/JS/CSS web projects from the active workspace directory
 * without requiring the user or mobile VM to manually spin up an external web server.
 */
class LocalStaticPreviewServer : AutoCloseable {
    private val running = AtomicBoolean(false)
    private var serverSocket: ServerSocket? = null
    @Volatile private var baseDir: File? = null
    @Volatile private var boundPort: Int = 0
    private val executor = Executors.newFixedThreadPool(4)

    val port: Int get() = boundPort
    val url: String get() = if (boundPort > 0) "http://127.0.0.1:$boundPort" else ""

    fun isRunning(): Boolean = running.get() && serverSocket?.isClosed == false

    @Synchronized
    fun start(workspaceDirectory: File): LocalStaticPreviewServer {
        val root = resolveWebRoot(workspaceDirectory)
        baseDir = root
        if (running.get() && serverSocket?.isClosed == false) {
            return this
        }
        running.set(true)
        val socket = ServerSocket(0, 16, InetAddress.getByName("127.0.0.1"))
        serverSocket = socket
        boundPort = socket.localPort
        Log.d("StaticPreview", "Started static preview server on port $boundPort for $root")

        Thread({
            while (running.get() && !socket.isClosed) {
                try {
                    val client = socket.accept()
                    executor.execute {
                        runCatching { handleRequest(client) }
                    }
                } catch (e: Exception) {
                    if (!running.get()) break
                    Log.e("StaticPreview", "Error in accept loop", e)
                    Thread.sleep(1000)
                }
            }
        }, "static-preview-server").apply {
            isDaemon = true
            start()
        }
        return this
    }

    @Synchronized
    fun updateWorkspace(workspaceDirectory: File) {
        val root = resolveWebRoot(workspaceDirectory)
        baseDir = root
        if (!isRunning()) {
            start(workspaceDirectory)
        }
    }

    private fun handleRequest(socket: Socket) {
        socket.use { s ->
            val input = BufferedInputStream(s.getInputStream())
            val output = BufferedOutputStream(s.getOutputStream())
            val requestLine = readLine(input) ?: return
            val parts = requestLine.split(" ")
            if (parts.size < 2) return
            val method = parts[0].uppercase()
            val rawPath = parts[1].substringBefore("?")
            val decodedPath = runCatching { URLDecoder.decode(rawPath, "UTF-8") }.getOrDefault(rawPath)

            val root = baseDir
            if (root == null || !root.isDirectory) {
                writeError(output, 404, "No workspace directory active")
                return
            }

            var targetFile = File(root, decodedPath.removePrefix("/")).canonicalFile
            if (!targetFile.toPath().startsWith(root.toPath())) {
                writeError(output, 403, "Access denied")
                return
            }

            if (targetFile.isDirectory) {
                val indexFile = File(targetFile, "index.html")
                if (indexFile.isFile) {
                    targetFile = indexFile
                } else {
                    // Try to find any html file in the root
                    val anyHtml = targetFile.listFiles()?.firstOrNull { it.isFile && it.name.endsWith(".html", ignoreCase = true) }
                    if (anyHtml != null) {
                        targetFile = anyHtml
                    }
                }
            }

            // SPA fallback: if file does not exist but root index.html exists, serve root index.html
            if (!targetFile.exists() || !targetFile.isFile) {
                val rootIndex = File(root, "index.html")
                if (rootIndex.isFile) {
                    targetFile = rootIndex
                } else {
                    writeError(output, 404, "File not found: $decodedPath")
                    return
                }
            }

            val mimeType = mimeTypeFor(targetFile)
            val fileLength = targetFile.length()

            val header = buildString {
                append("HTTP/1.1 200 OK\r\n")
                append("Content-Type: $mimeType\r\n")
                append("Content-Length: $fileLength\r\n")
                append("Access-Control-Allow-Origin: *\r\n")
                append("Cache-Control: no-cache, no-store, must-revalidate\r\n")
                append("Connection: close\r\n\r\n")
            }
            output.write(header.toByteArray(Charsets.UTF_8))

            if (method != "HEAD") {
                FileInputStream(targetFile).use { fileStream ->
                    val buffer = ByteArray(32 * 1024)
                    var bytesRead: Int
                    while (fileStream.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                    }
                }
            }
            output.flush()
        }
    }

    private fun writeError(output: BufferedOutputStream, code: Int, message: String) {
        val statusText = if (code == 404) "Not Found" else "Forbidden"
        val body = "<html><body><h1>$code $statusText</h1><p>$message</p></body></html>"
        val bytes = body.toByteArray(Charsets.UTF_8)
        val header = "HTTP/1.1 $code $statusText\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: ${bytes.size}\r\nAccess-Control-Allow-Origin: *\r\nConnection: close\r\n\r\n"
        output.write(header.toByteArray(Charsets.UTF_8))
        output.write(bytes)
        output.flush()
    }

    private fun readLine(input: BufferedInputStream): String? {
        val bytes = ArrayList<Byte>()
        while (true) {
            val value = input.read()
            if (value < 0) return if (bytes.isEmpty()) null else bytes.toByteArray().decodeToString()
            if (value == '\n'.code) return bytes.toByteArray().decodeToString().trimEnd('\r')
            bytes += value.toByte()
        }
    }

    private fun mimeTypeFor(file: File): String = when (file.extension.lowercase()) {
        "html", "htm" -> "text/html; charset=utf-8"
        "js", "mjs" -> "application/javascript; charset=utf-8"
        "css" -> "text/css; charset=utf-8"
        "json" -> "application/json; charset=utf-8"
        "svg" -> "image/svg+xml"
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        "ico" -> "image/x-icon"
        "wasm" -> "application/wasm"
        "woff" -> "font/woff"
        "woff2" -> "font/woff2"
        "ttf" -> "font/ttf"
        "txt", "md" -> "text/plain; charset=utf-8"
        else -> "application/octet-stream"
    }

    companion object {
        /**
         * Discovers the best web root directory in the given workspace.
         * Priority: dist/ > build/ > public/ > out/ > www/ > src/ > workspace root.
         */
        fun resolveWebRoot(workspaceDir: File): File {
            if (!workspaceDir.isDirectory) return workspaceDir
            val subdirs = listOf("dist", "build", "public", "out", "www", "src")
            for (sub in subdirs) {
                val candidate = File(workspaceDir, sub)
                if (candidate.isDirectory && File(candidate, "index.html").isFile) {
                    return candidate
                }
            }
            if (File(workspaceDir, "index.html").isFile) {
                return workspaceDir
            }
            // If any html file exists in workspace
            val rootHtml = workspaceDir.listFiles()?.firstOrNull { it.isFile && it.name.endsWith(".html", ignoreCase = true) }
            if (rootHtml != null) {
                return workspaceDir
            }
            // Check 1 level deep for any index.html
            val nested = workspaceDir.walkTopDown().maxDepth(3).firstOrNull { it.isFile && it.name.equals("index.html", ignoreCase = true) }
            if (nested != null) {
                return nested.parentFile ?: workspaceDir
            }
            return workspaceDir
        }

        fun hasWebEntrypoint(workspaceDir: File): Boolean {
            if (!workspaceDir.isDirectory) return false
            if (File(workspaceDir, "index.html").isFile) return true
            val subdirs = listOf("dist", "build", "public", "out", "www", "src")
            for (sub in subdirs) {
                val candidate = File(workspaceDir, sub)
                if (candidate.isDirectory && File(candidate, "index.html").isFile) return true
            }
            return workspaceDir.walkTopDown().maxDepth(3).any { it.isFile && it.name.endsWith(".html", ignoreCase = true) }
        }
    }

    override fun close() {
        running.set(false)
        runCatching { serverSocket?.close() }
        runCatching { executor.shutdownNow() }
        serverSocket = null
        boundPort = 0
    }
}
