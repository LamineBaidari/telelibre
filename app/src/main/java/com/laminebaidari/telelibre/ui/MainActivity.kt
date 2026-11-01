package com.laminebaidari.telelibre.ui

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.widget.TextView
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.laminebaidari.telelibre.R
import com.laminebaidari.telelibre.ui.browse.BrowseFragment
import com.laminebaidari.telelibre.ui.guide.GuideActivity
import com.laminebaidari.telelibre.ui.player.PlayerActivity
import com.laminebaidari.telelibre.work.Refresh
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction().replace(R.id.browse_container, BrowseFragment()).commit()
            resumeLastChannel()
        }
        val status = findViewById<TextView>(R.id.status)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                app.channels.channels.collect { list ->
                    status.visibility = if (list.none { !it.radio }) View.VISIBLE else View.GONE
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        Refresh.refreshIfStale(this)
        app.youtube.refreshIfStale()
    }

    /** TV key: back to the last channel. Guide key, if the remote has one: the programme grid. */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_TV -> resumeLastChannel()
            KeyEvent.KEYCODE_GUIDE -> startActivity(Intent(this, GuideActivity::class.java))
            else -> return super.onKeyDown(keyCode, event)
        }
        return true
    }

    /** Opening the app goes straight back to what was playing; Back shows the channel rows. */
    private fun resumeLastChannel() {
        val repo = app.channels
        val last = repo.lastChannelKey?.let(repo::find) ?: return
        PlayerActivity.start(this, last.key, repo.inGroup(last.group).map { it.key })
    }
}
