package com.riftartillery.game

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.riftartillery.core.Arena

/** One asynchronously prepared, streamed loop; all state changes run on the main thread. */
internal class ArenaMusic(private val context:Context) {
    private val manager=context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val attributes=AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
    private val focusRequest=AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(attributes).setOnAudioFocusChangeListener(::onFocusChange,Handler(Looper.getMainLooper())).build()
    private var player:MediaPlayer?=null
    private var prepared=false
    private var enabled=false
    private var active=false
    private var focusHeld=false
    private var interrupted=false
    private var ducked=false
    private var released=false
    var currentArena:Arena?=null
        private set
    var lastError:String?=null
        private set
    val isPlaying get()=prepared && player?.isPlaying==true
    val position get()=if(prepared) player?.currentPosition ?: 0 else 0
    val looping get()=prepared && player?.isLooping==true
    private val noisy=object:BroadcastReceiver() {
        override fun onReceive(context:Context,intent:Intent) {
            if(intent.action==AudioManager.ACTION_AUDIO_BECOMING_NOISY) onFocusChange(AudioManager.AUDIOFOCUS_LOSS)
        }
    }
    init {
        val filter=IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        if(Build.VERSION.SDK_INT>=33) context.registerReceiver(noisy,filter,Context.RECEIVER_NOT_EXPORTED)
        else @Suppress("DEPRECATION") context.registerReceiver(noisy,filter)
    }
    fun sync(arena:Arena,enabled:Boolean,active:Boolean) {
        if(released || currentArena==arena && this.enabled==enabled && this.active==active) return
        val changeTrack=currentArena!=arena
        if(changeTrack || this.enabled!=enabled || this.active!=active) interrupted=false
        currentArena=arena;this.enabled=enabled;this.active=active
        if(changeTrack) disposePlayer()
        if(!enabled || !active) {pause();abandonFocus();return}
        if(player==null) prepare(arena)
        applyPlayback()
    }
    private fun prepare(arena:Arena) {
        lastError=null
        val next=MediaPlayer();player=next
        try {
            next.setAudioAttributes(attributes)
            next.isLooping=true
            next.setVolume(.22f,.22f)
            next.setOnPreparedListener {mp->if(player===mp && !released) {prepared=true;applyPlayback()}}
            next.setOnErrorListener {mp,what,extra->
                if(player===mp) fail("MediaPlayer $what/$extra")
                true
            }
            context.assets.openFd("music/${arena.name.lowercase(java.util.Locale.ROOT)}.ogg").use {
                next.setDataSource(it.fileDescriptor,it.startOffset,it.length)
            }
            next.prepareAsync()
        } catch(e:Exception) {fail(e.javaClass.simpleName+": "+e.message)}
    }
    private fun applyPlayback() {
        val mp=player ?: return
        if(!prepared) return
        if(!active || !enabled || interrupted) {pause();return}
        if(!focusHeld) {
            focusHeld=manager.requestAudioFocus(focusRequest)==AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            if(!focusHeld) {interrupted=true;return}
        }
        mp.setVolume(if(ducked) .055f else .22f,if(ducked) .055f else .22f)
        try {if(!mp.isPlaying) mp.start()} catch(e:IllegalStateException) {fail("Playback: "+e.message)}
    }
    internal fun onFocusChange(change:Int) {
        if(released) return
        when(change) {
            AudioManager.AUDIOFOCUS_GAIN->{if(!active || !enabled) {abandonFocus();return};focusHeld=true;interrupted=false;ducked=false;applyPlayback()}
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK->{ducked=true;if(prepared) player?.setVolume(.055f,.055f)}
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT->{focusHeld=false;interrupted=true;pause()}
            AudioManager.AUDIOFOCUS_LOSS->{interrupted=true;pause();abandonFocus()}
        }
    }
    private fun pause() {if(prepared && player?.isPlaying==true) player?.pause()}
    private fun abandonFocus() {manager.abandonAudioFocusRequest(focusRequest);focusHeld=false;ducked=false}
    private fun fail(message:String) {lastError=message;Log.w("RiftMusic",message);disposePlayer();abandonFocus()}
    private fun disposePlayer() {
        prepared=false
        player?.apply {setOnPreparedListener(null);setOnErrorListener(null);release()}
        player=null
    }
    fun release() {
        if(released) return
        released=true;disposePlayer();abandonFocus();context.unregisterReceiver(noisy)
    }
}
