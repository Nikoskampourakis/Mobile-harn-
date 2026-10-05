package com.jarves.mh.network

import android.content.Context
import android.net.wifi.WifiManager
import android.text.format.Formatter
import android.util.Log
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.json.JSONArray
import org.json.JSONObject

data class ShareServerState(
    val isRunning: Boolean = false,
    val localPort: Int = 0,
    val localUrl: String = "",
    val publicUrl: String = "",
    val passCode: String = "",
    val activeVisitors: Int = 0,
    val projectName: String = "",
)

class ProjectShareServer(
    private val context: Context,
    private val projectWorkspaceDir: File,
    private val projectName: String,
    private val onStateChanged: (ShareServerState) -> Unit = {},
) : AutoCloseable {

    private val isRunning = AtomicBoolean(false)
    private var serverSocket: ServerSocket? = null
    private var serverThread: Thread? = null
    private var passCode: String = generateRandomCode()
    private val authorizedSessions = ConcurrentHashMap.newKeySet<String>()
    private var activePort: Int = 0

    init {
        notifyState()
    }

    fun start(customCode: String? = null): ShareServerState {
        if (isRunning.get()) return currentState()
        customCode?.takeIf { it.isNotBlank() }?.let { passCode = it.trim().uppercase() }
            ?: run { passCode = generateRandomCode() }

        runCatching {
            val socket = ServerSocket(0)
            serverSocket = socket
            activePort = socket.localPort
            isRunning.set(true)

            serverThread = Thread({
                while (isRunning.get() && !socket.isClosed) {
                    runCatching {
                        val client = socket.accept()
                        Thread({ handleClient(client) }, "share-client-${client.port}").apply {
                            isDaemon = true
                            start()
                        }
                    }
                }
            }, "mh-share-server").apply {
                isDaemon = true
                start()
            }
            notifyState()
        }.onFailure {
            Log.e("ShareServer", "Failed to start server", it)
            isRunning.set(false)
            notifyState()
        }

        return currentState()
    }

    fun stop() {
        isRunning.set(false)
        runCatching { serverSocket?.close() }
        serverSocket = null
        serverThread = null
        authorizedSessions.clear()
        notifyState()
    }

    fun regenerateCode(): String {
        passCode = generateRandomCode()
        authorizedSessions.clear()
        notifyState()
        return passCode
    }

    fun currentState(): ShareServerState {
        val ip = getDeviceIpAddress()
        val localUrl = if (activePort > 0 && ip.isNotBlank()) "http://$ip:$activePort" else ""
        return ShareServerState(
            isRunning = isRunning.get(),
            localPort = activePort,
            localUrl = localUrl,
            publicUrl = localUrl,
            passCode = passCode,
            activeVisitors = authorizedSessions.size,
            projectName = projectName,
        )
    }

    private fun notifyState() {
        onStateChanged(currentState())
    }

    private fun handleClient(socket: Socket) {
        socket.use { s ->
            val input = BufferedInputStream(s.getInputStream())
            val output = BufferedOutputStream(s.getOutputStream())

            val requestLine = readLine(input) ?: return
            val parts = requestLine.split(" ")
            if (parts.size < 2) return
            val method = parts[0]
            val fullPath = parts[1]
            val path = fullPath.substringBefore("?")
            val query = fullPath.substringAfter("?", "")

            val headers = mutableMapOf<String, String>()
            while (true) {
                val line = readLine(input) ?: break
                if (line.isEmpty()) break
                val split = line.indexOf(':')
                if (split > 0) {
                    headers[line.substring(0, split).trim().lowercase()] = line.substring(split + 1).trim()
                }
            }

            val cookieHeader = headers["cookie"].orEmpty()
            val sessionCookie = cookieHeader.split(";")
                .map(String::trim)
                .firstOrNull { it.startsWith("mh_session=") }
                ?.substringAfter("mh_session=")

            val isAuthorized = sessionCookie != null && authorizedSessions.contains(sessionCookie)

            if (method == "POST" && path == "/api/login") {
                val contentLength = headers["content-length"]?.toIntOrNull() ?: 0
                val bodyBytes = ByteArray(contentLength)
                var read = 0
                while (read < contentLength) {
                    val count = input.read(bodyBytes, read, contentLength - read)
                    if (count < 0) break
                    read += count
                }
                val enteredCode = runCatching {
                    JSONObject(bodyBytes.decodeToString()).optString("code")
                }.getOrDefault("").trim()

                if (enteredCode.equals(passCode, ignoreCase = true)) {
                    val newSession = java.util.UUID.randomUUID().toString()
                    authorizedSessions.add(newSession)
                    notifyState()
                    val response = JSONObject().put("success", true).toString()
                    writeResponse(output, 200, "application/json", response.toByteArray(), listOf("Set-Cookie: mh_session=$newSession; Path=/; HttpOnly"))
                } else {
                    val response = JSONObject().put("success", false).put("error", "Invalid pass code").toString()
                    writeResponse(output, 401, "application/json", response.toByteArray())
                }
                return
            }

            if (!isAuthorized) {
                serveLoginPage(output)
                return
            }

            when {
                path == "/" || path == "/index.html" -> serveDashboard(output)
                path == "/api/files" -> serveFileList(output)
                path == "/api/file" -> {
                    val fileParam = query.split("&").firstOrNull { it.startsWith("path=") }?.substringAfter("path=").orEmpty()
                    serveFileContent(output, java.net.URLDecoder.decode(fileParam, "UTF-8"))
                }
                path == "/api/download-zip" -> serveZipDownload(output)
                else -> writeResponse(output, 404, "text/plain", "Not Found".toByteArray())
            }
        }
    }

    private fun serveLoginPage(output: BufferedOutputStream) {
        val html = """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Access $projectName · PocketDev</title>
                <style>
                    body { font-family: system-ui, -apple-system, sans-serif; background: #0f172a; color: #f8fafc; display: flex; align-items: center; justify-content: center; min-height: 100vh; margin: 0; padding: 16px; }
                    .card { background: #1e293b; border: 1px solid #334155; border-radius: 16px; padding: 32px; max-width: 400px; width: 100%; box-shadow: 0 10px 25px rgba(0,0,0,0.5); text-align: center; }
                    h2 { margin-top: 0; color: #38bdf8; }
                    p { color: #94a3b8; font-size: 14px; margin-bottom: 24px; }
                    input { width: 100%; box-sizing: border-box; padding: 12px 16px; font-size: 20px; text-transform: uppercase; letter-spacing: 4px; text-align: center; background: #0f172a; border: 1px solid #475569; border-radius: 8px; color: #f8fafc; margin-bottom: 16px; }
                    input:focus { border-color: #38bdf8; outline: none; }
                    button { width: 100%; padding: 12px; background: #38bdf8; color: #0f172a; font-weight: bold; border: none; border-radius: 8px; font-size: 16px; cursor: pointer; transition: 0.2s; }
                    button:hover { background: #0ea5e9; }
                    .error { color: #f87171; font-size: 13px; margin-top: 12px; display: none; }
                    .badge { display: inline-block; background: #0369a1; color: #e0f2fe; padding: 4px 8px; border-radius: 6px; font-size: 12px; font-weight: 600; margin-bottom: 12px; }
                </style>
            </head>
            <body>
                <div class="card">
                    <span class="badge">SECURE HOSTING</span>
                    <h2>$projectName</h2>
                    <p>Enter the 6-character shared pass code shown on the host device to view this project.</p>
                    <form onsubmit="submitCode(event)">
                        <input type="text" id="code" maxlength="8" placeholder="PASS CODE" required autofocus />
                        <button type="submit" id="btn">Unlock Project</button>
                        <div class="error" id="err">Incorrect pass code. Please try again.</div>
                    </form>
                </div>
                <script>
                    async function submitCode(e) {
                        e.preventDefault();
                        const code = document.getElementById('code').value.trim();
                        const err = document.getElementById('err');
                        const btn = document.getElementById('btn');
                        btn.disabled = true;
                        err.style.display = 'none';
                        try {
                            const res = await fetch('/api/login', {
                                method: 'POST',
                                headers: { 'Content-Type': 'application/json' },
                                body: JSON.stringify({ code })
                            });
                            const data = await res.json();
                            if (data.success) {
                                location.reload();
                            } else {
                                err.style.display = 'block';
                            }
                        } catch(e) {
                            err.innerText = 'Connection error';
                            err.style.display = 'block';
                        } finally {
                            btn.disabled = false;
                        }
                    }
                </script>
            </body>
            </html>
        """.trimIndent()
        writeResponse(output, 200, "text/html; charset=utf-8", html.toByteArray())
    }

    private fun serveDashboard(output: BufferedOutputStream) {
        val html = """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>$projectName · PocketDev</title>
                <style>
                    * { box-sizing: border-box; }
                    body { font-family: system-ui, -apple-system, sans-serif; background: #090d16; color: #f1f5f9; margin: 0; padding: 0; height: 100vh; display: flex; flex-direction: column; }
                    header { background: #0f172a; border-bottom: 1px solid #1e293b; padding: 12px 20px; display: flex; justify-content: space-between; align-items: center; }
                    .brand { display: flex; align-items: center; gap: 12px; }
                    .brand h1 { font-size: 18px; margin: 0; color: #38bdf8; }
                    .brand span { font-size: 12px; color: #22c55e; background: #14532d; padding: 2px 8px; border-radius: 12px; font-weight: bold; }
                    .actions a { background: #334155; color: #f8fafc; padding: 8px 14px; text-decoration: none; border-radius: 6px; font-size: 13px; font-weight: 500; transition: 0.2s; }
                    .actions a:hover { background: #475569; }
                    main { display: flex; flex: 1; overflow: hidden; }
                    #sidebar { width: 300px; background: #0f172a; border-right: 1px solid #1e293b; overflow-y: auto; padding: 12px; }
                    #viewer { flex: 1; background: #090d16; overflow: auto; padding: 20px; }
                    .file-item { padding: 8px 12px; border-radius: 6px; cursor: pointer; color: #94a3b8; font-size: 13px; font-family: monospace; display: flex; align-items: center; gap: 8px; margin-bottom: 2px; }
                    .file-item:hover { background: #1e293b; color: #f8fafc; }
                    .file-item.active { background: #0284c7; color: white; }
                    pre { margin: 0; background: #0f172a; padding: 16px; border-radius: 8px; border: 1px solid #1e293b; font-family: 'JetBrains Mono', Consolas, monospace; font-size: 13px; overflow: auto; line-height: 1.5; color: #e2e8f0; }
                    .banner { background: #1e293b; border-radius: 8px; padding: 16px; margin-bottom: 16px; border: 1px solid #334155; }
                </style>
            </head>
            <body>
                <header>
                    <div class="brand">
                        <h1>$projectName</h1>
                        <span>LIVE ON DEVICE</span>
                    </div>
                    <div class="actions">
                        <a href="/api/download-zip" download="$projectName.zip">⬇ Download ZIP</a>
                    </div>
                </header>
                <main>
                    <div id="sidebar">
                        <div style="font-size: 11px; text-transform: uppercase; color: #64748b; font-weight: bold; margin-bottom: 8px; padding-left: 8px;">Workspace Files</div>
                        <div id="fileList">Loading files…</div>
                    </div>
                    <div id="viewer">
                        <div class="banner">
                            <h3 style="margin-top:0;">Shared On-Device Workspace</h3>
                            <p style="color:#94a3b8; font-size:13px; margin-bottom:0;">You are viewing a project hosted directly from an Android phone. Any changes made by the AI agent will appear in real time.</p>
                        </div>
                        <pre id="codeView">// Select a file from the sidebar to inspect its content.</pre>
                    </div>
                </main>
                <script>
                    let currentPath = '';
                    async function loadFiles() {
                        const res = await fetch('/api/files');
                        const files = await res.json();
                        const list = document.getElementById('fileList');
                        list.innerHTML = '';
                        files.forEach(f => {
                            const item = document.createElement('div');
                            item.className = 'file-item';
                            item.innerText = f.path;
                            item.onclick = () => openFile(f.path, item);
                            list.appendChild(item);
                        });
                        if (files.length > 0) openFile(files[0].path, list.firstChild);
                    }
                    async function openFile(path, el) {
                        currentPath = path;
                        document.querySelectorAll('.file-item').forEach(i => i.classList.remove('active'));
                        if (el) el.classList.add('active');
                        const view = document.getElementById('codeView');
                        view.innerText = 'Loading ' + path + '…';
                        try {
                            const res = await fetch('/api/file?path=' + encodeURIComponent(path));
                            const text = await res.text();
                            view.innerText = text;
                        } catch(e) {
                            view.innerText = 'Could not load file';
                        }
                    }
                    loadFiles();
                </script>
            </body>
            </html>
        """.trimIndent()
        writeResponse(output, 200, "text/html; charset=utf-8", html.toByteArray())
    }

    private fun serveFileList(output: BufferedOutputStream) {
        val files = mutableListOf<JSONObject>()
        val base = projectWorkspaceDir.canonicalFile
        base.walkTopDown()
            .filter { it.isFile && !it.name.startsWith(".") }
            .forEach { file ->
                val relative = file.relativeTo(base).invariantSeparatorsPath
                if (!relative.contains("/.") && !relative.startsWith(".")) {
                    files.add(JSONObject().put("path", relative).put("size", file.length()))
                }
            }
        val json = JSONArray(files).toString()
        writeResponse(output, 200, "application/json", json.toByteArray())
    }

    private fun serveFileContent(output: BufferedOutputStream, relativePath: String) {
        val safeFile = File(projectWorkspaceDir, relativePath).canonicalFile
        if (!safeFile.toPath().startsWith(projectWorkspaceDir.canonicalFile.toPath()) || !safeFile.isFile) {
            writeResponse(output, 404, "text/plain", "File not found".toByteArray())
            return
        }
        val bytes = safeFile.readBytes()
        writeResponse(output, 200, "text/plain; charset=utf-8", bytes)
    }

    private fun serveZipDownload(output: BufferedOutputStream) {
        val tempZip = File(context.cacheDir, "project-share-${System.currentTimeMillis()}.zip")
        try {
            ZipOutputStream(tempZip.outputStream()).use { zip ->
                val base = projectWorkspaceDir.canonicalFile
                base.walkTopDown()
                    .filter { it.isFile && !it.name.startsWith(".") }
                    .forEach { file ->
                        val relative = file.relativeTo(base).invariantSeparatorsPath
                        if (!relative.contains("/.") && !relative.startsWith(".")) {
                            zip.putNextEntry(ZipEntry(relative))
                            FileInputStream(file).use { it.copyTo(zip) }
                            zip.closeEntry()
                        }
                    }
            }
            val bytes = tempZip.readBytes()
            writeResponse(
                output,
                200,
                "application/zip",
                bytes,
                listOf("Content-Disposition: attachment; filename=\"${projectName.replace(' ', '_')}.zip\""),
            )
        } finally {
            tempZip.delete()
        }
    }

    private fun writeResponse(
        output: BufferedOutputStream,
        code: Int,
        contentType: String,
        body: ByteArray,
        extraHeaders: List<String> = emptyList(),
    ) {
        val statusText = if (code == 200) "OK" else if (code == 404) "Not Found" else if (code == 401) "Unauthorized" else "Error"
        val header = buildString {
            append("HTTP/1.1 $code $statusText\r\n")
            append("Content-Type: $contentType\r\n")
            append("Content-Length: ${body.size}\r\n")
            append("Connection: close\r\n")
            extraHeaders.forEach { append("$it\r\n") }
            append("\r\n")
        }
        output.write(header.toByteArray())
        output.write(body)
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

    private fun getDeviceIpAddress(): String {
        return runCatching {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (iface.isLoopback || !iface.isUp) continue
                val addresses = iface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        return addr.hostAddress
                    }
                }
            }
            ""
        }.getOrDefault("")
    }

    private fun generateRandomCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val random = SecureRandom()
        return (1..6).map { chars[random.nextInt(chars.length)] }.joinToString("")
    }

    override fun close() {
        stop()
    }
}
