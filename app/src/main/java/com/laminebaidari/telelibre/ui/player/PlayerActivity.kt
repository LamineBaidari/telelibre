package com.laminebaidari.telelibre.ui.player

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.annotation.OptIn
import androidx.fragment.app.FragmentActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.PlayerView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.laminebaidari.telelibre.R
import com.laminebaidari.telelibre.data.StreamChecker
import com.laminebaidari.telelibre.model.Channel
import com.laminebaidari.telelibre.ui.app
import com.laminebaidari.telelibre.ui.guide.GuideActivity
import com.laminebaidari.telelibre.ui.displayName
import com.laminebaidari.telelibre.ui.label
import com.laminebaidari.telelibre.ui.loadLogo
import com.laminebaidari.telelibre.ui.toggleFavourite

/**
 * Full-screen live TV or radio. Up/Down or CH+/CH- zap through the list the channel was opened from,
 * OK shows the quick list, ☰ switches to the favourites, the bookmark key toggles favourite,
 * Back closes overlays then returns to the rows.
 */
@OptIn(UnstableApi::class)
class PlayerActivity : FragmentActivity() {
    private lateinit var playerView: PlayerView
    private lateinit var status: TextView
    private lateinit var banner: View
    private lateinit var quickPanel: View
    private lateinit var quickList: RecyclerView
    private lateinit var quickAdapter: QuickListAdapter
    private lateinit var radioPanel: View
    private lateinit var radioSong: TextView

    private var player: ExoPlayer? = null
    private var channels: List<Channel> = emptyList()
    private var index = 0
    private var retried = false
    private val handler = Handler(Looper.getMainLooper())

    private val hideBanner = Runnable { banner.visibility = View.GONE }
    private val tuneNow = Runnable { startPlayback() }
    private val stalled = Runnable { onPlaybackFailed() }
    private val skipDead = Runnable { tune(index) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        playerView = findViewById(R.id.player_view)
        status = findViewById(R.id.status)
        banner = findViewById(R.id.banner)
        quickPanel = findViewById(R.id.quick_panel)
        quickList = findViewById(R.id.quick_list)
        radioPanel = findViewById(R.id.radio_panel)
        radioSong = findViewById(R.id.radio_song)

        if (!load(intent)) {
            finish()
            return
        }

        quickAdapter = QuickListAdapter(
            onClick = { position ->
                hideQuickList()
                if (position != index) tune(position)
            },
            onLongClick = { position ->
                toggleFavourite(this, channels[position])
                quickAdapter.notifyItemChanged(position)
            },
        )
        quickList.layoutManager = LinearLayoutManager(this)
        quickList.adapter = quickAdapter

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    quickPanel.visibility == View.VISIBLE -> hideQuickList()
                    banner.visibility == View.VISIBLE -> banner.visibility = View.GONE
                    else -> finish()
                }
            }
        })
    }

    /** A home-screen tile can open another channel while this screen is already up. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (load(intent) && player != null) tune(index)
    }

    /**
     * Reads the channel and the list to zap through: from [start], or from a home-screen tile
     * (telelibre://play?key=…), which zaps through the favourites.
     */
    private fun load(intent: Intent): Boolean {
        val repo = app.channels
        val key: String?
        val keys: List<String>
        val data = intent.data
        if (data?.scheme == "telelibre" && data.host == "play") {
            key = data.getQueryParameter("key")
            val channel = key?.let(repo::find) ?: return false
            keys = repo.favouriteChannels().map { it.key }.takeIf { key in it } ?: repo.inGroup(channel.group).map { it.key }
        } else {
            key = intent.getStringExtra(EXTRA_KEY)
            keys = intent.getStringArrayListExtra(EXTRA_LIST).orEmpty()
        }
        val found = keys.mapNotNull(repo::find)
        if (found.isEmpty()) return false
        channels = found
        index = channels.indexOfFirst { it.key == key }.coerceAtLeast(0)
        return true
    }

    override fun onStart() {
        super.onStart()
        if (channels.isEmpty()) return
        createPlayer()
        tune(index)
    }

    // Release the decoder when hidden: the TV's own apps need it back.
    override fun onStop() {
        super.onStop()
        handler.removeCallbacksAndMessages(null)
        player?.release()
        player = null
    }

    private fun createPlayer() {
        val renderers = DefaultRenderersFactory(this).setEnableDecoderFallback(true)
        val selector = DefaultTrackSelector(this).apply {
            setParameters(buildUponParameters().setMaxVideoSize(1920, 1080))
        }
        player = ExoPlayer.Builder(this, renderers).setTrackSelector(selector).build().apply {
            playWhenReady = true
            addListener(listener)
        }
        playerView.player = player
    }

    /** Shows the banner at once; the stream starts after a short pause so fast zapping stays smooth. */
    private fun tune(target: Int) {
        if (channels.isEmpty()) return finish()
        index = Math.floorMod(target, channels.size)
        retried = false
        app.channels.lastChannelKey = channels[index].key
        showBanner()
        handler.removeCallbacks(skipDead)
        handler.removeCallbacks(tuneNow)
        handler.postDelayed(tuneNow, 350)
    }

    private fun startPlayback() {
        val player = player ?: return
        val channel = channels[index]
        showStatus(getString(if (retried) R.string.player_retrying else R.string.player_loading))
        val http = OkHttpDataSource.Factory(app.http)
            .setUserAgent(channel.userAgent ?: StreamChecker.DEFAULT_USER_AGENT)
            .apply { channel.referrer?.let { setDefaultRequestProperties(mapOf("Referer" to it)) } }
        showRadioPanel(channel)
        val item = MediaItem.fromUri(channel.url)
        // Many playlist URLs (e.g. jmp2.uk redirects) don't end in .m3u8, so say it's HLS explicitly.
        // Radio is a plain MP3/AAC stream; Media3 asks it for song titles (ICY metadata) on its own.
        val source = if (channel.radio) ProgressiveMediaSource.Factory(http).createMediaSource(item)
        else HlsMediaSource.Factory(http).createMediaSource(item)
        player.setMediaSource(source)
        player.prepare()
        watchForStall()
    }

    private val listener = object : Player.Listener {
        override fun onPlaybackStateChanged(state: Int) {
            when (state) {
                Player.STATE_READY -> {
                    handler.removeCallbacks(stalled)
                    status.visibility = View.GONE
                }
                Player.STATE_BUFFERING -> watchForStall()
            }
        }

        override fun onMediaMetadataChanged(metadata: MediaMetadata) {
            // Some stations send " - " when they have no song title: show nothing then.
            val title = metadata.title?.toString().orEmpty()
            if (channels.getOrNull(index)?.radio == true) radioSong.text = if (title.any { it.isLetter() }) title else ""
        }

        override fun onPlayerError(error: PlaybackException) {
            if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
                player?.seekToDefaultPosition()
                player?.prepare()
                return
            }
            onPlaybackFailed()
        }
    }

    private fun watchForStall() {
        handler.removeCallbacks(stalled)
        handler.postDelayed(stalled, STALL_TIMEOUT_MS)
    }

    /** First failure: retry from the original URL. Second: hide the channel until the next refresh. */
    private fun onPlaybackFailed() {
        handler.removeCallbacks(stalled)
        if (channels.isEmpty()) return
        if (!retried) {
            retried = true
            startPlayback()
            return
        }
        val dead = channels[index]
        app.channels.markDead(dead.key)
        player?.stop()
        showStatus(getString(R.string.player_dead))
        channels = channels - dead
        if (channels.isEmpty()) {
            handler.postDelayed({ finish() }, SKIP_DELAY_MS)
            return
        }
        // The next channel slid into this index.
        index = Math.floorMod(index, channels.size)
        handler.postDelayed(skipDead, SKIP_DELAY_MS)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (quickPanel.visibility == View.VISIBLE) return super.onKeyDown(keyCode, event)
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_CHANNEL_UP, KeyEvent.KEYCODE_PAGE_UP -> tune(index + 1)
            KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_CHANNEL_DOWN, KeyEvent.KEYCODE_PAGE_DOWN -> tune(index - 1)
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> showQuickList()
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_INFO -> showBanner()
            KeyEvent.KEYCODE_MENU -> showFavourites()
            KeyEvent.KEYCODE_BOOKMARK -> {
                toggleFavourite(this, channels[index])
                showBanner()
            }
            KeyEvent.KEYCODE_GUIDE -> startActivity(Intent(this, GuideActivity::class.java))
            else -> return super.onKeyDown(keyCode, event)
        }
        return true
    }

    private fun showBanner() {
        val channel = channels[index]
        findViewById<ImageView>(R.id.banner_logo).loadLogo(channel, 320, 180)
        findViewById<TextView>(R.id.banner_name).text = displayName(this, channel)
        findViewById<TextView>(R.id.banner_position).text =
            getString(R.string.player_position, index + 1, channels.size, channel.group)
        val (now, next) = app.epg.nowNext(channel.key)
        findViewById<TextView>(R.id.banner_now).apply {
            text = now?.let { getString(R.string.player_now, it.label()) }.orEmpty()
            visibility = if (now == null) View.GONE else View.VISIBLE
        }
        findViewById<TextView>(R.id.banner_next).apply {
            text = next?.let { getString(R.string.player_next, it.label()) }.orEmpty()
            visibility = if (next == null) View.GONE else View.VISIBLE
        }
        banner.visibility = View.VISIBLE
        handler.removeCallbacks(hideBanner)
        handler.postDelayed(hideBanner, BANNER_MS)
    }

    /** ☰: zap through the favourites from now on, starting from the quick list. */
    private fun showFavourites() {
        val favourites = app.channels.favouriteChannels()
        if (favourites.isEmpty()) {
            Toast.makeText(this, R.string.no_favourites, Toast.LENGTH_SHORT).show()
            return
        }
        val current = channels[index]
        channels = favourites
        index = favourites.indexOfFirst { it.key == current.key }.coerceAtLeast(0)
        showQuickList()
        findViewById<TextView>(R.id.quick_title).setText(R.string.row_favourites)
    }

    private fun showRadioPanel(channel: Channel) {
        radioPanel.visibility = if (channel.radio) View.VISIBLE else View.GONE
        if (!channel.radio) return
        findViewById<ImageView>(R.id.radio_logo).loadLogo(channel, 640, 400)
        findViewById<TextView>(R.id.radio_name).text = channel.name
        radioSong.text = ""
    }

    private fun showQuickList() {
        banner.visibility = View.GONE
        findViewById<TextView>(R.id.quick_title).text = channels[index].group
        quickAdapter.channels = channels
        quickAdapter.notifyDataSetChanged()
        quickPanel.visibility = View.VISIBLE
        (quickList.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(index, 200)
        quickList.post { quickList.findViewHolderForAdapterPosition(index)?.itemView?.requestFocus() }
    }

    private fun hideQuickList() {
        quickPanel.visibility = View.GONE
        quickList.clearFocus()
    }

    private fun showStatus(text: String) {
        status.text = text
        status.visibility = View.VISIBLE
    }

    companion object {
        private const val EXTRA_KEY = "key"
        private const val EXTRA_LIST = "list"
        private const val BANNER_MS = 4_000L
        private const val STALL_TIMEOUT_MS = 15_000L
        private const val SKIP_DELAY_MS = 5_000L

        fun start(context: Context, key: String, list: List<String>) {
            context.startActivity(
                Intent(context, PlayerActivity::class.java)
                    .putExtra(EXTRA_KEY, key)
                    .putStringArrayListExtra(EXTRA_LIST, ArrayList(list))
            )
        }
    }
}
