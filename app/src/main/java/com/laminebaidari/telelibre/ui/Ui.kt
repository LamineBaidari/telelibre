package com.laminebaidari.telelibre.ui

import android.content.Context
import android.widget.ImageView
import android.widget.Toast
import coil.load
import com.laminebaidari.telelibre.App
import com.laminebaidari.telelibre.R
import com.laminebaidari.telelibre.model.Channel
import com.laminebaidari.telelibre.model.Programme
import java.text.DateFormat
import java.util.Date

val Context.app: App get() = applicationContext as App

fun ImageView.loadLogo(channel: Channel, widthPx: Int, heightPx: Int) {
    if (channel.logo == null) {
        setImageResource(R.drawable.ic_tv)
        return
    }
    load(channel.logo) {
        size(widthPx, heightPx)
        placeholder(R.drawable.ic_tv)
        error(R.drawable.ic_tv)
    }
}

/** "20:00–21:30 Journal" */
fun Programme.label(): String {
    val f = DateFormat.getTimeInstance(DateFormat.SHORT)
    return "${f.format(Date(start))}–${f.format(Date(stop))} $title"
}

fun displayName(context: Context, channel: Channel): String =
    if (channel.key in context.app.channels.favourites.value) "★ ${channel.name}" else channel.name

fun toggleFavourite(context: Context, channel: Channel) {
    val added = context.app.channels.toggleFavourite(channel.key)
    val message = context.getString(if (added) R.string.fav_added else R.string.fav_removed, channel.name)
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
}
