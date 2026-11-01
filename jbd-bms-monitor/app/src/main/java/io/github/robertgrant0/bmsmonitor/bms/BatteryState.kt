package io.github.robertgrant0.bmsmonitor.bms

enum class LinkState {
    /** No BMS assigned to this slot yet. */
    NOT_CONFIGURED,

    /** Monitoring is switched off. */
    STOPPED,
    NO_PERMISSION,
    BLUETOOTH_OFF,
    CONNECTING,
    CONNECTED,

    /** Lost or failed; a reconnect is scheduled. */
    DISCONNECTED,
}

data class BatteryState(
    val slot: Int,
    val name: String,
    val address: String? = null,
    val link: LinkState = LinkState.NOT_CONFIGURED,
    val statusText: String = "",
    val basic: BasicInfo? = null,
    val cells: List<Double> = emptyList(),
    val hardwareVersion: String? = null,
    val rssi: Int? = null,
    /** Time of the last successful read, epoch ms (0 = never). */
    val lastUpdate: Long = 0L,
    /** When the link was last lost (or monitoring started), epoch ms. 0 while connected. */
    val disconnectedSince: Long = 0L,
    val reconnectCount: Int = 0,
) {
    val isConfigured: Boolean get() = address != null
    val isConnected: Boolean get() = link == LinkState.CONNECTED

    /** True when [basic] holds live data rather than the last values seen before a disconnect. */
    fun isLive(now: Long): Boolean = isConnected && basic != null && now - lastUpdate < STALE_AFTER_MS

    val cellMin: Double? get() = cells.minOrNull()
    val cellMax: Double? get() = cells.maxOrNull()
    val cellDelta: Double? get() = if (cells.size >= 2) cells.max() - cells.min() else null
    val cellAverage: Double? get() = if (cells.isEmpty()) null else cells.average()

    companion object {
        const val STALE_AFTER_MS = 20_000L
        const val SLOT_COUNT = 3
    }
}
