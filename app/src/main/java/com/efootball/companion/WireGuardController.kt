package com.efootball.companion

import android.content.Context
import android.content.Intent
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Tunnel
import com.wireguard.config.Config
import java.io.ByteArrayInputStream

/**
 * WireGuard client bridge. Profiles and private keys remain in memory only.
 * The imported tunnel is automatically restricted to the installed eFootball app.
 */
class WireGuardController(context: Context) {
    private val appContext = context.applicationContext
    private val backend = GoBackend(appContext)
    private var config: Config? = null
    private val tunnel = object : Tunnel {
        override fun getName(): String = "titan-game"
        override fun onStateChange(newState: Tunnel.State) = Unit
    }

    private fun findEfootballPackages(): Set<String> {
        val launchers = appContext.packageManager.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),
            0
        )
        return launchers.mapNotNull { info ->
            val label = info.loadLabel(appContext.packageManager)?.toString().orEmpty()
            if (label.contains("eFootball", ignoreCase = true)) info.activityInfo?.packageName else null
        }.toSet()
    }

    @Synchronized
    fun importProfile(bytes: ByteArray): String {
        require(bytes.isNotEmpty() && bytes.size <= 64 * 1024) {
            "WireGuard profile must be between 1 byte and 64 KB."
        }

        val eFootballPackages = findEfootballPackages()
        require(eFootballPackages.size == 1) {
            if (eFootballPackages.isEmpty())
                "Could not identify an installed eFootball app. Install and open eFootball, then import again."
            else
                "More than one eFootball launcher was found. Remove the extra app before importing."
        }
        val eFootballPackage = eFootballPackages.single()

        // Standard WireGuard providers do not add Android's app-routing directive.
        // Inject it locally so only eFootball uses the tunnel, never the whole phone.
        val profileText = bytes.toString(Charsets.UTF_8).removePrefix("\uFEFF")
        val lines = profileText.lines().toMutableList()
        val interfaceIndex = lines.indexOfFirst { it.trim().equals("[Interface]", ignoreCase = true) }
        require(interfaceIndex >= 0) { "Invalid profile: missing [Interface] section." }
        val interfaceEnd = (interfaceIndex + 1 until lines.size)
            .firstOrNull { lines[it].trim().startsWith("[") } ?: lines.size
        val interfaceLines = lines.subList(interfaceIndex + 1, interfaceEnd)
        val existingIncluded = interfaceLines
            .filter { it.substringBefore("#").contains("=") }
            .filter { it.substringBefore("=").trim().equals("IncludedApplications", ignoreCase = true) }
            .flatMap { it.substringAfter("=").split(",").map(String::trim).filter(String::isNotEmpty) }
            .toSet()
        val hasExcluded = interfaceLines.any {
            it.substringBefore("#").substringBefore("=").trim().equals("ExcludedApplications", ignoreCase = true)
        }
        require(!hasExcluded) {
            "This profile uses ExcludedApplications. Import a standard profile without app filters; TITAN adds the eFootball-only filter safely."
        }
        require(existingIncluded.isEmpty() || existingIncluded == setOf(eFootballPackage)) {
            "Profile contains an app filter that is not limited to the installed eFootball app."
        }
        if (existingIncluded.isEmpty()) {
            lines.add(interfaceEnd, "IncludedApplications = $eFootballPackage")
        }

        val parsed = Config.parse(ByteArrayInputStream(lines.joinToString("\n").toByteArray(Charsets.UTF_8)))
        require(parsed.getPeers().isNotEmpty() && parsed.getPeers().all { it.getEndpoint().isPresent }) {
            "Profile must contain at least one peer with a valid relay endpoint."
        }
        val included = parsed.getInterface().getIncludedApplications()
        require(included == setOf(eFootballPackage)) {
            "Safety check failed: only the installed eFootball app may use this tunnel."
        }
        config = parsed
        return "Profile validated • ${parsed.getPeers().size} peer(s) • eFootball-only routing enabled automatically"
    }

    fun isProfileReady(): Boolean = config != null

    @Synchronized
    @Throws(Exception::class)
    fun connect(): String {
        val activeConfig = config ?: throw IllegalStateException("Import a valid WireGuard profile first.")
        backend.setState(tunnel, Tunnel.State.UP, activeConfig)
        return "WireGuard tunnel started. Confirm handshake and traffic before a match."
    }

    @Synchronized
    @Throws(Exception::class)
    fun disconnect(): String {
        backend.setState(tunnel, Tunnel.State.DOWN, null)
        return "WireGuard tunnel disconnected."
    }

    fun isConnected(): Boolean = backend.getState(tunnel) == Tunnel.State.UP
}
