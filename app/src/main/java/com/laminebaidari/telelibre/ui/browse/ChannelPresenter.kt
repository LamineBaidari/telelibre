package com.laminebaidari.telelibre.ui.browse

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.leanback.widget.Presenter
import coil.dispose
import com.laminebaidari.telelibre.R
import com.laminebaidari.telelibre.model.Channel
import com.laminebaidari.telelibre.ui.app
import com.laminebaidari.telelibre.ui.displayName
import com.laminebaidari.telelibre.ui.loadLogo
import com.laminebaidari.telelibre.ui.toggleFavourite

/** Channel card: logo, name, and what is on now when the guide knows. Hold OK to toggle favourite. */
class ChannelPresenter : Presenter() {
    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder =
        ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.card_channel, parent, false))

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any) {
        val channel = item as Channel
        val view = viewHolder.view
        val context = view.context
        view.findViewById<TextView>(R.id.name).text = displayName(context, channel)
        view.findViewById<TextView>(R.id.now).text = context.app.epg.nowNext(channel.key).first?.title.orEmpty()
        val logo = view.findViewById<ImageView>(R.id.logo)
        logo.loadLogo(channel, 320, 180)
        view.setOnLongClickListener {
            toggleFavourite(context, channel)
            true
        }
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) {
        viewHolder.view.findViewById<ImageView>(R.id.logo).apply {
            dispose()
            setImageDrawable(null)
        }
        viewHolder.view.setOnLongClickListener(null)
    }
}
