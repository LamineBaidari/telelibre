package com.laminebaidari.telelibre.ui.search

import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.FragmentActivity
import androidx.leanback.app.VerticalGridSupportFragment
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.FocusHighlight
import androidx.leanback.widget.OnItemViewClickedListener
import androidx.leanback.widget.VerticalGridPresenter
import com.laminebaidari.telelibre.R
import com.laminebaidari.telelibre.epg.EpgMatch
import com.laminebaidari.telelibre.model.Channel
import com.laminebaidari.telelibre.ui.app
import com.laminebaidari.telelibre.ui.browse.ChannelPresenter
import com.laminebaidari.telelibre.ui.player.PlayerActivity

/** Type a name, the grid below narrows down as you type. No voice search (avoids the mic permission). */
class SearchActivity : FragmentActivity() {
    private lateinit var grid: ResultsFragment

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_search)
        grid = supportFragmentManager.findFragmentById(R.id.grid_container) as? ResultsFragment
            ?: ResultsFragment().also {
                supportFragmentManager.beginTransaction().replace(R.id.grid_container, it).commitNow()
            }
        val empty = findViewById<TextView>(R.id.empty)
        val query = findViewById<EditText>(R.id.query)
        query.doAfterTextChanged { text ->
            val needle = EpgMatch.normalize(text?.toString().orEmpty())
            val results = app.channels.channels.value.filter { needle.isEmpty() || EpgMatch.normalize(it.name).contains(needle) }
            grid.show(results)
            empty.visibility = if (results.isEmpty()) View.VISIBLE else View.GONE
        }
        grid.show(app.channels.channels.value)
        query.requestFocus()
    }

    class ResultsFragment : VerticalGridSupportFragment() {
        private val items = ArrayObjectAdapter(ChannelPresenter())

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            gridPresenter = VerticalGridPresenter(FocusHighlight.ZOOM_FACTOR_SMALL).apply { numberOfColumns = 5 }
            adapter = items
            onItemViewClickedListener = OnItemViewClickedListener { _, item, _, _ ->
                @Suppress("UNCHECKED_CAST")
                val results = items.unmodifiableList<Channel>()
                PlayerActivity.start(requireContext(), (item as Channel).key, results.map { it.key })
            }
        }

        fun show(channels: List<Channel>) = items.setItems(channels, null)
    }
}
