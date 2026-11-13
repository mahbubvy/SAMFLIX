package com.potflix.sync

/** The two things that sync between devices. */
enum class SyncKind(val prefix: String) { History("h:"), Watchlist("w:") }

/**
 * One synced item: the latest known version of a history or watchlist entry, or a marker that
 * it was removed. [payload] is the item as JSON; [updatedAt] is when it last changed.
 */
data class SyncEntry(
    val kind: SyncKind,
    val url: String,
    val payload: String?,
    val updatedAt: Long,
    val deleted: Boolean
) {
    val key: String get() = kind.prefix + url
}

/**
 * Tracks when each history and watchlist entry last changed, and merges another device's copy.
 * For every item the most recent change wins, and removals win over older edits, so an item
 * removed on one device doesn't come back from another.
 */
class SyncLedger(entries: Collection<SyncEntry> = emptyList()) {
    private val entries = LinkedHashMap<String, SyncEntry>().apply { entries.forEach { put(it.key, it) } }

    fun all(): List<SyncEntry> = entries.values.toList()

    /**
     * Records what this device currently holds for [kind]: new or edited items get [now] as their
     * change time, and items that disappeared are marked removed. Returns true if anything changed.
     */
    fun recordLocal(kind: SyncKind, items: List<Pair<String, String>>, now: Long): Boolean {
        var changed = false
        val present = HashSet<String>()
        for ((url, payload) in items) {
            val key = kind.prefix + url
            present += key
            val known = entries[key]
            if (known == null || known.deleted || known.payload != payload) {
                entries[key] = SyncEntry(kind, url, payload, now.coerceAtLeast((known?.updatedAt ?: 0L) + 1), deleted = false)
                changed = true
            }
        }
        for ((key, entry) in entries.toList()) {
            if (entry.kind == kind && !entry.deleted && key !in present) {
                entries[key] = entry.copy(payload = null, updatedAt = now.coerceAtLeast(entry.updatedAt + 1), deleted = true)
                changed = true
            }
        }
        return changed
    }

    /** Takes in another device's entries. Returns true if any of them was newer than ours. */
    fun merge(remote: Collection<SyncEntry>): Boolean {
        var changed = false
        for (incoming in remote) {
            val known = entries[incoming.key]
            if (known == null || incoming.updatedAt > known.updatedAt) {
                entries[incoming.key] = incoming
                changed = true
            }
        }
        return changed
    }

    /** The current, not-removed items of [kind], most recently changed first. */
    fun current(kind: SyncKind): List<SyncEntry> =
        entries.values.filter { it.kind == kind && !it.deleted && it.payload != null }.sortedByDescending { it.updatedAt }

    /** Forgets removal markers older than [cutoff], so the ledger doesn't grow forever. */
    fun pruneRemovalsBefore(cutoff: Long) {
        entries.values.removeAll { it.deleted && it.updatedAt < cutoff }
    }
}
