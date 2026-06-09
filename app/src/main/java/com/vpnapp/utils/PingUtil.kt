package com.vpnapp.utils

import com.vpnapp.model.ProxyServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

object PingUtil {

    private const val TIMEOUT_MS = 3000

    suspend fun ping(server: ProxyServer): Long = withContext(Dispatchers.IO) {
        try {
            val t0 = System.currentTimeMillis()
            Socket().use { it.connect(InetSocketAddress(server.server, server.port), TIMEOUT_MS) }
            System.currentTimeMillis() - t0
        } catch (_: Exception) { -2L }
    }

    suspend fun pingAll(
        servers: List<ProxyServer>,
        concurrency: Int = 12,
        onUpdate: (ProxyServer) -> Unit,
    ) = withContext(Dispatchers.IO) {
        val sem = Semaphore(concurrency)
        servers.map { srv ->
            async {
                sem.withPermit {
                    srv.pingMs = ping(srv)
                    onUpdate(srv)
                }
            }
        }.forEach { it.await() }
    }
}
