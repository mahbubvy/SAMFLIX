package com.potflix.sync

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.annotation.Keep
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.potflix.data.local.entity.LocalMovieEntity
import com.potflix.data.repository.WatchlistRepository
import com.potflix.domain.model.Movie
import com.potflix.domain.repository.MovieRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/** What the Settings screen shows about sync. */
data class SyncStatus(
    val enabled: Boolean = false,
    val code: String? = null,
    val nearby: List<String> = emptyList(),
    val lastSyncAt: Long? = null,
    val lastSyncWith: String? = null,
    val problem: String? = null
)

/** One message between devices: who sent it and their whole ledger. */
@Keep
private data class SyncMessage(val version: Int, val deviceId: String, val deviceName: String, val entries: List<SyncEntry>)

private data class Peer(val serviceName: String, val name: String, val host: java.net.InetAddress, val port: Int)

/**
 * Syncs the watchlist and watch history between a user's devices over the home Wi-Fi, with no
 * cloud in between. Devices that share a sync code find each other with network service
 * discovery and swap encrypted copies of their ledger; each side keeps the newest change.
 * It runs while the app is open (see [start] and [stop]).
 */
@Singleton
class LibrarySync @Inject constructor(
    @ApplicationContext private val context: Context,
    private val movieRepository: MovieRepository,
    private val watchlistRepository: WatchlistRepository
) {
    private val prefs = context.getSharedPreferences("library_sync", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val nsd by lazy { context.getSystemService(Context.NSD_SERVICE) as NsdManager }

    private val _status = MutableStateFlow(SyncStatus(enabled = prefs.getBoolean(KEY_ENABLED, false), code = prefs.getString(KEY_CODE, null)))
    val status: StateFlow<SyncStatus> = _status.asStateFlow()

    private val deviceId: String = prefs.getString(KEY_DEVICE_ID, null)
        ?: UUID.randomUUID().toString().replace("-", "").take(12).also { prefs.edit().putString(KEY_DEVICE_ID, it).apply() }
    private val ownServiceName = "$SERVICE_PREFIX$deviceId"
    private val deviceName: String by lazy {
        Settings.Global.getString(context.contentResolver, "device_name")?.takeIf { it.isNotBlank() } ?: Build.MODEL
    }

    private val ledgerLock = Mutex()
    private val ledger: SyncLedger by lazy { SyncLedger(loadLedger()) }

    private var scope: CoroutineScope? = null
    private var server: ServerSocket? = null
    private var registration: NsdManager.RegistrationListener? = null
    private var discovery: NsdManager.DiscoveryListener? = null
    private val peers = ConcurrentHashMap<String, Peer>()
    private val resolveLock = Mutex()
    private val pushRequests = Channel<Unit>(Channel.CONFLATED)

    // ---- Settings actions ----------------------------------------------------------------

    /** Turns sync on, creating this device's code if it has none yet. */
    fun enable() {
        val code = prefs.getString(KEY_CODE, null) ?: SyncCrypto.newCode()
        prefs.edit().putBoolean(KEY_ENABLED, true).putString(KEY_CODE, code).apply()
        _status.update { it.copy(enabled = true, code = code, problem = null) }
        restart()
    }

    fun disable() {
        prefs.edit().putBoolean(KEY_ENABLED, false).apply()
        stop()
        _status.update { it.copy(enabled = false, nearby = emptyList()) }
    }

    /** Joins the devices that use [typedCode]. Returns false if it isn't a valid code. */
    fun join(typedCode: String): Boolean {
        val code = SyncCrypto.normalize(typedCode) ?: return false
        prefs.edit().putBoolean(KEY_ENABLED, true).putString(KEY_CODE, code).apply()
        _status.update { it.copy(enabled = true, code = code, problem = null) }
        restart()
        return true
    }

    /** Gives this device a fresh code, which unpairs it from every other device. */
    fun newCode() {
        val code = SyncCrypto.newCode()
        prefs.edit().putString(KEY_CODE, code).apply()
        _status.update { it.copy(code = code) }
        restart()
    }

    /** Syncs with every device found nearby right now. */
    fun syncNow() {
        scope?.launch { peers.values.forEach { exchangeWith(it) } }
    }

    // ---- Lifecycle -------------------------------------------------------------------------

    @Synchronized
    fun start() {
        if (scope != null || !prefs.getBoolean(KEY_ENABLED, false)) return
        val code = prefs.getString(KEY_CODE, null) ?: return
        val key = SyncCrypto.keyFor(code)
        val newScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope = newScope
        newScope.launch {
            ledgerLock.withLock { ledger.pruneRemovalsBefore(System.currentTimeMillis() - REMOVAL_MEMORY_MS); saveLedger() }
            runCatching { serve(key, newScope) }
                .onFailure { report("Couldn't start sync: ${it.message}") }
        }
        newScope.launch { watchLocalChanges() }
        newScope.launch {
            for (request in pushRequests) {
                delay(PUSH_DEBOUNCE_MS)
                peers.values.forEach { exchangeWith(it) }
            }
        }
    }

    @Synchronized
    fun stop() {
        registration?.let { runCatching { nsd.unregisterService(it) } }
        discovery?.let { runCatching { nsd.stopServiceDiscovery(it) } }
        registration = null
        discovery = null
        runCatching { server?.close() }
        server = null
        scope?.cancel()
        scope = null
        peers.clear()
        _status.update { it.copy(nearby = emptyList()) }
    }

    private fun restart() {
        stop()
        start()
    }

    // ---- Local changes ---------------------------------------------------------------------

    private suspend fun watchLocalChanges() {
        combine(movieRepository.getWatchHistoryFlow(), watchlistRepository.getWatchlist()) { history, watchlist ->
            history to watchlist
        }.collect { (history, watchlist) ->
            val changed = ledgerLock.withLock {
                val now = System.currentTimeMillis()
                val a = ledger.recordLocal(SyncKind.History, history.map { it.url to gson.toJson(it) }, now)
                val b = ledger.recordLocal(SyncKind.Watchlist, watchlist.map { it.url to gson.toJson(it) }, now)
                if (a || b) saveLedger()
                a || b
            }
            if (changed) pushRequests.trySend(Unit)
        }
    }

    /** Writes the ledger's current items into the app's history and watchlist. */
    private suspend fun applyLedger() {
        val (history, watchlist) = ledgerLock.withLock {
            ledger.current(SyncKind.History).mapNotNull { e -> runCatching { gson.fromJson(e.payload, Movie::class.java) }.getOrNull() } to
                ledger.current(SyncKind.Watchlist).mapNotNull { e -> runCatching { gson.fromJson(e.payload, LocalMovieEntity::class.java) }.getOrNull() }
        }
        movieRepository.replaceWatchHistory(history)
        val present = watchlistRepository.getWatchlist().first()
        val wanted = watchlist.associateBy { it.url }
        present.filter { it.url !in wanted }.forEach { watchlistRepository.removeByUrl(it.url) }
        val presentByUrl = present.associateBy { it.url }
        watchlist.filter { presentByUrl[it.url] != it }.forEach { watchlistRepository.addToWatchlist(it) }
    }

    // ---- Network ---------------------------------------------------------------------------

    private suspend fun serve(key: ByteArray, scope: CoroutineScope) {
        val socket = ServerSocket(0)
        server = socket
        register(socket.localPort)
        discover(scope)
        while (!socket.isClosed) {
            val client = runCatching { socket.accept() }.getOrNull() ?: break
            scope.launch { client.use { answer(it, key) } }
        }
    }

    /** Another device connected: take its ledger, merge, and send ours back. */
    private suspend fun answer(socket: Socket, key: ByteArray) {
        socket.soTimeout = SOCKET_TIMEOUT_MS
        val incoming = readMessage(socket, key) ?: return // wrong code or garbage: ignore
        val merged = ledgerLock.withLock { ledger.merge(incoming.entries).also { if (it) saveLedger() } }
        if (merged) applyLedger()
        writeMessage(socket, key, ledgerLock.withLock { ledger.all() })
        markSynced(incoming.deviceName)
    }

    /** Sends our ledger to [peer] and merges what comes back. */
    private suspend fun exchangeWith(peer: Peer) {
        val code = prefs.getString(KEY_CODE, null) ?: return
        val key = SyncCrypto.keyFor(code)
        runCatching {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(peer.host, peer.port), SOCKET_TIMEOUT_MS)
                socket.soTimeout = SOCKET_TIMEOUT_MS
                writeMessage(socket, key, ledgerLock.withLock { ledger.all() })
                val reply = readMessage(socket, key) ?: return@use
                val merged = ledgerLock.withLock { ledger.merge(reply.entries).also { if (it) saveLedger() } }
                if (merged) applyLedger()
                markSynced(reply.deviceName)
            }
        }.onFailure { Log.d(TAG, "Sync with ${peer.name} failed: ${it.message}") }
    }

    private fun writeMessage(socket: Socket, key: ByteArray, entries: List<SyncEntry>) {
        val json = gson.toJson(SyncMessage(PROTOCOL_VERSION, deviceId, deviceName, entries))
        val sealed = SyncCrypto.seal(key, json.toByteArray(Charsets.UTF_8))
        DataOutputStream(socket.getOutputStream()).apply { writeInt(sealed.size); write(sealed); flush() }
    }

    private fun readMessage(socket: Socket, key: ByteArray): SyncMessage? {
        val input = DataInputStream(socket.getInputStream())
        val size = input.readInt()
        if (size <= 0 || size > MAX_MESSAGE_BYTES) return null
        val sealed = ByteArray(size).also { input.readFully(it) }
        val plain = SyncCrypto.open(key, sealed) ?: return null
        return runCatching { gson.fromJson(plain.toString(Charsets.UTF_8), SyncMessage::class.java) }.getOrNull()
            ?.takeIf { it.version == PROTOCOL_VERSION && it.deviceId != deviceId }
    }

    private fun register(port: Int) {
        val info = NsdServiceInfo().apply {
            serviceName = ownServiceName
            serviceType = SERVICE_TYPE
            setPort(port)
            setAttribute("name", deviceName)
        }
        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) = Unit
            override fun onRegistrationFailed(info: NsdServiceInfo, code: Int) = report("Couldn't announce this device ($code)")
            override fun onServiceUnregistered(info: NsdServiceInfo) = Unit
            override fun onUnregistrationFailed(info: NsdServiceInfo, code: Int) = Unit
        }
        registration = listener
        nsd.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener)
    }

    private fun discover(scope: CoroutineScope) {
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(type: String) = Unit
            override fun onDiscoveryStopped(type: String) = Unit
            override fun onStartDiscoveryFailed(type: String, code: Int) = report("Couldn't look for devices ($code)")
            override fun onStopDiscoveryFailed(type: String, code: Int) = Unit
            override fun onServiceFound(info: NsdServiceInfo) {
                if (!info.serviceName.startsWith(SERVICE_PREFIX) || info.serviceName == ownServiceName) return
                scope.launch {
                    val peer = resolve(info) ?: return@launch
                    peers[peer.serviceName] = peer
                    publishNearby()
                    exchangeWith(peer)
                }
            }
            override fun onServiceLost(info: NsdServiceInfo) {
                peers.remove(info.serviceName)
                publishNearby()
            }
        }
        discovery = listener
        nsd.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
    }

    /** Older Android resolves one service at a time, so resolves queue up. */
    @Suppress("DEPRECATION")
    private suspend fun resolve(info: NsdServiceInfo): Peer? = resolveLock.withLock {
        suspendCancellableCoroutine { cont ->
            nsd.resolveService(info, object : NsdManager.ResolveListener {
                override fun onResolveFailed(info: NsdServiceInfo, code: Int) {
                    if (cont.isActive) cont.resume(null)
                }
                override fun onServiceResolved(resolved: NsdServiceInfo) {
                    val host = resolved.host
                    val name = resolved.attributes["name"]?.toString(Charsets.UTF_8) ?: resolved.serviceName
                    if (cont.isActive) cont.resume(host?.let { Peer(resolved.serviceName, name, it, resolved.port) })
                }
            })
        }
    }

    // ---- Status and storage ----------------------------------------------------------------

    private fun publishNearby() = _status.update { status -> status.copy(nearby = peers.values.map { it.name }.sorted()) }

    private fun markSynced(withName: String) =
        _status.update { it.copy(lastSyncAt = System.currentTimeMillis(), lastSyncWith = withName, problem = null) }

    private fun report(problem: String) {
        Log.w(TAG, problem)
        _status.update { it.copy(problem = problem) }
    }

    private fun loadLedger(): List<SyncEntry> {
        val json = prefs.getString(KEY_LEDGER, null) ?: return emptyList()
        return runCatching { gson.fromJson<List<SyncEntry>>(json, object : TypeToken<List<SyncEntry>>() {}.type) }
            .getOrNull().orEmpty()
    }

    /** Call while holding [ledgerLock]. */
    private fun saveLedger() {
        prefs.edit().putString(KEY_LEDGER, gson.toJson(ledger.all())).apply()
    }

    private companion object {
        const val TAG = "LibrarySync"
        const val SERVICE_TYPE = "_samflix._tcp."
        const val SERVICE_PREFIX = "SAMFLIX-"
        const val PROTOCOL_VERSION = 1
        const val SOCKET_TIMEOUT_MS = 8_000
        const val MAX_MESSAGE_BYTES = 8 * 1024 * 1024
        const val PUSH_DEBOUNCE_MS = 2_000L
        const val REMOVAL_MEMORY_MS = 90L * 24 * 60 * 60 * 1000
        const val KEY_ENABLED = "enabled"
        const val KEY_CODE = "code"
        const val KEY_DEVICE_ID = "device_id"
        const val KEY_LEDGER = "ledger"
    }
}

