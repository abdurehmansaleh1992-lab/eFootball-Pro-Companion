package com.efootball.companion

import android.content.Context
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Tunnel
import com.wireguard.config.Config
import java.io.ByteArrayInputStream

/**
 * WireGuard client bridge. Profiles are held in memory only; private keys are never
 * written to preferences or logs. A profile must explicitly use IncludedApplications
 * so the tunnel cannot silently capture all phone traffic.
 */
class WireGuardController(context: Context) {
    private val appContext = context.applicationContext
    private val backend = GoBackend(appContext)
    private var config: Config? = null
    private val tunnel = object : Tunnel {
        override fun getName(): String = "titan-game"
        override fun onStateChange(newState: Tunnel.State) = Unit
    }

    @Synchronized
    fun importProfile(bytes: ByteArray): String {
        require(bytes.isNotEmpty() && bytes.size <= 64 * 1024) {
            "WireGuard profile must be between 1 byte and 64 KB."
        }
        val parsed = Config.parse(ByteArrayInputStream(bytes))
        require(parsed.getPeers().isNotEmpty() && parsed.getPeers().all { it.getEndpoint().isPresent }) {
            "Profile must contain at least one peer with a valid relay endpoint."
        }
        val included = parsed.getInterface().getIncludedApplications()
        require(included.isNotEmpty()) {
            "Safety check: profile must contain IncludedApplications for eFootball only. Full-device routing is disabled."
        }
        val installedLaunchers = appContext.packageManager.queryIntentActivities(
            android.content.Intent(android.content.Intent.ACTION_MAIN).addCategory(android.content.Intent.CATEGORY_LAUNCHER),
            0
        )
        val eFootballPackages = installedLaunchers.mapNotNull { info ->
            val label = info.loadLabel(appContext.packageManager)?.toString().orEmpty()
            if (label.contains("eFootball", ignoreCase = true)) info.activityInfo?.packageName else null
        }.toSet()
        require(eFootballPackages.isNotEmpty()) {
            "Could not identify an installed eFootball app. Open eFootball once, then import the profile again."
        }
        require(included.any { it in eFootballPackages }) {
            "This profile does not include the installed eFootball app. Add its exact package name to IncludedApplications."
        }
        require(included.all { it in eFootballPackages }) {
            "For safety, this build accepts only eFootball in IncludedApplications."
        }
        config = parsed
        return "Profile validated • " + parsed.getPeers().size + " peer(s) • eFootball-only routing"
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
