package com.vpnapp.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.vpnapp.model.ProxyServer
import com.vpnapp.utils.ConfigRepository
import com.vpnapp.utils.PingUtil
import kotlinx.coroutines.launch

sealed class UiState {
    object Loading  : UiState()
    object Scanning : UiState()
    data class Ready(val servers: List<ProxyServer>) : UiState()
    data class Error(val message: String)            : UiState()
}

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val _uiState       = MutableLiveData<UiState>(UiState.Loading)
    val uiState: LiveData<UiState> = _uiState

    private val _connected     = MutableLiveData(false)
    val connected: LiveData<Boolean> = _connected

    private val _selected      = MutableLiveData<ProxyServer?>(null)
    val selected: LiveData<ProxyServer?> = _selected

    private val _error         = MutableLiveData<String?>(null)
    val error: LiveData<String?> = _error

    private var allServers: List<ProxyServer> = emptyList()
    var rawYaml: String = ""
        private set

    // ── Load & scan ──────────────────────────────────────────

    fun load(force: Boolean = false) {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            val result = ConfigRepository.fetchYaml(force)
            if (result.isFailure) {
                _uiState.value = UiState.Error(result.exceptionOrNull()?.message ?: "Ошибка загрузки")
                return@launch
            }
            rawYaml    = result.getOrThrow()
            allServers = ConfigRepository.parseProxies(rawYaml)
            if (allServers.isEmpty()) {
                _uiState.value = UiState.Error("Серверы не найдены в конфиге")
                return@launch
            }
            scan()
        }
    }

    fun rescan() {
        allServers.forEach { it.pingMs = -1L }
        viewModelScope.launch { scan() }
    }

    private suspend fun scan() {
        _uiState.value = UiState.Scanning
        PingUtil.pingAll(allServers, concurrency = 12) { _ ->
            postAlive()
        }
        postAlive()
        // auto-pick best if nothing selected yet
        if (_selected.value == null || !(_selected.value?.isAlive == true)) {
            _selected.postValue(bestServer())
        }
    }

    private fun postAlive() {
        val alive = allServers.filter { it.isAlive }.sortedBy { it.pingMs }
        _uiState.postValue(UiState.Ready(alive))
    }

    // ── Selection ────────────────────────────────────────────

    fun select(server: ProxyServer) {
        _selected.value = server
    }

    fun bestServer(): ProxyServer? =
        allServers.filter { it.isAlive }.minByOrNull { it.pingMs }

    // ── VPN state ────────────────────────────────────────────

    fun setConnected(on: Boolean) {
        _connected.value = on
    }

    fun setError(msg: String?) {
        _error.value = msg
    }

    // ── Config write ─────────────────────────────────────────

    suspend fun writeConfig(proxyName: String): String =
        ConfigRepository.writeSingleProxyConfig(getApplication(), proxyName)
}
