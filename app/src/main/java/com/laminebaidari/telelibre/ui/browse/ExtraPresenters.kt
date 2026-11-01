package com.laminebaidari.telelibre.ui.browse

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.leanback.widget.Presenter
import coil.dispose
import coil.load
import com.laminebaidari.telelibre.R
import com.laminebaidari.telelibre.data.YtChannel

/** An action card in the Menu row (programme guide, install update). */
data class MenuItem(val id: Int, val title: String, val subtitle: String, @DrawableRes val icon: Int) {
    companion object {
        const val GUIDE = 1
        const val UPDATE = 2
    }
}

class MenuPresenter : Presenter() {
    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder =
        ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.card_channel, parent, false))

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any) {
        val menu = item as MenuItem
        val view = viewHolder.view
        view.findViewById<TextView>(R.id.name).text = menu.title
        view.findViewById<TextView>(R.id.now).text = menu.subtitle
        view.findViewById<ImageView>(R.id.logo).setImageResource(menu.icon)
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) = Unit
}

/** Senegalese broadcaster on YouTube: "🔴 En direct" while live, else the title of its newest video. */
class YouTubePresenter : Presenter() {
    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder =
        ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.card_channel, parent, false))

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any) {
        val channel = item as YtChannel
        val view = viewHolder.view
        val context = view.context
        view.findViewById<TextView>(R.id.name).text = channel.name
        view.findViewById<TextView>(R.id.now).text = when {
            channel.liveVideoId != null -> context.getString(R.string.yt_live)
            channel.latestTitle != null -> channel.latestTitle
            else -> ""
        }
        view.findViewById<ImageView>(R.id.logo).load(channel.image) {
            size(320, 180)
            placeholder(R.drawable.ic_tv)
            error(R.drawable.ic_tv)
        }
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) {
        viewHolder.view.findViewById<ImageView>(R.id.logo).apply {
            dispose()
            setImageDrawable(null)
        }
    }
}
