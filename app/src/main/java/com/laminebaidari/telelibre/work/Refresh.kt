package com.laminebaidari.telelibre.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.laminebaidari.telelibre.App
import com.laminebaidari.telelibre.data.ChannelRefresher
import java.util.concurrent.TimeUnit

/** Rebuilds the channel list, then refreshes the guide for it. */
class RefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as App
        val channels = ChannelRefresher(app.http).refresh()
        // Keep the previous list rather than replacing it with nothing (no network, source down…).
        if (channels.isEmpty()) return Result.retry()
        app.channels.replaceAll(channels)
        // Separate job: each worker run is capped at 10 minutes and the guide download is slow.
        Refresh.refreshGuide(applicationContext)
        return Result.success()
    }
}

/** Refreshes only the guide, which goes stale faster than the channel list. */
class EpgWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as App
        val channels = app.channels.channels.value
        if (channels.isNotEmpty()) app.epg.refresh(channels)
        return Result.success()
    }
}

object Refresh {
    private const val DAILY = "refresh-daily"
    private const val GUIDE = "guide-periodic"
    const val NOW = "refresh-now"
    private const val GUIDE_NOW = "guide-now"
    private const val DAY = 24 * 3_600_000L
    private const val GUIDE_MAX_AGE = 6 * 3_600_000L

    private val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    fun schedulePeriodic(context: Context) {
        val wm = WorkManager.getInstance(context)
        wm.enqueueUniquePeriodicWork(
            DAILY, ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<RefreshWorker>(24, TimeUnit.HOURS).setConstraints(network).build(),
        )
        wm.enqueueUniquePeriodicWork(
            GUIDE, ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<EpgWorker>(12, TimeUnit.HOURS).setConstraints(network).build(),
        )
    }

    /** The TV is often switched off, so periodic work may not have run: catch up when the app opens. */
    fun refreshIfStale(context: Context) {
        val app = context.applicationContext as App
        val now = System.currentTimeMillis()
        val wm = WorkManager.getInstance(context)
        if (now - app.channels.lastRefresh > DAY || app.channels.channels.value.isEmpty()) {
            wm.enqueueUniqueWork(
                NOW, ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<RefreshWorker>().setConstraints(network).build(),
            )
        } else if (now - app.epg.lastRefresh > GUIDE_MAX_AGE) {
            refreshGuide(context)
        }
    }

    fun refreshGuide(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            GUIDE_NOW, ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<EpgWorker>().setConstraints(network).build(),
        )
    }
}
