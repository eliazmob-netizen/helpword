package com.vpnapp.utils

import android.content.Context
import com.vpnapp.model.ProxyServer
import com.vpnapp.parser.ClashConfigParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

object ConfigRepository {

    const val CONFIG_URL  = "http://217.26.28.135/clash_final.yaml"
    const val SOCKS_PORT  = 7891
    const val HTTP_PORT   = 7890

    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private var rawYaml: String? = null

    // ── Fetch ──────────────────────────────────────────────────
    suspend fun fetchYaml(force: Boolean = false): Result<String> =
        withContext(Dispatchers.IO) {
            if (!force && rawYaml != null) return@withContext Result.success(rawYaml!!)
            try {
                val body = client.newCall(Request.Builder().url(CONFIG_URL).build())
                    .execute().use { r ->
                        if (!r.isSuccessful) throw Exception("HTTP ${r.code}")
                        r.body?.string() ?: throw Exception("Empty body")
                    }
                rawYaml = body
                Result.success(body)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    // ── Parse proxy list ───────────────────────────────────────
    fun parseProxies(yaml: String): List<ProxyServer> =
        ClashConfigParser.parseProxies(yaml)

    // ── Write single-proxy Clash config to disk ────────────────
    // Returns path to the written config file
    suspend fun writeSingleProxyConfig(
        context: Context,
        selectedProxyName: String,
    ): String = withContext(Dispatchers.IO) {
        val yaml = rawYaml ?: throw IllegalStateException("YAML not loaded")
        val cfg  = ClashConfigParser.buildSingleProxyConfig(yaml, selectedProxyName, SOCKS_PORT, HTTP_PORT)
        val dir  = File(context.filesDir, "clash").also { it.mkdirs() }
        val file = File(dir, "config.yaml")
        file.writeText(cfg)
        file.absolutePath
    }

    fun clearCache() { rawYaml = null }
}
