package io.github.robertgrant0.bmsmonitor.bms

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Decides when the siren sounds. A siren-worthy alarm latches the siren on, and it keeps
 * sounding (even if the condition clears) until someone silences it. Silencing covers
 * every alarm active at that moment; an alarm that is new, or that clears and later
 * comes back, sounds the siren again.
 */
class SirenLatch {
    private val acknowledged = HashSet<String>()
    private var lastActive: Map<String, String> = emptyMap()
    private val latched = LinkedHashMap<String, String>()

    private val _ringing = MutableStateFlow<List<String>>(emptyList())

    /** Messages of the alarms that set the siren off; empty when the siren is quiet. */
    val ringing: StateFlow<List<String>> = _ringing.asStateFlow()

    /** [active]: stable alarm id → message, for every siren-worthy alarm active right now. */
    @Synchronized
    fun update(active: Map<String, String>) {
        lastActive = active
        acknowledged.retainAll(active.keys)
        for ((key, message) in active) {
            if (key !in acknowledged) latched[key] = message
        }
        publish()
    }

    /** Silence the siren. */
    @Synchronized
    fun silence() {
        acknowledged += lastActive.keys
        latched.clear()
        publish()
    }

    /** Sound the siren once, for the "Test siren" button. */
    @Synchronized
    fun test() {
        latched[TEST_KEY] = "Test alarm – press Silence to stop"
        publish()
    }

    private fun publish() {
        _ringing.value = latched.values.toList()
    }

    companion object {
        private const val TEST_KEY = "test"

        fun isSirenWorthy(alarm: Alarm, includeWarnings: Boolean): Boolean =
            alarm.severity == Severity.ALARM || includeWarnings
    }
}
