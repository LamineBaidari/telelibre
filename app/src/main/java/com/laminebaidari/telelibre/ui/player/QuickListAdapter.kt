package com.laminebaidari.telelibre.ui.player

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.laminebaidari.telelibre.R
import com.laminebaidari.telelibre.model.Channel
import com.laminebaidari.telelibre.ui.app
import com.laminebaidari.telelibre.ui.displayName
import com.laminebaidari.telelibre.ui.loadLogo

class QuickListAdapter(
    private val onClick: (Int) -> Unit,
    private val onLongClick: (Int) -> Unit,
) : RecyclerView.Adapter<QuickListAdapter.Holder>() {
    var channels: List<Channel> = emptyList()

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val logo: ImageView = view.findViewById(R.id.logo)
        val name: TextView = view.findViewById(R.id.name)
        val now: TextView = view.findViewById(R.id.now)
    }

    override fun getItemCount() = channels.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_quick_channel, parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val channel = channels[position]
        val context = holder.itemView.context
        holder.name.text = "${position + 1}. ${displayName(context, channel)}"
        holder.now.text = context.app.epg.nowNext(channel.key).first?.title.orEmpty()
        holder.logo.loadLogo(channel, 144, 80)
        holder.itemView.setOnClickListener { onClick(holder.bindingAdapterPosition) }
        holder.itemView.setOnLongClickListener {
            onLongClick(holder.bindingAdapterPosition)
            true
        }
    }
}
