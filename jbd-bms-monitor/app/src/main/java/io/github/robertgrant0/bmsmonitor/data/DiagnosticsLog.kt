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

    fun info(tag: String, message: String) = add(LogLevel.INFO, tag, message)
    fun warn(tag: String, message: String) = add(LogLevel.WARN, tag, message)
    fun error(tag: String, message: String) = add(LogLevel.ERROR, tag, message)

    @Synchronized
    fun add(level: LogLevel, tag: String, message: String) {
        entries.addLast(LogEntry(clock(), level, tag, message))
        while (entries.size > capacity) entries.removeFirst()
        _revision.value = _revision.value + 1
    }

    /** Oldest first. */
    @Synchronized
    fun snapshot(): List<LogEntry> = entries.toList()

    @Synchronized
    fun clear() {
        entries.clear()
        _revision.value = _revision.value + 1
    }

    /** Plain-text export: [header] lines, a blank line, then one line per entry, oldest first. */
    fun export(header: List<String>): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
        val sb = StringBuilder()
        for (h in header) sb.append(h).append('\n')
        sb.append('\n')
        for (e in snapshot()) {
            sb.append(fmt.format(Date(e.time))).append(' ')
                .append(e.level.name.padEnd(5)).append(' ')
                .append(e.tag.padEnd(10)).append(' ')
                .append(e.message).append('\n')
        }
        return sb.toString()
    }
}
