package com.github.libretube.services

import android.app.Notification
import android.content.Intent
import android.os.Binder
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.net.toUri
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.github.libretube.R
import com.github.libretube.api.JsonHelper
import com.github.libretube.api.RetrofitInstance
import com.github.libretube.api.obj.Segment
import com.github.libretube.api.obj.Streams
import com.github.libretube.constants.BACKGROUND_CHANNEL_ID
import com.github.libretube.constants.IntentData
import com.github.libretube.constants.PLAYER_NOTIFICATION_ID
import com.github.libretube.db.DatabaseHolder.Database
import com.github.libretube.db.obj.WatchPosition
import com.github.libretube.enums.SbSkipOptions
import com.github.libretube.extensions.parcelableExtra
import com.github.libretube.extensions.setMetadata
import com.github.libretube.extensions.toID
import com.github.libretube.helpers.PlayerHelper
import com.github.libretube.helpers.PlayerHelper.checkForSegments
import com.github.libretube.helpers.PlayerHelper.loadPlaybackParams
import com.github.libretube.helpers.ProxyHelper
import com.github.libretube.obj.PlayerNotificationData
import com.github.libretube.parcelable.PlayerData
import com.github.libretube.util.NowPlayingNotification
import com.github.libretube.util.PlayingQueue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString

/**
 * Loads the selected videos audio in background mode with a notification area.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class OnlinePlayerService : LifecycleService() {
    /**
     * VideoId of the video
     */
    private lateinit var videoId: String

    /**
     * PlaylistId/ChannelId for autoplay
     */
    private var playlistId: String? = null
    private var channelId: String? = null

    /**
     * The response that gets when called the Api.
     */
    var streams: Streams? = null
        private set

    /**
     * The [ExoPlayer] player. Followed tutorial [here](https://developer.android.com/codelabs/exoplayer-intro)
     */
    var player: ExoPlayer? = null
    private var playWhenReadyPlayer = true

    /**
     * SponsorBlock Segment data
     */
    private var segments: List<Segment> = listOf()
    private var sponsorBlockConfig: MutableMap<String, SbSkipOptions> =
        PlayerHelper.getSponsorBlockCategories()

    /**
     * [Notification] for the player
     */
    private lateinit var nowPlayingNotification: NowPlayingNotification

    /**
     * Autoplay Preference
     */
    private val handler = Handler(Looper.getMainLooper())

    /**
     * Used for connecting to the AudioPlayerFragment
     */
    private val binder = LocalBinder()

    /**
     * Listener for passing playback state changes to the AudioPlayerFragment
     */
    var onIsPlayingChanged: ((isPlaying: Boolean) -> Unit)? = null
    var onNewVideo: ((streams: Streams, videoId: String) -> Unit)? = null

    /**
     * Setting the required [Notification] for running as a foreground service
     */
    override fun onCreate() {
        super.onCreate()

        val notification = NotificationCompat.Builder(this, BACKGROUND_CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.playingOnBackground))
            .setSmallIcon(R.drawable.ic_launcher_lockscreen)
            .build()

        startForeground(PLAYER_NOTIFICATION_ID, notification)
    }

    /**
     * Initializes the [player] with the [MediaItem].
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // reset the playing queue listeners
        PlayingQueue.resetToDefaults()

        intent?.parcelableExtra<PlayerData>(IntentData.playerData)?.let { playerData ->
            // get the intent arguments
            videoId = playerData.videoId
            playlistId = playerData.playlistId

            // play the audio in the background
            loadAudio(playerData)

            PlayingQueue.setOnQueueTapListener { streamItem ->
                streamItem.url?.toID()?.let { playNextVideo(it) }
            }

            if (PlayerHelper.watchPositionsAudio) {
                updateWatchPosition()
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun updateWatchPosition() {
        player?.currentPosition?.let {
            val watchPosition = WatchPosition(videoId, it)

            // indicator that a new video is getting loaded
            this.streams ?: return@let

            CoroutineScope(Dispatchers.IO).launch {
                Database.watchPositionDao().insert(watchPosition)
            }
        }
        handler.postDelayed(this::updateWatchPosition, 500)
    }

    /**
     * Gets the video data and prepares the [player].
     */
    private fun loadAudio(playerData: PlayerData) {
        val (videoId, _, _, keepQueue, timestamp) = playerData

        lifecycleScope.launch(Dispatchers.IO) {
            streams = runCatching {
                RetrofitInstance.api.getStreams(videoId)
            }.getOrNull() ?: return@launch

            // clear the queue if it shouldn't be kept explicitly
            if (!keepQueue) PlayingQueue.clear()

            if (PlayingQueue.isEmpty()) updateQueue()

            // save the current stream to the queue
            streams?.toStreamItem(videoId)?.let {
                PlayingQueue.updateCurrent(it)
            }

            withContext(Dispatchers.Main) {
                playAudio(timestamp)
            }
        }
    }

    private fun playAudio(seekToPosition: Long) {
        initializePlayer()
        setMediaItem()

        // create the notification
        if (!this@OnlinePlayerService::nowPlayingNotification.isInitialized) {
            nowPlayingNotification = NowPlayingNotification(
                this@OnlinePlayerService,
                player!!,
                true
            )
        }
        val playerNotificationData = PlayerNotificationData(
            streams?.title,
            streams?.uploader,
            streams?.thumbnailUrl
        )
        nowPlayingNotification.updatePlayerNotification(videoId, playerNotificationData)
        streams?.let { onNewVideo?.invoke(it, videoId) }

        player?.apply {
            playWhenReady = playWhenReadyPlayer
            prepare()
        }

        // seek to the previous position if available
        if (seekToPosition != 0L) {
            player?.seekTo(seekToPosition)
        } else if (PlayerHelper.watchPositionsAudio) {
            runCatching {
                val watchPosition = runBlocking {
                    Database.watchPositionDao().findById(videoId)
                }
                streams?.duration?.let {
                    if (watchPosition != null && watchPosition.position < it * 1000 * 0.9) {
                        player?.seekTo(watchPosition.position)
                    }
                }
            }
        }

        if (PlayerHelper.sponsorBlockEnabled) fetchSponsorBlockSegments()
    }

    /**
     * create the player
     */
    private fun initializePlayer() {
        if (player != null) return

        player = ExoPlayer.Builder(this)
            .setUsePlatformDiagnostics(false)
            .setHandleAudioBecomingNoisy(true)
            .setAudioAttributes(PlayerHelper.getAudioAttributes(), true)
            .setLoadControl(PlayerHelper.getLoadControl())
            .build()
            .loadPlaybackParams(isBackgroundMode = true)

        /**
         * Listens for changed playbackStates (e.g. pause, end)
         * Plays the next video when the current one ended
         */
        player?.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                super.onIsPlayingChanged(isPlaying)
                onIsPlayingChanged?.invoke(isPlaying)
            }

            override fun onPlaybackStateChanged(state: Int) {
                when (state) {
                    Player.STATE_ENDED -> {
                        if (PlayerHelper.autoPlayEnabled) playNextVideo()
                    }

                    Player.STATE_IDLE -> {
                        onDestroy()
                    }

                    Player.STATE_BUFFERING -> {}
                    Player.STATE_READY -> {}
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                // show a toast on errors
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(
                        this@OnlinePlayerService.applicationContext,
                        error.localizedMessage,
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        })
    }

    /**
     * Plays the next video from the queue
     */
    private fun playNextVideo(nextId: String? = null) {
        val nextVideo = nextId ?: PlayingQueue.getNext() ?: return

        // play new video on background
        this.videoId = nextVideo
        this.streams = null
        this.segments = emptyList()
        loadAudio(PlayerData(videoId, keepQueue = true))
    }

    /**
     * Sets the [MediaItem] with the [streams] into the [player]
     */
    private fun setMediaItem() {
        val streams = streams ?: return

        val (uri, mimeType) = if (streams.audioStreams.isNotEmpty()) {
            PlayerHelper.createDashSource(streams, this, true) to MimeTypes.APPLICATION_MPD
        } else {
            ProxyHelper.rewriteUrl(streams.hls)?.toUri() to MimeTypes.APPLICATION_M3U8
        }

        val mediaItem = MediaItem.Builder()
            .setUri(uri)
            .setMimeType(mimeType)
            .setMetadata(streams)
            .build()
        player?.setMediaItem(mediaItem)
    }

    /**
     * fetch the segments for SponsorBlock
     */
    private fun fetchSponsorBlockSegments() {
        lifecycleScope.launch(Dispatchers.IO) {
            runCatching {
                if (sponsorBlockConfig.isEmpty()) return@runCatching
                segments = RetrofitInstance.api.getSegments(
                    videoId,
                    JsonHelper.json.encodeToString(sponsorBlockConfig.keys)
                ).segments
                checkForSegments()
            }
        }
    }

    /**
     * check for SponsorBlock segments
     */
    private fun checkForSegments() {
        handler.postDelayed(this::checkForSegments, 100)

        player?.checkForSegments(this, segments, sponsorBlockConfig)
    }

    private fun updateQueue() {
        if (playlistId != null) {
            streams?.toStreamItem(videoId)?.let {
                PlayingQueue.insertPlaylist(playlistId!!, it)
            }
        } else if (channelId != null) {
            streams?.toStreamItem(videoId)?.let {
                PlayingQueue.insertChannel(channelId!!, it)
            }
        } else {
            streams?.relatedStreams?.toTypedArray()?.let {
                if (PlayerHelper.autoInsertRelatedVideos) PlayingQueue.add(*it)
            }
        }
    }

    /**
     * Stop the service when app is removed from the task manager.
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        onDestroy()
    }

    /**
     * destroy the [OnlinePlayerService] foreground service
     */
    override fun onDestroy() {
        // reset the playing queue
        PlayingQueue.resetToDefaults()

        if (this::nowPlayingNotification.isInitialized) nowPlayingNotification.destroySelfAndPlayer()

        // called when the user pressed stop in the notification
        // stop the service from being in the foreground and remove the notification
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        // destroy the service
        stopSelf()

        super.onDestroy()
    }

    inner class LocalBinder : Binder() {
        // Return this instance of [BackgroundMode] so clients can call public methods
        fun getService(): OnlinePlayerService = this@OnlinePlayerService
    }

    override fun onBind(intent: Intent): IBinder {
        super.onBind(intent)
        return binder
    }

    fun getCurrentPosition() = player?.currentPosition

    fun getDuration() = player?.duration

    fun seekToPosition(position: Long) = player?.seekTo(position)

    fun pause() {
        player?.pause()
    }

    fun play() {
        player?.play()
    }
}
