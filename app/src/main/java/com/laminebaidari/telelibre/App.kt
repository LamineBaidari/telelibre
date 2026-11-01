package com.laminebaidari.telelibre

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.memory.MemoryCache
import com.laminebaidari.telelibre.data.ChannelRepository
import com.laminebaidari.telelibre.data.YouTubeRepository
import com.laminebaidari.telelibre.epg.EpgRepository
import com.laminebaidari.telelibre.home.HomeChannel
import com.laminebaidari.telelibre.update.Updater
import com.laminebaidari.telelibre.work.Refresh
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class App : Application(), ImageLoaderFactory {
    lateinit var http: OkHttpClient
        private set
    lateinit var channels: ChannelRepository
        private set
    lateinit var epg: EpgRepository
        private set
    lateinit var youtube: YouTubeRepository
        private set
    lateinit var updater: Updater
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        http = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
        channels = ChannelRepository(this)
        epg = EpgRepository(this, http)
        youtube = YouTubeRepository(http)
        updater = Updater(this, http)
        Refresh.schedulePeriodic(this)
        syncHomeChannel()
    }

    /** Keeps the favourites row on the TV home screen in step with favourites and the guide. */
    @OptIn(FlowPreview::class)
    private fun syncHomeChannel() {
        val home = HomeChannel(this)
        scope.launch {
            combine(channels.favourites, channels.channels, epg.guide) { _, _, _ -> }
                .debounce(2_000)
                .collect { home.sync(channels.favouriteChannels()) { key -> epg.nowNext(key).first } }
        }
    }

    // Small memory cache and 16-bit bitmaps: this TV has little free RAM.
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .okHttpClient(http)
        .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.15).build() }
        .allowRgb565(true)
        .crossfade(false)
        .build()
}
