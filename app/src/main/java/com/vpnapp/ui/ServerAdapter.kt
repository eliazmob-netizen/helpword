package com.vpnapp.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.vpnapp.R
import com.vpnapp.model.PingColor
import com.vpnapp.model.ProxyServer

class ServerAdapter(
    private val onClick: (ProxyServer) -> Unit
) : ListAdapter<ProxyServer, ServerAdapter.VH>(DIFF) {

    var selected: ProxyServer? = null

    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        val flag:  TextView = v.findViewById(R.id.tvFlag)
        val name:  TextView = v.findViewById(R.id.tvName)
        val sub:   TextView = v.findViewById(R.id.tvSub)
        val ping:  TextView = v.findViewById(R.id.tvPing)
        val check: View     = v.findViewById(R.id.ivCheck)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(
        LayoutInflater.from(parent.context).inflate(R.layout.item_server, parent, false)
    )

    override fun onBindViewHolder(h: VH, pos: Int) {
        val srv = getItem(pos)
        val ctx = h.flag.context
        val isSelected = srv.name == selected?.name

        h.flag.text  = srv.flagEmoji
        h.name.text  = srv.name
        h.sub.text   = "${srv.displayType} · ${srv.server}:${srv.port}"
        h.check.visibility = if (isSelected) View.VISIBLE else View.INVISIBLE

        // ping pill
        val (bgRes, fgRes, label) = when (srv.pingColor) {
            PingColor.LOW  -> Triple(R.color.ping_bg_low,  R.color.ping_fg_low,  "${srv.pingMs} ms")
            PingColor.MID  -> Triple(R.color.ping_bg_mid,  R.color.ping_fg_mid,  "${srv.pingMs} ms")
            PingColor.HIGH -> Triple(R.color.ping_bg_high, R.color.ping_fg_high, "${srv.pingMs} ms")
            PingColor.DEAD -> Triple(R.color.ping_bg_dead, R.color.ping_fg_dead, "—")
        }
        h.ping.text = label
        h.ping.backgroundTintList = ContextCompat.getColorStateList(ctx, bgRes)
        h.ping.setTextColor(ContextCompat.getColor(ctx, fgRes))

        // row highlight
        h.itemView.setBackgroundColor(
            ContextCompat.getColor(ctx, if (isSelected) R.color.item_selected_bg else R.color.transparent)
        )

        h.itemView.setOnClickListener { onClick(srv) }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<ProxyServer>() {
            override fun areItemsTheSame(a: ProxyServer, b: ProxyServer) =
                a.name == b.name && a.server == b.server
            override fun areContentsTheSame(a: ProxyServer, b: ProxyServer) =
                a.pingMs == b.pingMs && a.name == b.name
        }
    }
}
