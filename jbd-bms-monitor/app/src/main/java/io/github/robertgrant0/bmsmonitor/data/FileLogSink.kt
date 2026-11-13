package io.github.robertgrant0.bmsmonitor.data

import java.io.File
import java.io.IOException
import java.util.concurrent.Executors

/**
 * Appends every log entry to a file so the history survives the process dying, which is
 * exactly when it's needed. Rotates at [maxBytes] (one older file is kept).
 */
class FileLogSink(private val file: File, private val maxBytes: Long = 512L * 1024) : (LogEntry) -> Unit {
    private val executor = Executors.newSingleThreadExecutor { r -> Thread(r, "diagnostics-log").apply { isDaemon = true } }
    private val older = File(file.path + ".1")

    override fun invoke(entry: LogEntry) {
        executor.execute {
            try {
                file.parentFile?.mkdirs()
                if (file.length() > maxBytes) {
                    older.delete()
                    file.renameTo(older)
                }
                file.appendText(DiagnosticsLog.format(entry) + "\n")
            } catch (_: IOException) {
            }
        }
    }

    /** Everything on disk, oldest first, after pending writes have landed. */
    fun readAll(): String {
        flush()
        return buildString {
            if (older.exists()) append(older.readText())
            if (file.exists()) append(file.readText())
        }
    }

    fun clear() {
        flush()
        older.delete()
        file.delete()
    }

    private fun flush() {
        try {
            executor.submit {}.get()
        } catch (_: Exception) {
        }
    }
}
