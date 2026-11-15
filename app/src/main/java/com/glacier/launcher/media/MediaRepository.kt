package com.glacier.launcher.media

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Now-playing info from the system's active media sessions. Requires Notification Access (opt-in). */
class MediaRepository(private val ctx: Context) {
    data class Info(val title: String, val artist: String, val playing: Boolean, val art: Bitmap?)

    private val _info = MutableStateFlow<Info?>(null)
    val info: StateFlow<Info?> = _info.asStateFlow()
    private val _hasAccess = MutableStateFlow(false)
    val hasAccess: StateFlow<Boolean> = _hasAccess.asStateFlow()

    private val msm = ctx.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
    private val listenerName = ComponentName(ctx, GlacierNotificationListener::class.java)
    private var controller: MediaController? = null
    private var registered = false

    private val controllerCb = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) = publish()
        override fun onMetadataChanged(metadata: MediaMetadata?) = publish()
        override fun onSessionDestroyed() = refresh()
    }
    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { pick(it) }

    fun refresh() {
        val enabled = NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)
        _hasAccess.value = enabled
        if (!enabled) {
            controller?.unregisterCallback(controllerCb)
            controller = null
            _info.value = null
            registered = false
            return
        }
        try {
            if (!registered) {
                msm.addOnActiveSessionsChangedListener(sessionsListener, listenerName)
                registered = true
            }
            pick(msm.getActiveSessions(listenerName))
        } catch (e: SecurityException) {
            _hasAccess.value = false
        }
    }

    private fun pick(list: List<MediaController>?) {
        val best = list?.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING } ?: list?.firstOrNull()
        if (best?.sessionToken != controller?.sessionToken) {
            controller?.unregisterCallback(controllerCb)
            controller = best
            best?.registerCallback(controllerCb)
        }
        publish()
    }

    private fun publish() {
        val c = controller
        if (c == null) { _info.value = null; return }
        val m = c.metadata
        val title = m?.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty()
        if (title.isBlank()) { _info.value = null; return }
        _info.value = Info(
            title = title,
            artist = m?.getString(MediaMetadata.METADATA_KEY_ARTIST).orEmpty(),
            playing = c.playbackState?.state == PlaybackState.STATE_PLAYING,
            art = m?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) ?: m?.getBitmap(MediaMetadata.METADATA_KEY_ART)
        )
    }

    fun playPause() {
        val c = controller ?: return
        if (c.playbackState?.state == PlaybackState.STATE_PLAYING) c.transportControls.pause() else c.transportControls.play()
    }
    fun next() { controller?.transportControls?.skipToNext() }
    fun previous() { controller?.transportControls?.skipToPrevious() }
}
