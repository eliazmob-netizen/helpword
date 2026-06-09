package com.vpnapp.parser

import com.vpnapp.model.ProxyServer
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor

object ClashConfigParser {

    @Suppress("UNCHECKED_CAST")
    fun parseProxies(yamlText: String): List<ProxyServer> {
        return try {
            val opts = LoaderOptions().apply { maxAliasesForCollections = 500 }
            val yaml = Yaml(SafeConstructor(opts))
            val doc  = yaml.load<Map<String, Any>>(yamlText) ?: return emptyList()
            val list = doc["proxies"] as? List<*> ?: return emptyList()
            list.filterIsInstance<Map<String, Any>>().mapNotNull { map ->
                val name   = map["name"]   as? String ?: return@mapNotNull null
                val server = map["server"] as? String ?: return@mapNotNull null
                val port   = (map["port"]  as? Number)?.toInt() ?: return@mapNotNull null
                val type   = (map["type"]  as? String ?: "unknown").lowercase()
                ProxyServer(name = name, server = server, port = port, type = type)
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Build a minimal Clash config that uses ONLY one proxy.
     * We keep the original proxy definition verbatim and point
     * all rules to it, then expose a local SOCKS5+HTTP proxy.
     */
    @Suppress("UNCHECKED_CAST")
    fun buildSingleProxyConfig(
        fullYaml: String,
        selectedProxyName: String,
        socksPort: Int = 7891,
        httpPort: Int  = 7890,
    ): String {
        return try {
            val opts = LoaderOptions().apply { maxAliasesForCollections = 500 }
            val yaml = Yaml(SafeConstructor(opts))
            val doc  = yaml.load<Map<String, Any>>(fullYaml) ?: return fallbackConfig(selectedProxyName, socksPort, httpPort)
            val allProxies = doc["proxies"] as? List<*> ?: return fallbackConfig(selectedProxyName, socksPort, httpPort)

            // Find the selected proxy definition
            val proxyDef = allProxies.filterIsInstance<Map<String, Any>>()
                .firstOrNull { (it["name"] as? String) == selectedProxyName }
                ?: return fallbackConfig(selectedProxyName, socksPort, httpPort)

            // Serialise proxy map back to YAML inline
            val proxyYaml = Yaml().dump(listOf(proxyDef)).trimEnd()

            """
mixed-port: $httpPort
socks-port: $socksPort
allow-lan: false
mode: rule
log-level: silent
ipv6: false

dns:
  enable: true
  enhanced-mode: fake-ip
  nameserver:
    - 1.1.1.1
    - 8.8.8.8

proxies:
${proxyYaml.lines().joinToString("\n") { "  $it" }}

proxy-groups:
  - name: PROXY
    type: select
    proxies:
      - $selectedProxyName

rules:
  - MATCH,PROXY
""".trimIndent()
        } catch (e: Exception) {
            fallbackConfig(selectedProxyName, socksPort, httpPort)
        }
    }

    private fun fallbackConfig(name: String, socksPort: Int, httpPort: Int) = """
mixed-port: $httpPort
socks-port: $socksPort
allow-lan: false
mode: rule
log-level: silent
proxies: []
proxy-groups:
  - name: PROXY
    type: select
    proxies: []
rules:
  - MATCH,DIRECT
""".trimIndent()
}
