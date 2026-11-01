package com.laminebaidari.telelibre.home

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.tvprovider.media.tv.PreviewChannel
import androidx.tvprovider.media.tv.PreviewChannelHelper
import androidx.tvprovider.media.tv.PreviewProgram
import androidx.tvprovider.media.tv.TvContractCompat
import com.laminebaidari.telelibre.R
import com.laminebaidari.telelibre.model.Channel
import com.laminebaidari.telelibre.model.Programme

/** "Télé Libre – Favoris" row on the TV's home screen: one tile per favourite, opening it directly. */
class HomeChannel(private val context: Context) {
    private val prefs = context.getSharedPreferences("telelibre", Context.MODE_PRIVATE)
    private val helper = PreviewChannelHelper(context)

    /** Replaces the row's tiles with [favourites]. Runs off the main thread (content provider calls). */
    fun sync(favourites: List<Channel>, nowPlaying: (String) -> Programme?) {
        // No row on the home screen until there is something to put in it.
        if (favourites.isEmpty() && prefs.getLong(KEY_CHANNEL, -1L) == -1L) return
        try {
            val channelId = channelId() ?: return
            prefs.getStringSet(KEY_PROGRAMS, emptySet())!!.forEach {
                runCatching { helper.deletePreviewProgram(it.toLong()) }
            }
            val ids = favourites.mapIndexed { i, channel ->
                val builder = PreviewProgram.Builder()
                    .setChannelId(channelId)
                    .setType(TvContractCompat.PreviewPrograms.TYPE_CHANNEL)
                    .setTitle(channel.name)
                    .setDescription(nowPlaying(channel.key)?.title ?: channel.group)
                    .setPosterArtAspectRatio(TvContractCompat.PreviewPrograms.ASPECT_RATIO_16_9)
                    .setIntentUri(playUri(channel.key))
                    .setInternalProviderId(channel.key)
                    .setWeight(favourites.size - i)
                channel.logo?.let { builder.setPosterArtUri(Uri.parse(it)) }
                helper.publishPreviewProgram(builder.build()).toString()
            }
            prefs.edit().putStringSet(KEY_PROGRAMS, ids.toSet()).apply()
        } catch (e: Exception) {
            // Some launchers have no home-screen channels; the app works the same without them.
            Log.w(TAG, "Home channel sync failed", e)
        }
    }

    private fun channelId(): Long? {
        val saved = prefs.getLong(KEY_CHANNEL, -1L)
        if (saved != -1L && helper.getPreviewChannel(saved) != null) return saved
        val channel = PreviewChannel.Builder()
            .setDisplayName(context.getString(R.string.home_channel_name))
            .setAppLinkIntentUri(Uri.parse("telelibre://home"))
            .setLogo(logo())
            .build()
        val id = helper.publishDefaultChannel(channel)
        prefs.edit().putLong(KEY_CHANNEL, id).remove(KEY_PROGRAMS).apply()
        return id
    }

    private fun logo(): Bitmap {
        val drawable = ContextCompat.getDrawable(context, R.drawable.ic_launcher)!!
        val bitmap = Bitmap.createBitmap(160, 160, Bitmap.Config.ARGB_8888)
        drawable.setBounds(0, 0, 160, 160)
        drawable.draw(Canvas(bitmap))
        return bitmap
    }

    companion object {
        private const val TAG = "HomeChannel"
        private const val KEY_CHANNEL = "homeChannelId"
        private const val KEY_PROGRAMS = "homeProgramIds"

        fun playUri(key: String): Uri = Uri.Builder().scheme("telelibre").authority("play").appendQueryParameter("key", key).build()
    }
}
