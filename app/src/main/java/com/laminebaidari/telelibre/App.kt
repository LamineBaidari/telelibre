package com.laminebaidari.telelibre

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.memory.MemoryCache
import com.laminebaidari.telelibre.data.ChannelRepository
import com.laminebaidari.telelibre.epg.EpgRepository
import com.laminebaidari.telelibre.work.Refresh
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class App : Application(), ImageLoaderFactory {
    lateinit var http: OkHttpClient
        private set
    lateinit var channels: ChannelRepository
        private set
    lateinit var epg: EpgRepository
        private set

    override fun onCreate() {
        super.onCreate()
        http = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
        channels = ChannelRepository(this)
        epg = EpgRepository(this, http)
        Refresh.schedulePeriodic(this)
    }

    // Small memory cache and 16-bit bitmaps: this TV has little free RAM.
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .okHttpClient(http)
        .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.15).build() }
        .allowRgb565(true)
        .crossfade(false)
        .build()
}
