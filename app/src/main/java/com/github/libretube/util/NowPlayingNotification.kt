package com.github.libretube.util

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.PendingIntent.FLAG_UPDATE_CURRENT
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.support.v4.media.session.MediaSessionCompat
import android.util.Log
import androidx.annotation.DrawableRes
import androidx.core.app.NotificationCompat
import androidx.core.app.PendingIntentCompat
import androidx.core.content.getSystemService
import androidx.core.graphics.drawable.toBitmap
import androidx.core.os.bundleOf
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaStyleNotificationHelper
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import coil.request.ImageRequest
import com.github.libretube.R
import com.github.libretube.constants.BACKGROUND_CHANNEL_ID
import com.github.libretube.constants.IntentData
import com.github.libretube.constants.PLAYER_NOTIFICATION_ID
import com.github.libretube.helpers.BackgroundHelper
import com.github.libretube.helpers.ImageHelper
import com.github.libretube.helpers.PlayerHelper
import com.github.libretube.obj.PlayerNotificationData
import com.github.libretube.ui.activities.MainActivity
import com.google.common.util.concurrent.ListenableFuture

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class NowPlayingNotification(
    private val context: Context,
    private val player: ExoPlayer,
    private val isBackgroundPlayerNotification: Boolean,
) {
    private var videoId: String? = null
    private val nManager = context.getSystemService<NotificationManager>()!!

    /**
     * The metadata of the current playing song (thumbnail, title, uploader)
     */
    private var notificationData: PlayerNotificationData? = null

    /**
     * The [MediaSessionCompat] for the [notificationData].
     */
    private lateinit var mediaSession: MediaSession

    /**
     * The [NotificationCompat.Builder] to load the [mediaSession] content on it.
     */
    private var notificationBuilder: NotificationCompat.Builder? = null

    /**
     * The [Bitmap] which represents the background / thumbnail of the notification
     */
    private var notificationBitmap: Bitmap? = null

    private fun loadCurrentLargeIcon() {
        if (DataSaverMode.isEnabled(context)) return
        if (notificationBitmap == null) enqueueThumbnailRequest {
            createOrUpdateNotification()
        }
    }

    private fun createCurrentContentIntent(): PendingIntent {
        // starts a new MainActivity Intent when the player notification is clicked
        // it doesn't start a completely new MainActivity because the MainActivity's launchMode
        // is set to "singleTop" in the AndroidManifest (important!!!)
        // that's the only way to launch back into the previous activity (e.g. the player view
        val intent = Intent(context, MainActivity::class.java).apply {
            if (isBackgroundPlayerNotification) {
                putExtra(IntentData.openAudioPlayer, true)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
        }
        return PendingIntentCompat.getActivity(context, 0, intent, FLAG_UPDATE_CURRENT, false)
    }

    private fun createDeleteIntent(): PendingIntent {
        val intent = Intent(STOP).setPackage(context.packageName)
        return PendingIntentCompat
            .getBroadcast(context, 1, intent, PendingIntent.FLAG_CANCEL_CURRENT, false)
    }

    private fun enqueueThumbnailRequest(callback: (Bitmap) -> Unit) {
        // If playing a downloaded file, show the downloaded thumbnail instead of loading an
        // online image
        notificationData?.thumbnailPath?.let { path ->
            ImageHelper.getDownloadedImage(context, path)?.let {
                notificationBitmap = processBitmap(it)
                callback.invoke(notificationBitmap!!)
            }
            return
        }

        val request = ImageRequest.Builder(context)
            .data(notificationData?.thumbnailUrl)
            .target {
                notificationBitmap = processBitmap(it.toBitmap())
                callback.invoke(notificationBitmap!!)
            }
            .build()

        // enqueue the thumbnail loading request
        ImageHelper.imageLoader.enqueue(request)
    }

    private fun processBitmap(bitmap: Bitmap): Bitmap {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            bitmap
        } else ImageHelper.getSquareBitmap(bitmap)
    }

    private val legacyNotificationButtons
        get() = listOf(
            createNotificationAction(R.drawable.ic_prev_outlined, PREV),
            createNotificationAction(
                if (player.isPlaying) R.drawable.ic_pause else R.drawable.ic_play,
                PLAY_PAUSE
            ),
            createNotificationAction(R.drawable.ic_next_outlined, NEXT),
            createNotificationAction(R.drawable.ic_rewind_md, REWIND),
            createNotificationAction(R.drawable.ic_forward_md, FORWARD),
        )

    private fun createNotificationAction(
        drawableRes: Int,
        actionName: String
    ): NotificationCompat.Action {
        val intent = Intent(actionName).setPackage(context.packageName)
        val pendingIntent = PendingIntentCompat
            .getBroadcast(context, 1, intent, PendingIntent.FLAG_CANCEL_CURRENT, false)
        return NotificationCompat.Action.Builder(drawableRes, actionName, pendingIntent).build()
    }

    private fun createMediaSessionAction(
        @DrawableRes drawableRes: Int,
        actionName: String,
    ): CommandButton {
        return CommandButton.Builder()
            .setDisplayName(actionName)
            .setSessionCommand(SessionCommand(actionName, bundleOf()))
            .setIconResId(drawableRes)
            .build()
    }

    /**
     * Creates a [MediaSessionCompat] for the player
     */
    private fun createMediaSession() {
        if (this::mediaSession.isInitialized) return

        val sessionCallback = object : MediaSession.Callback {
            override fun onConnect(
                session: MediaSession,
                controller: MediaSession.ControllerInfo,
            ): MediaSession.ConnectionResult {
                val connectionResult = super.onConnect(session, controller)
                val availableSessionCommands = connectionResult.availableSessionCommands.buildUpon()
                val availablePlayerCommands =
                    connectionResult.availablePlayerCommands // Player.Commands.Builder().add(Player.COMMAND_PLAY_PAUSE).build()
                getCustomActions().forEach { button ->
                    button.sessionCommand?.let { availableSessionCommands.add(it) }
                }
                session.setAvailableCommands(
                    controller,
                    availableSessionCommands.build(),
                    availablePlayerCommands
                )
                return MediaSession.ConnectionResult.accept(
                    availableSessionCommands.build(),
                    availablePlayerCommands,
                )
            }

            override fun onCustomCommand(
                session: MediaSession,
                controller: MediaSession.ControllerInfo,
                customCommand: SessionCommand,
                args: Bundle,
            ): ListenableFuture<SessionResult> {
                handlePlayerAction(customCommand.customAction)
                return super.onCustomCommand(session, controller, customCommand, args)
            }

            override fun onPlayerCommandRequest(
                session: MediaSession,
                controller: MediaSession.ControllerInfo,
                playerCommand: Int
            ): Int {
                if (playerCommand == Player.COMMAND_SEEK_TO_PREVIOUS) {
                    handlePlayerAction(PREV)
                    return SessionResult.RESULT_SUCCESS
                }
                return super.onPlayerCommandRequest(session, controller, playerCommand)
            }

            override fun onPostConnect(
                session: MediaSession,
                controller: MediaSession.ControllerInfo
            ) {
                session.setCustomLayout(getCustomActions())
            }
        }

        mediaSession = MediaSession.Builder(context, player)
            .setCallback(sessionCallback)
            .build()
        mediaSession.setCustomLayout(getCustomActions())
    }

    private fun getCustomActions() = mutableListOf(
        // disabled and overwritten in onPlayerCommandRequest
        // createMediaSessionAction(R.drawable.ic_prev_outlined, PREV),
        createMediaSessionAction(R.drawable.ic_next_outlined, NEXT),
        createMediaSessionAction(R.drawable.ic_rewind_md, REWIND),
        createMediaSessionAction(R.drawable.ic_forward_md, FORWARD),
    )

    private fun handlePlayerAction(action: String) {
        when (action) {
            NEXT -> {
                if (PlayingQueue.hasNext()) {
                    PlayingQueue.onQueueItemSelected(
                        PlayingQueue.currentIndex() + 1,
                    )
                }
            }

            PREV -> {
                if (PlayingQueue.hasPrev()) {
                    PlayingQueue.onQueueItemSelected(
                        PlayingQueue.currentIndex() - 1,
                    )
                }
            }

            REWIND -> {
                player.seekTo(player.currentPosition - PlayerHelper.seekIncrement)
            }

            FORWARD -> {
                player.seekTo(player.currentPosition + PlayerHelper.seekIncrement)
            }

            PLAY_PAUSE -> {
                if (player.isPlaying) player.pause() else player.play()
            }

            STOP -> {
                Log.e("stop", "stop")
                if (isBackgroundPlayerNotification) {
                    BackgroundHelper.stopBackgroundPlay(context)
                }
            }
        }
    }

    /**
     * Updates or creates the [notificationBuilder]
     */
    fun updatePlayerNotification(
        videoId: String,
        data: PlayerNotificationData,
    ) {
        this.videoId = videoId
        this.notificationData = data
        // reset the thumbnail bitmap in order to become reloaded for the new video
        this.notificationBitmap = null

        loadCurrentLargeIcon()

        if (notificationBuilder == null) {
            createMediaSession()
            createNotificationBuilder()
            createActionReceiver()
            // update the notification each time the player continues playing or pauses
            player.addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    createOrUpdateNotification()
                    super.onIsPlayingChanged(isPlaying)
                }
            })
        }

        createOrUpdateNotification()
    }

    /**
     * Initializes the [notificationBuilder] attached to the [player] and shows it.
     */
    private fun createNotificationBuilder() {
        notificationBuilder = NotificationCompat.Builder(context, BACKGROUND_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_lockscreen)
            .setContentIntent(createCurrentContentIntent())
            .setDeleteIntent(createDeleteIntent())
            .setStyle(
                MediaStyleNotificationHelper.MediaStyle(mediaSession)
                    .setShowActionsInCompactView(1)
            )
    }

    private fun createOrUpdateNotification() {
        if (notificationBuilder == null) return
        val notification = notificationBuilder!!
            .setContentTitle(notificationData?.title)
            .setContentText(notificationData?.uploaderName)
            .setLargeIcon(notificationBitmap)
            .clearActions()
            .apply {
                legacyNotificationButtons.forEach {
                    addAction(it)
                }
            }
            .build()
        nManager.notify(PLAYER_NOTIFICATION_ID, notification)
    }

    private val notificationActionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            handlePlayerAction(intent.action ?: return)
        }
    }

    private fun createActionReceiver() {
        listOf(PREV, NEXT, REWIND, FORWARD, PLAY_PAUSE, STOP).forEach {
            context.registerReceiver(notificationActionReceiver, IntentFilter(it))
        }
    }

    /**
     * Destroy the [NowPlayingNotification]
     */
    fun destroySelfAndPlayer() {
        mediaSession.release()

        player.stop()
        player.release()

        runCatching {
            context.unregisterReceiver(notificationActionReceiver)
        }

        nManager.cancel(PLAYER_NOTIFICATION_ID)
    }

    companion object {
        private const val PREV = "prev"
        private const val NEXT = "next"
        private const val REWIND = "rewind"
        private const val FORWARD = "forward"
        private const val PLAY_PAUSE = "play_pause"
        private const val STOP = "stop"
    }
}
