package com.vpnapp.ui

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.vpnapp.R
import com.vpnapp.databinding.ActivityMainBinding
import com.vpnapp.model.ProxyServer
import com.vpnapp.service.ClashVpnService
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var b: ActivityMainBinding
    private val vm: MainViewModel by viewModels()
    private lateinit var adapter: ServerAdapter

    // VPN permission result
    private val vpnPermLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) launchVpn()
        else Toast.makeText(this, "VPN permission denied", Toast.LENGTH_SHORT).show()
    }

    // Broadcast from service
    private val stateReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            val running = intent?.getBooleanExtra(ClashVpnService.EXTRA_RUNNING, false) ?: false
            val err     = intent?.getStringExtra(ClashVpnService.EXTRA_ERROR)
            vm.setConnected(running)
            if (err != null) Toast.makeText(this@MainActivity, err, Toast.LENGTH_LONG).show()
        }
    }

    // ── Lifecycle ────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)

        setupList()
        setupObservers()
        setupClicks()
        registerStateReceiver()

        vm.load()
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(stateReceiver)
    }

    // ── Setup ────────────────────────────────────────────────

    private fun setupList() {
        adapter = ServerAdapter { srv ->
            vm.select(srv)
            // if already connected → reconnect to new server
            if (vm.connected.value == true) {
                stopVpn()
                lifecycleScope.launch { startVpnWith(srv) }
            }
        }
        b.rvServers.layoutManager = LinearLayoutManager(this)
        b.rvServers.adapter = adapter
        b.rvServers.itemAnimator = null
    }

    private fun setupObservers() {
        vm.uiState.observe(this) { state ->
            when (state) {
                is UiState.Loading -> {
                    b.progress.visibility  = View.VISIBLE
                    b.tvSub.text           = getString(R.string.loading_config)
                    b.rvServers.visibility = View.GONE
                    b.tvEmpty.visibility   = View.GONE
                    b.btnRescan.isEnabled  = false
                }
                is UiState.Scanning -> {
                    b.progress.visibility  = View.VISIBLE
                    b.tvSub.text           = getString(R.string.scanning)
                    b.rvServers.visibility = View.VISIBLE
                    b.tvEmpty.visibility   = View.GONE
                    b.btnRescan.isEnabled  = false
                }
                is UiState.Ready -> {
                    b.progress.visibility  = View.GONE
                    b.btnRescan.isEnabled  = true
                    adapter.selected = vm.selected.value
                    adapter.submitList(state.servers.toList())
                    if (state.servers.isEmpty()) {
                        b.rvServers.visibility = View.GONE
                        b.tvEmpty.visibility   = View.VISIBLE
                        b.tvSub.text           = getString(R.string.no_servers)
                    } else {
                        b.rvServers.visibility = View.VISIBLE
                        b.tvEmpty.visibility   = View.GONE
                        b.tvSub.text = resources.getQuantityString(
                            R.plurals.servers_live, state.servers.size, state.servers.size
                        )
                    }
                }
                is UiState.Error -> {
                    b.progress.visibility  = View.GONE
                    b.btnRescan.isEnabled  = true
                    b.rvServers.visibility = View.GONE
                    b.tvEmpty.visibility   = View.VISIBLE
                    b.tvEmpty.text         = state.message
                    b.tvSub.text           = getString(R.string.error_label)
                }
            }
        }

        vm.connected.observe(this) { on ->
            applyConnectedState(on)
        }

        vm.selected.observe(this) { srv ->
            adapter.selected = srv
            adapter.notifyDataSetChanged()
            if (vm.connected.value == true && srv != null) {
                b.tvServer.text = "${srv.flagEmoji} ${srv.name}"
                b.tvPing.text   = if (srv.pingMs >= 0) "${srv.pingMs} ms" else ""
            }
        }
    }

    private fun setupClicks() {
        b.btnConnect.setOnClickListener {
            if (vm.connected.value == true) {
                stopVpn()
            } else {
                requestVpnPermission()
            }
        }
        b.btnRescan.setOnClickListener { vm.rescan() }
    }

    // ── VPN ──────────────────────────────────────────────────

    private fun requestVpnPermission() {
        val intent = VpnService.prepare(this)
        if (intent != null) vpnPermLauncher.launch(intent)
        else launchVpn()
    }

    private fun launchVpn() {
        val srv = vm.selected.value ?: vm.bestServer() ?: run {
            Toast.makeText(this, "Нет доступных серверов", Toast.LENGTH_SHORT).show()
            return
        }
        vm.select(srv)
        lifecycleScope.launch { startVpnWith(srv) }
    }

    private suspend fun startVpnWith(srv: ProxyServer) {
        val configPath = try {
            vm.writeConfig(srv.name)
        } catch (e: Exception) {
            Toast.makeText(this@MainActivity, "Ошибка конфига: ${e.message}", Toast.LENGTH_LONG).show()
            return
        }

        b.tvServer.text = "${srv.flagEmoji} ${srv.name}"
        b.tvPing.text   = "Подключение…"

        val intent = Intent(this, ClashVpnService::class.java).apply {
            action = ClashVpnService.ACTION_START
            putExtra(ClashVpnService.EXTRA_CONFIG_PATH,  configPath)
            putExtra(ClashVpnService.EXTRA_SERVER_NAME,  srv.name)
            putExtra(ClashVpnService.EXTRA_SERVER_TYPE,  srv.type)
            putExtra(ClashVpnService.EXTRA_SERVER_PING,  srv.pingMs)
        }
        ContextCompat.startForegroundService(this, intent)
    }

    private fun stopVpn() {
        startService(Intent(this, ClashVpnService::class.java).apply {
            action = ClashVpnService.ACTION_STOP
        })
    }

    // ── UI helpers ───────────────────────────────────────────

    private fun applyConnectedState(on: Boolean) {
        if (on) {
            b.statusDot.setColorFilter(ContextCompat.getColor(this, R.color.green_live))
            b.tvStatusLabel.text = getString(R.string.connected)
            b.btnConnect.text    = getString(R.string.disconnect)
            b.btnConnect.setBackgroundColor(ContextCompat.getColor(this, R.color.btn_disconnect))
            b.btnConnect.setTextColor(ContextCompat.getColor(this, R.color.btn_disconnect_text))
            b.cardTop.strokeColor = ContextCompat.getColor(this, R.color.card_border_connected)
        } else {
            b.statusDot.setColorFilter(ContextCompat.getColor(this, R.color.dot_off))
            b.tvStatusLabel.text = getString(R.string.not_connected)
            b.tvServer.text      = getString(R.string.not_connected)
            b.tvPing.text        = ""
            b.btnConnect.text    = getString(R.string.connect_fastest)
            b.btnConnect.setBackgroundColor(ContextCompat.getColor(this, R.color.btn_connect))
            b.btnConnect.setTextColor(ContextCompat.getColor(this, R.color.btn_connect_text))
            b.cardTop.strokeColor = ContextCompat.getColor(this, R.color.card_border)
        }
    }

    private fun registerStateReceiver() {
        val filter = IntentFilter(ClashVpnService.BROADCAST_STATE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            registerReceiver(stateReceiver, filter, RECEIVER_NOT_EXPORTED)
        else
            registerReceiver(stateReceiver, filter)
    }
}
