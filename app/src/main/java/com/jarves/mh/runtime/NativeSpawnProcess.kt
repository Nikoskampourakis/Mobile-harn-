package com.jarves.mh.runtime

import android.os.ParcelFileDescriptor
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream

internal class NativeSpawnProcess private constructor(
    private val pid: Int,
    internal val outputFile: File,
    private val stdin: OutputStream,
    private val outputPump: Thread? = null,
) : Process() {
    @Volatile private var result: Int? = null

    override fun getOutputStream(): OutputStream = stdin
    override fun getInputStream(): InputStream = FileInputStream(outputFile)
    override fun getErrorStream(): InputStream = ByteArrayInputStream(ByteArray(0))

    override fun waitFor(): Int {
        result?.let { return it }
        return NativeSpawn.waitFor(pid, false).also {
            result = it
            outputPump?.join(1_000)
        }
    }

    override fun exitValue(): Int {
        result?.let { return it }
        val status = NativeSpawn.waitFor(pid, true)
        if (status == NativeSpawn.STILL_RUNNING) throw IllegalThreadStateException("Process is still running")
        return status.also { result = it }
    }

    override fun destroy() {
        NativeSpawn.kill(pid, 15)
    }

    /** Send the same interrupt signal produced by Ctrl+C in a real terminal. */
    internal fun interrupt() {
        NativeSpawn.kill(pid, 2)
    }

    override fun destroyForcibly(): Process {
        NativeSpawn.kill(pid, 9)
        return this
    }

    override fun isAlive(): Boolean = runCatching { exitValue(); false }.getOrDefault(true)

    companion object {
        fun start(
            argv: List<String>,
            environment: Map<String, String>,
            cwd: String,
            outputFile: File,
            pseudoTerminal: Boolean = false,
            ptyRows: Int = 40,
            ptyColumns: Int = 120,
        ): NativeSpawnProcess {
            outputFile.parentFile?.mkdirs()
            if (pseudoTerminal) outputFile.delete()
            val spawned = NativeSpawn.spawn(
                argv.toTypedArray(),
                environment.map { "${it.key}=${it.value}" }.toTypedArray(),
                cwd,
                outputFile.absolutePath,
                pseudoTerminal,
                ptyRows,
                ptyColumns,
            )
            check(spawned.size == 3 && spawned[0] > 0) { "Native runtime launch failed" }
            val input = ParcelFileDescriptor.AutoCloseOutputStream(ParcelFileDescriptor.adoptFd(spawned[1]))
            val pump = spawned[2].takeIf { it >= 0 }?.let { outputFd ->
                Thread({
                    runCatching {
                        ParcelFileDescriptor.AutoCloseInputStream(ParcelFileDescriptor.adoptFd(outputFd)).use { source ->
                            FileOutputStream(outputFile, false).use { destination -> source.copyTo(destination) }
                        }
                    }
                }, "pocket-pty-output").apply {
                    isDaemon = true
                    start()
                }
            }
            return NativeSpawnProcess(spawned[0], outputFile, input, pump)
        }
    }
}

internal object NativeSpawn {
    const val STILL_RUNNING = -2
    var isLoaded = false

    fun loadLibrary(context: android.content.Context) {
        if (isLoaded) return
        runCatching {
            System.loadLibrary("pocketspawn")
            isLoaded = true
        }.onFailure { error ->
            val fallback = File(context.filesDir, "native-bin/libpocketspawn.so")
            if (fallback.exists()) {
                runCatching {
                    System.load(fallback.absolutePath)
                    isLoaded = true
                }
            }
            if (!isLoaded) {
                android.util.Log.e("PocketSpawn", "Failed to load pocketspawn library", error)
            }
        }
    }

    init {
        // Try default load on first access if possible, but loadLibrary(context) is preferred.
        runCatching {
            System.loadLibrary("pocketspawn")
            isLoaded = true
        }
    }

    fun spawn(
        argv: Array<String>,
        environment: Array<String>,
        cwd: String,
        outputFile: String,
        pseudoTerminal: Boolean,
        ptyRows: Int,
        ptyColumns: Int,
    ): IntArray {
        if (!isLoaded) return intArrayOf(-1, -1, -1)
        return runCatching {
            spawnNative(argv, environment, cwd, outputFile, pseudoTerminal, ptyRows, ptyColumns)
        }.getOrDefault(intArrayOf(-1, -1, -1))
    }

    fun waitFor(pid: Int, noHang: Boolean): Int {
        if (!isLoaded || pid <= 0) return -1
        return runCatching { waitForNative(pid, noHang) }.getOrDefault(-1)
    }

    fun kill(pid: Int, signal: Int): Int {
        if (!isLoaded || pid <= 0) return -1
        return runCatching { killNative(pid, signal) }.getOrDefault(-1)
    }

    @JvmStatic
    private external fun spawnNative(
        argv: Array<String>,
        environment: Array<String>,
        cwd: String,
        outputFile: String,
        pseudoTerminal: Boolean,
        ptyRows: Int,
        ptyColumns: Int,
    ): IntArray

    @JvmStatic
    private external fun waitForNative(pid: Int, noHang: Boolean): Int

    @JvmStatic
    private external fun killNative(pid: Int, signal: Int): Int
}
