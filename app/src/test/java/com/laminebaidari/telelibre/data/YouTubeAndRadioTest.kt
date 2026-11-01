package com.laminebaidari.telelibre.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class YouTubeAndRadioTest {
    @Test
    fun readsLiveStatusFromTheLivePage() {
        val live = """<link rel="canonical" href="https://www.youtube.com/watch?v=uK-LXagNYWE"><meta name="x">"""
        val notLive = """<link rel="canonical" href="https://www.youtube.com/channel/UC5NQ49FVRIAuWE1el6L2gkg">""" +
            """<meta property="og:image" content="https://yt3.googleusercontent.com/abc=s900-c-k?a=1&amp;b=2">"""
        assertEquals("uK-LXagNYWE", YouTubeRepository.parseLiveVideoId(live))
        assertNull(YouTubeRepository.parseLiveVideoId(notLive))
        assertEquals("https://yt3.googleusercontent.com/abc=s900-c-k?a=1&b=2", YouTubeRepository.parseImage(notLive))
    }

    @Test
    fun readsNewestVideoFromTheFeed() {
        val feed = """<?xml version="1.0"?><feed><title>TFM</title>
            <entry><id>yt:video:AAA</id><yt:videoId>abcdefghijk</yt:videoId><title>Xibaar Yi 13H &amp; météo</title></entry>
            <entry><yt:videoId>zzzzzzzzzzz</yt:videoId><title>Older</title></entry></feed>"""
        assertEquals("abcdefghijk" to "Xibaar Yi 13H & météo", YouTubeRepository.parseLatest(feed))
        assertNull(YouTubeRepository.parseLatest("<feed><title>Empty</title></feed>"))
    }

    @Test
    fun keepsPlayableRadioStations() {
        val json = """[
            {"stationuuid":"1","name":"Sud FM","url_resolved":"https://stream.zeno.fm/a","codec":"MP3","lastcheckok":1,"favicon":"https://x/sud.png"},
            {"stationuuid":"2","name":"Playlist FM","url_resolved":"https://x.example/radio.m3u8","codec":"UNKNOWN","lastcheckok":1,"favicon":""},
            {"stationuuid":"3","name":"Broken FM","url_resolved":"https://x.example/b.mp3","codec":"MP3","lastcheckok":0,"favicon":""},
            {"stationuuid":"4","name":"Relay FM","url_resolved":"http://51.1.2.3:8000/live","codec":"AAC","lastcheckok":1,"favicon":""},
            {"stationuuid":"5","name":"SUD FM ","url_resolved":"https://other.example/sud.aac","codec":"AAC","lastcheckok":1,"favicon":""}
        ]"""
        val stations = RadioRefresher.parseStations(json)
        assertEquals(listOf("Sud FM", "SUD FM"), stations.map { it.name })
        val first = stations.first()
        assertTrue(first.radio)
        assertEquals("radio:1", first.key)
        assertEquals(Sources.GROUP_RADIO, first.group)
        assertEquals("https://x/sud.png", first.logo)
        // Same name twice: the first (most popular) one wins.
        assertEquals(listOf("radio:1"), RadioRefresher.merge(stations).map { it.key })
        assertFalse(RadioRefresher.FALLBACK.isEmpty())
        assertTrue(RadioRefresher.FALLBACK.all { it.radio && LegalityFilter.allowed(it.url) })
    }
}
