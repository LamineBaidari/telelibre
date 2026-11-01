package com.laminebaidari.telelibre.ui.browse

import android.content.Intent
import android.os.Bundle
import android.view.View
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
import com.laminebaidari.telelibre.model.Channel
import com.laminebaidari.telelibre.ui.app
import com.laminebaidari.telelibre.ui.player.PlayerActivity
import com.laminebaidari.telelibre.ui.search.SearchActivity
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/** Rows of channels: Favoris, Sénégal, Afrique francophone, then by category. */
class BrowseFragment : BrowseSupportFragment() {
    private val rows = ArrayObjectAdapter(ListRowPresenter(FocusHighlight.ZOOM_FACTOR_SMALL))
    private val presenter = ChannelPresenter()

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
            val channel = item as Channel
            @Suppress("UNCHECKED_CAST")
            val inRow = ((row as ListRow).adapter as ArrayObjectAdapter).unmodifiableList<Channel>()
            PlayerActivity.start(requireContext(), channel.key, inRow.map { it.key })
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val repo = requireContext().app.channels
        val guide = requireContext().app.epg.guide
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    combine(repo.channels, repo.favourites) { channels, favs -> channels to favs }
                        .distinctUntilChanged()
                        .collect { (channels, favs) -> rebuild(channels, favs) }
                }
                // New guide data only changes card subtitles: rebind instead of rebuilding rows.
                launch { guide.collect { refreshCards() } }
            }
        }
    }

    private fun rebuild(channels: List<Channel>, favourites: Set<String>) {
        val selected = selectedPosition
        rows.clear()
        var id = 0L
        val favs = channels.filter { it.key in favourites }
        if (favs.isNotEmpty()) addRow(id++, getString(R.string.row_favourites), favs)
        val byGroup = channels.groupBy { it.group }
        for (group in Sources.GROUP_ORDER) byGroup[group]?.let { addRow(id++, group, it) }
        if (selected in 0 until rows.size()) setSelectedPosition(selected, false)
    }

    private fun addRow(id: Long, label: String, channels: List<Channel>) {
        val items = ArrayObjectAdapter(presenter).apply { addAll(0, channels) }
        rows.add(ListRow(HeaderItem(id, "$label (${channels.size})"), items))
    }

    private fun refreshCards() {
        for (i in 0 until rows.size()) {
            val items = (rows.get(i) as ListRow).adapter as ArrayObjectAdapter
            items.notifyArrayItemRangeChanged(0, items.size())
        }
    }
}
