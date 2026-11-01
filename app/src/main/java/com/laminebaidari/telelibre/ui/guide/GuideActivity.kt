package com.laminebaidari.telelibre.ui.guide

import android.os.Bundle
import android.text.TextUtils
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.laminebaidari.telelibre.R
import com.laminebaidari.telelibre.model.Channel
import com.laminebaidari.telelibre.ui.app
import com.laminebaidari.telelibre.ui.displayName
import com.laminebaidari.telelibre.ui.loadLogo
import com.laminebaidari.telelibre.ui.player.PlayerActivity
import java.text.DateFormat
import java.util.Date

/**
 * Programme grid: one row per channel that has guide data, 2.5 hours at a time.
 * Left/Right past the edge of a row moves the window by half an hour; OK plays the channel.
 */
class GuideActivity : FragmentActivity() {
    private lateinit var list: RecyclerView
    private lateinit var adapter: RowAdapter
    private val timeFormat = DateFormat.getTimeInstance(DateFormat.SHORT)
    private var windowStart = GuideLayout.windowStart(System.currentTimeMillis())
    private var cellsWidth = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_guide)
        val density = resources.displayMetrics.density
        cellsWidth = resources.displayMetrics.widthPixels - (96 * density).toInt() -
            resources.getDimensionPixelSize(R.dimen.guide_channel_width)

        val favourites = app.channels.favourites.value
        val guide = app.epg.guide.value
        val channels = app.channels.channels.value
            .filter { !it.radio && !guide[it.key].isNullOrEmpty() }
            .sortedBy { if (it.key in favourites) 0 else 1 }
        findViewById<View>(R.id.guide_empty).visibility = if (channels.isEmpty()) View.VISIBLE else View.GONE

        adapter = RowAdapter(channels)
        list = findViewById(R.id.guide_list)
        list.layoutManager = LinearLayoutManager(this)
        list.adapter = adapter
        list.itemAnimator = null
        render()
        list.post { list.findViewHolderForAdapterPosition(0)?.let { focusCell(it as RowHolder, first = true) } }
    }

    private fun render() {
        findViewById<TextView>(R.id.guide_title).text =
            getString(R.string.guide_title, DateFormat.getDateInstance(DateFormat.FULL).format(Date(windowStart)))
        val header = findViewById<FrameLayout>(R.id.time_header)
        header.removeAllViews()
        for (i in 0 until (GuideLayout.WINDOW / GuideLayout.HALF_HOUR).toInt()) {
            header.addView(TextView(this).apply {
                text = timeFormat.format(Date(windowStart + i * GuideLayout.HALF_HOUR))
                setTextColor(ContextCompat.getColor(context, R.color.accent))
                textSize = 14f
            }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                leftMargin = (i * cellsWidth * GuideLayout.HALF_HOUR / GuideLayout.WINDOW).toInt()
            })
        }
        adapter.notifyItemRangeChanged(0, adapter.itemCount)
    }

    /** Moves the window when Left/Right would leave the row; otherwise normal focus movement. */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN &&
            (event.keyCode == KeyEvent.KEYCODE_DPAD_LEFT || event.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT)
        ) {
            val cell = currentFocus
            val tag = cell?.tag as? CellTag
            if (tag != null) {
                val left = event.keyCode == KeyEvent.KEYCODE_DPAD_LEFT
                val earliest = GuideLayout.windowStart(System.currentTimeMillis()) - GuideLayout.HALF_HOUR * 2
                if (left && tag.index == 0 && windowStart > earliest) return shift(-GuideLayout.HALF_HOUR, tag.row, first = true)
                if (!left && tag.index == tag.count - 1) return shift(GuideLayout.HALF_HOUR, tag.row, first = false)
                if ((left && tag.index == 0) || (!left && tag.index == tag.count - 1)) return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun shift(delta: Long, row: Int, first: Boolean): Boolean {
        windowStart += delta
        render()
        list.post { (list.findViewHolderForAdapterPosition(row) as? RowHolder)?.let { focusCell(it, first) } }
        return true
    }

    private fun focusCell(holder: RowHolder, first: Boolean) {
        val cells = holder.cells
        if (cells.childCount == 0) return
        cells.getChildAt(if (first) 0 else cells.childCount - 1).requestFocus()
    }

    private data class CellTag(val row: Int, val index: Int, val count: Int)

    private class RowHolder(view: View) : RecyclerView.ViewHolder(view) {
        val logo: ImageView = view.findViewById(R.id.logo)
        val name: TextView = view.findViewById(R.id.name)
        val cells: FrameLayout = view.findViewById(R.id.cells)
    }

    private inner class RowAdapter(val channels: List<Channel>) : RecyclerView.Adapter<RowHolder>() {
        override fun getItemCount() = channels.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            RowHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_guide_row, parent, false))

        override fun onBindViewHolder(holder: RowHolder, position: Int) {
            val channel = channels[position]
            holder.name.text = displayName(this@GuideActivity, channel)
            holder.logo.loadLogo(channel, 128, 72)
            val now = System.currentTimeMillis()
            val cells = GuideLayout.cells(app.epg.guide.value[channel.key].orEmpty(), windowStart, cellsWidth)
            // Reuse the row's cell views; only add or remove the difference.
            while (holder.cells.childCount > cells.size) holder.cells.removeViewAt(holder.cells.childCount - 1)
            while (holder.cells.childCount < cells.size) holder.cells.addView(newCell())
            cells.forEachIndexed { i, cell ->
                val view = holder.cells.getChildAt(i) as TextView
                val p = cell.programme
                // Short programmes get narrow cells: drop the time, then the text, rather than cram it.
                view.text = when {
                    p == null -> "—"
                    cell.width < NARROW / 2 -> ""
                    cell.width < NARROW -> p.title
                    else -> "${timeFormat.format(Date(maxOf(p.start, windowStart)))}  ${p.title}"
                }
                val airing = p != null && p.start <= now && now < p.stop
                view.setTextColor(ContextCompat.getColor(this@GuideActivity, if (airing) R.color.accent else R.color.text_primary))
                view.tag = CellTag(position, i, cells.size)
                view.layoutParams = FrameLayout.LayoutParams(maxOf(1, cell.width - GAP), ViewGroup.LayoutParams.MATCH_PARENT).apply {
                    leftMargin = cell.left
                }
                view.setOnClickListener {
                    PlayerActivity.start(this@GuideActivity, channel.key, channels.map { it.key })
                }
            }
        }

        private fun newCell() = TextView(this@GuideActivity).apply {
            setBackgroundResource(R.drawable.card_background)
            isFocusable = true
            gravity = Gravity.CENTER_VERTICAL
            setPadding(12, 0, 12, 0)
            textSize = 14f
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
        }
    }

    companion object {
        private const val GAP = 4
        /** Cells narrower than this (px, about 20 minutes) show the title only. */
        private const val NARROW = 220
    }
}
