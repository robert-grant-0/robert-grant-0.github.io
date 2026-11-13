package io.github.robertgrant0.bmsmonitor.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class LogLevel { INFO, WARN, ERROR }

data class LogEntry(val time: Long, val level: LogLevel, val tag: String, val message: String)

/**
 * In-memory ring buffer of what the app has been doing: connections, errors, alarms and
 * service events. Shown on the Diagnostics screen and shareable as text, so a problem in
 * the field can be explained after the event.
 */
class DiagnosticsLog(
    val capacity: Int = 2000,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val entries = ArrayDeque<LogEntry>(capacity)
    private val _revision = MutableStateFlow(0)

    /** Bumps on every change; collect it to know when to take a fresh [snapshot]. */
    val revision: StateFlow<Int> = _revision.asStateFlow()

    /** Optional extra destination for every entry, e.g. a file that outlives the process. */
    @Volatile
    var sink: ((LogEntry) -> Unit)? = null

    fun info(tag: String, message: String) = add(LogLevel.INFO, tag, message)
    fun warn(tag: String, message: String) = add(LogLevel.WARN, tag, message)
    fun error(tag: String, message: String) = add(LogLevel.ERROR, tag, message)

    fun add(level: LogLevel, tag: String, message: String) {
        val entry = LogEntry(clock(), level, tag, message)
        synchronized(this) {
            entries.addLast(entry)
            while (entries.size > capacity) entries.removeFirst()
            _revision.value = _revision.value + 1
        }
        sink?.invoke(entry)
    }

    /** Oldest first. */
    @Synchronized
    fun snapshot(): List<LogEntry> = entries.toList()

    @Synchronized
    fun clear() {
        entries.clear()
        _revision.value = _revision.value + 1
    }

    /**
     * Plain-text export: [header] lines, a blank line, then [body] if given (e.g. the on-disk
     * history), otherwise one line per in-memory entry, oldest first.
     */
    fun export(header: List<String>, body: String? = null): String {
        val sb = StringBuilder()
        for (h in header) sb.append(h).append('\n')
        sb.append('\n')
        if (body != null) sb.append(body) else for (e in snapshot()) sb.append(format(e)).append('\n')
        return sb.toString()
    }

    companion object {
        private val lineFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

        /** One export line: timestamp, level, tag, message. */
        fun format(e: LogEntry): String = synchronized(lineFormat) {
            "${lineFormat.format(Date(e.time))} ${e.level.name.padEnd(5)} ${e.tag.padEnd(10)} ${e.message}"
        }
    }
}
