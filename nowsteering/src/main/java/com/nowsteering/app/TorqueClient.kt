package com.nowsteering.app

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import org.prowl.torque.remote.ITorqueService

/**
 * Phase 2b probe: reads (never writes) Torque Pro's plugin service. Diagnostics only; nothing here touches the car screen.
 *
 * Read-only calls used: getVersion, hasFullPermissions, isConnectedToECU, listAllPIDs, listActivePIDs,
 * listECUSupportedPIDs, getPIDInformation, getPIDValuesAsDouble, getPIDUpdateTime.
 * Never used: sendCommandGetResponse, requestExclusiveLock, sendPIDData*, setPIDData, setDebugTestMode,
 * storeInProfile, anything that writes or changes how Torque talks to the adapter.
 *
 * Binding uses flags = 0 (no BIND_AUTO_CREATE) so we never start Torque ourselves: if Torque is not running we
 * wait, and connect when it starts (its APP_LAUNCHED broadcast also triggers a fresh bind attempt).
 */
class TorqueClient(private val ctx: Context) {
    enum class Install { NONE, PRO, LITE }

    /** What Diagnostics shows. Written on the poll thread, read on the main thread. */
    data class Snapshot(
        val install: Install = Install.NONE,
        val bound: Boolean = false,
        val version: String = "—",
        val fullPermissions: String = "—",
        val obdConnected: Boolean? = null,
        val note: String = "",
        val pids: List<TorquePid> = emptyList(),
        val rows: Map<String, Pair<Double, Long>> = emptyMap(),
        val allCount: Int = 0,
    )

    companion object {
        const val PRO = "org.prowl.torque"
        const val LITE = "org.prowl.torquefree"
        const val SERVICE_CLASS = "org.prowl.torque.remote.TorqueService"
        const val ACTION_CONNECTED = "org.prowl.torque.OBD_CONNECTED"
        const val ACTION_DISCONNECTED = "org.prowl.torque.OBD_DISCONNECTED"
        const val ACTION_LAUNCHED = "org.prowl.torque.APP_LAUNCHED"
        const val ACTION_QUITTING = "org.prowl.torque.APP_QUITTING"
        private const val POLL_MS = 1000L
        private const val LOG_VALUES_MS = 5000L
        private const val REBIND_MS = 10_000L
    }

    private val thread = HandlerThread("torque").also { it.start() }
    private val h = Handler(thread.looper)
    @Volatile var snapshot = Snapshot()
        private set

    private var svc: ITorqueService? = null
    private var started = false
    private var bindingPkg: String? = null
    private var pidIds: List<String> = emptyList()
    private var lastValuesLog = 0L
    private var lastNote = ""
    private var listedForConnection = false
    private var allCount = 0

    private val conn = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            h.post {
                svc = ITorqueService.Stub.asInterface(binder)
                NsLog.log("TORQUE", "bound to ${name.flattenToShortString()}")
                listedForConnection = false
                update()
            }
        }
        override fun onServiceDisconnected(name: ComponentName) {
            h.post {
                NsLog.log("TORQUE", "service disconnected (Torque stopped)")
                svc = null; pidIds = emptyList(); listedForConnection = false
                snapshot = snapshot.copy(bound = false, obdConnected = null, pids = emptyList(), rows = emptyMap())
            }
        }
        override fun onBindingDied(name: ComponentName) {
            h.post { NsLog.log("TORQUE", "binding died"); unbind(); h.postDelayed(::tryBind, REBIND_MS) }
        }
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            h.post {
                when (i.action) {
                    ACTION_CONNECTED -> { NsLog.log("TORQUE", "OBD_CONNECTED broadcast"); listedForConnection = false; snapshot = snapshot.copy(obdConnected = true) }
                    ACTION_DISCONNECTED -> { NsLog.log("TORQUE", "OBD_DISCONNECTED broadcast"); snapshot = snapshot.copy(obdConnected = false) }
                    ACTION_LAUNCHED -> { NsLog.log("TORQUE", "APP_LAUNCHED broadcast"); if (svc == null) tryBind() }
                    ACTION_QUITTING -> NsLog.log("TORQUE", "APP_QUITTING broadcast")
                }
            }
        }
    }

    /** Safe to call repeatedly (service start, Diagnostics opening). */
    fun start() {
        h.post {
            if (started) return@post
            started = true
            val f = IntentFilter().apply {
                addAction(ACTION_CONNECTED); addAction(ACTION_DISCONNECTED); addAction(ACTION_LAUNCHED); addAction(ACTION_QUITTING)
            }
            if (Build.VERSION.SDK_INT >= 33) ctx.registerReceiver(receiver, f, Context.RECEIVER_EXPORTED) else ctx.registerReceiver(receiver, f)
            NsLog.log("TORQUE", "client started; installed=${installed()}")
            tryBind()
            h.post(poll)
        }
    }

    fun refreshPidList() { h.post { listedForConnection = false; update() } }

    private fun installed(): Install {
        val pm = ctx.packageManager
        fun has(p: String) = try { pm.getPackageInfo(p, 0); true } catch (_: PackageManager.NameNotFoundException) { false }
        return when { has(PRO) -> Install.PRO; has(LITE) -> Install.LITE; else -> Install.NONE }
    }

    private fun tryBind() {
        if (svc != null) return
        val inst = installed()
        if (inst == Install.NONE) { setNote("Torque is not installed"); snapshot = snapshot.copy(install = inst, bound = false); h.postDelayed(::tryBind, REBIND_MS); return }
        val pkg = if (inst == Install.PRO) PRO else LITE
        val ok = try {
            ctx.bindService(Intent().setClassName(pkg, SERVICE_CLASS), conn, 0)
        } catch (t: Throwable) {
            NsLog.log("TORQUE", "bind threw ${t.javaClass.simpleName}: ${t.message}"); false
        }
        if (ok) bindingPkg = pkg
        NsLog.log("TORQUE", "bind $pkg/$SERVICE_CLASS -> $ok" + if (inst == Install.LITE) " (Torque LITE: plugins may be unsupported)" else "")
        if (!ok) { setNote("Torque installed, but its plugin service did not accept the bind"); h.postDelayed(::tryBind, REBIND_MS) }
        else setNote("Waiting for Torque to start")
        snapshot = snapshot.copy(install = inst)
    }

    private fun unbind() {
        try { if (bindingPkg != null) ctx.unbindService(conn) } catch (_: Throwable) {}
        bindingPkg = null; svc = null
    }

    private fun setNote(n: String) {
        if (n != lastNote) { lastNote = n; NsLog.log("TORQUE", n) }
        snapshot = snapshot.copy(note = n)
    }

    private val poll = object : Runnable {
        override fun run() {
            try { update() } catch (t: Throwable) {
                NsLog.log("TORQUE", "poll failed: ${t.javaClass.simpleName} ${t.message}")
                svc = null; unbind(); h.postDelayed(::tryBind, REBIND_MS)
            }
            h.postDelayed(this, POLL_MS)
        }
    }

    private fun update() {
        val s = svc ?: run {
            if (!started) return
            snapshot = snapshot.copy(install = installed(), bound = false)
            return
        }
        val now = System.currentTimeMillis()
        val ecu = s.isConnectedToECU
        if (snapshot.obdConnected != ecu) NsLog.log("TORQUE", "OBD ${if (ecu) "connected" else "not connected"} (isConnectedToECU)")
        var snap = snapshot.copy(install = installed(), bound = true, obdConnected = ecu, note = "", allCount = allCount)
        if (snap.version == "—") {
            val v = s.version; val full = s.hasFullPermissions()
            snap = snap.copy(version = "API $v", fullPermissions = full.toString())
            NsLog.log("TORQUE", "Torque plugin API version $v, full permissions=$full")
        }
        if (!ecu) {
            setNote("Torque is running but not connected to the car (adapter)")
            snapshot = snap.copy(note = lastNote, rows = emptyMap())
            listedForConnection = false
            return
        }
        if (!listedForConnection) { snap = snap.copy(pids = listPids(s)).copy(allCount = allCount); listedForConnection = true; lastValuesLog = 0 }
        val ids = pidIds
        if (ids.isNotEmpty()) {
            val values = s.getPIDValuesAsDouble(ids.toTypedArray())
            val times = s.getPIDUpdateTime(ids.toTypedArray())
            val rows = HashMap<String, Pair<Double, Long>>()
            ids.forEachIndexed { i, id -> if (i < values.size && i < times.size) rows[id] = values[i] to times[i] }
            snap = snap.copy(rows = rows)
            if (now - lastValuesLog >= LOG_VALUES_MS) {
                lastValuesLog = now
                val line = snap.pids.joinToString("; ") { p ->
                    val r = rows[p.id]
                    "${p.shortName.ifEmpty { p.name }}=${TorquePid.valueText(r?.first, r?.second, now)}${p.unit}"
                }
                NsLog.log("TORQUE", "values: $line")
            }
        }
        snapshot = snap
    }

    /** Computationally expensive on Torque's side: once per connection, or when the Refresh button is pressed. */
    private fun listPids(s: ITorqueService): List<TorquePid> {
        val all = s.listAllPIDs()?.toList().orEmpty()
        val active = s.listActivePIDs()?.toSet().orEmpty()
        val ecu = s.listECUSupportedPIDs()?.toSet().orEmpty()
        val shown = all.filter { it in active || it in ecu }
        val info = if (shown.isEmpty()) emptyArray() else s.getPIDInformation(shown.toTypedArray())
        val pids = shown.mapIndexed { i, id -> TorquePid.parseInfo(id, info.getOrNull(i), id in active, id in ecu) }
        pidIds = pids.map { it.id }
        allCount = all.size
        NsLog.log("TORQUE", "PID list: ${all.size} known, ${ecu.size} ECU-supported, ${active.size} active; showing ${pids.size}")
        pids.forEach { NsLog.log("TORQUE", "PID ${it.id} | ${it.name} | ${it.shortName} | ${it.unit} | ${if (it.active) "active " else ""}${if (it.ecuSupported) "ecu" else ""}") }
        return pids
    }
}
