package com.laminebaidari.telelibre.ui.browse

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.leanback.app.BrowseSupportFragment
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.FocusHighlight
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.ListRowPresenter
import androidx.leanback.widget.OnItemViewClickedListener
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.laminebaidari.telelibre.R
import com.laminebaidari.telelibre.data.Sources
import com.laminebaidari.telelibre.data.YtChannel
import com.laminebaidari.telelibre.model.Channel
import com.laminebaidari.telelibre.ui.app
import com.laminebaidari.telelibre.ui.guide.GuideActivity
import com.laminebaidari.telelibre.ui.player.PlayerActivity
import com.laminebaidari.telelibre.ui.search.SearchActivity
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/** Rows: Menu, Favoris, Sénégal, Sénégal sur YouTube, Afrique francophone, then by category (Radio among them). */
class BrowseFragment : BrowseSupportFragment() {
    private val rows = ArrayObjectAdapter(ListRowPresenter(FocusHighlight.ZOOM_FACTOR_SMALL))
    private val presenter = ChannelPresenter()
    // These two rows keep their adapters across rebuilds and are updated in place.
    private val menuItems = ArrayObjectAdapter(MenuPresenter())
    private val youtubeItems = ArrayObjectAdapter(YouTubePresenter())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = getString(R.string.app_name)
        headersState = HEADERS_ENABLED
        isHeadersTransitionOnBackEnabled = true
        brandColor = ContextCompat.getColor(requireContext(), R.color.brand_dark)
        searchAffordanceColor = ContextCompat.getColor(requireContext(), R.color.accent)
        adapter = rows
        setOnSearchClickedListener { startActivity(Intent(requireContext(), SearchActivity::class.java)) }
        onItemViewClickedListener = OnItemViewClickedListener { _, item, _, row ->
            when (item) {
                is Channel -> {
                    @Suppress("UNCHECKED_CAST")
                    val inRow = ((row as ListRow).adapter as ArrayObjectAdapter).unmodifiableList<Channel>()
                    PlayerActivity.start(requireContext(), item.key, inRow.map { it.key })
                }
                is YtChannel -> openOnYouTube(item)
                is MenuItem -> when (item.id) {
                    MenuItem.GUIDE -> startActivity(Intent(requireContext(), GuideActivity::class.java))
                    MenuItem.UPDATE -> requireContext().app.updater.install(requireActivity())
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val app = requireContext().app
        val repo = app.channels
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    combine(repo.channels, repo.favourites) { channels, favs -> channels to favs }
                        .distinctUntilChanged()
                        .collect { (channels, favs) -> rebuild(channels, favs) }
                }
                // New guide data only changes card subtitles: rebind instead of rebuilding rows.
                launch { app.epg.guide.collect { refreshCards() } }
                launch { app.youtube.channels.collect { youtubeItems.setItems(it, null) } }
                launch { app.updater.available.collect { updateMenu(it) } }
                app.youtube.refreshIfStale()
            }
        }
    }

    private fun rebuild(channels: List<Channel>, favourites: Set<String>) {
        val selected = selectedPosition
        rows.clear()
        var id = 0L
        rows.add(ListRow(HeaderItem(id++, getString(R.string.row_menu)), menuItems))
        val favs = channels.filter { it.key in favourites }
        if (favs.isNotEmpty()) addRow(id++, getString(R.string.row_favourites), favs)
        val byGroup = channels.groupBy { it.group }
        for (group in Sources.GROUP_ORDER) {
            byGroup[group]?.let { addRow(id++, group, it) }
            if (group == Sources.GROUP_SENEGAL) {
                rows.add(ListRow(HeaderItem(id++, getString(R.string.row_youtube)), youtubeItems))
            }
        }
        if (selected in 0 until rows.size()) setSelectedPosition(selected, false)
    }

    private fun addRow(id: Long, label: String, channels: List<Channel>) {
        val items = ArrayObjectAdapter(presenter).apply { addAll(0, channels) }
        rows.add(ListRow(HeaderItem(id, "$label (${channels.size})"), items))
    }

    private fun updateMenu(updateVersion: String?) {
        val items = buildList {
            if (updateVersion != null) {
                add(MenuItem(MenuItem.UPDATE, getString(R.string.menu_update, updateVersion), getString(R.string.menu_update_hint), R.drawable.ic_update))
            }
            add(MenuItem(MenuItem.GUIDE, getString(R.string.menu_guide), getString(R.string.menu_guide_hint), R.drawable.ic_guide))
        }
        menuItems.setItems(items, null)
    }

    private fun openOnYouTube(channel: YtChannel) {
        val videoId = channel.videoId
        val uri = Uri.parse(
            if (videoId != null) "https://www.youtube.com/watch?v=$videoId"
            else "https://www.youtube.com/channel/${channel.channelId}"
        )
        val intent = Intent(Intent.ACTION_VIEW, uri)
        try {
            startActivity(Intent(intent).setPackage(YOUTUBE_TV))
        } catch (e: ActivityNotFoundException) {
            try {
                startActivity(intent)
            } catch (e: ActivityNotFoundException) {
                Toast.makeText(requireContext(), R.string.yt_missing, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun refreshCards() {
        for (i in 0 until rows.size()) {
            val items = (rows.get(i) as ListRow).adapter as ArrayObjectAdapter
            items.notifyArrayItemRangeChanged(0, items.size())
        }
    }

    companion object {
        private const val YOUTUBE_TV = "com.google.android.youtube.tv"
    }
}
