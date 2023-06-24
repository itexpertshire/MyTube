package com.github.libretube.ui.views

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.os.postDelayed
import androidx.core.view.ViewCompat
import androidx.core.view.isVisible
import androidx.core.view.marginStart
import androidx.core.view.updateLayoutParams
import androidx.media3.common.Player
import androidx.media3.common.text.Cue
import androidx.media3.common.util.RepeatModeUtil
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import androidx.media3.ui.SubtitleView
import androidx.media3.ui.TimeBar
import com.github.libretube.R
import com.github.libretube.databinding.DoubleTapOverlayBinding
import com.github.libretube.databinding.ExoStyledPlayerControlViewBinding
import com.github.libretube.databinding.PlayerGestureControlsViewBinding
import com.github.libretube.extensions.dpToPx
import com.github.libretube.extensions.normalize
import com.github.libretube.extensions.round
import com.github.libretube.helpers.AudioHelper
import com.github.libretube.helpers.BrightnessHelper
import com.github.libretube.helpers.PlayerHelper
import com.github.libretube.obj.BottomSheetItem
import com.github.libretube.ui.base.BaseActivity
import com.github.libretube.ui.interfaces.PlayerGestureOptions
import com.github.libretube.ui.interfaces.PlayerOptions
import com.github.libretube.ui.listeners.PlayerGestureController
import com.github.libretube.ui.sheets.BaseBottomSheet
import com.github.libretube.ui.sheets.PlaybackOptionsSheet
import com.github.libretube.util.PlayingQueue

@SuppressLint("ClickableViewAccessibility")
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
open class CustomExoPlayerView(
    context: Context,
    attributeSet: AttributeSet? = null,
) : PlayerView(context, attributeSet), PlayerOptions, PlayerGestureOptions {
    @Suppress("LeakingThis")
    val binding = ExoStyledPlayerControlViewBinding.bind(this)

    /**
     * Objects for player tap and swipe gesture
     */
    private lateinit var gestureViewBinding: PlayerGestureControlsViewBinding
    private lateinit var playerGestureController: PlayerGestureController
    private lateinit var brightnessHelper: BrightnessHelper
    private lateinit var audioHelper: AudioHelper
    private var doubleTapOverlayBinding: DoubleTapOverlayBinding? = null

    /**
     * Objects from the parent fragment
     */

    private val runnableHandler = Handler(Looper.getMainLooper())
    var isPlayerLocked: Boolean = false

    /**
     * Preferences
     */
    private var resizeModePref = PlayerHelper.resizeModePref

    val activity get() = context as BaseActivity

    private val supportFragmentManager
        get() = activity.supportFragmentManager

    private fun toggleController() {
        if (isControllerFullyVisible) hideController() else showController()
    }

    fun initialize(
        doubleTapOverlayBinding: DoubleTapOverlayBinding,
        playerGestureControlsViewBinding: PlayerGestureControlsViewBinding,
    ) {
        this.doubleTapOverlayBinding = doubleTapOverlayBinding
        this.gestureViewBinding = playerGestureControlsViewBinding
        this.playerGestureController = PlayerGestureController(context as BaseActivity, this)
        this.brightnessHelper = BrightnessHelper(context as Activity)
        this.audioHelper = AudioHelper(context)

        // Set touch listener for tap and swipe gestures.
        setOnTouchListener(playerGestureController)
        initializeGestureProgress()

        initRewindAndForward()
        applyCaptionsStyle()
        initializeAdvancedOptions()

        // don't let the player view hide its controls automatically
        controllerShowTimeoutMs = -1
        // don't let the player view show its controls automatically
        controllerAutoShow = false

        // locking the player
        binding.lockPlayer.setOnClickListener {
            // change the locked/unlocked icon
            binding.lockPlayer.setImageResource(
                if (!isPlayerLocked) {
                    R.drawable.ic_locked
                } else {
                    R.drawable.ic_unlocked
                },
            )

            // show/hide all the controls
            lockPlayer(isPlayerLocked)

            // change locked status
            isPlayerLocked = !isPlayerLocked
        }

        resizeMode = when (resizeModePref) {
            "fill" -> AspectRatioFrameLayout.RESIZE_MODE_FILL
            "zoom" -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
        }

        binding.playPauseBTN.setOnClickListener {
            when {
                player?.isPlaying == false && player?.playbackState == Player.STATE_ENDED -> {
                    player?.seekTo(0)
                }
                player?.isPlaying == false -> player?.play()
                else -> player?.pause()
            }
        }

        player?.addListener(object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                super.onEvents(player, events)
                if (events.containsAny(
                        Player.EVENT_PLAYBACK_STATE_CHANGED,
                        Player.EVENT_IS_PLAYING_CHANGED,
                        Player.EVENT_PLAY_WHEN_READY_CHANGED,
                    )
                ) {
                    updatePlayPauseButton()

                    // keep screen on if the video is playing
                    keepScreenOn = player.isPlaying == true
                    onPlayerEvent(player, events)
                }
            }
        })

        // prevent the controls from disappearing while scrubbing the time bar
        binding.exoProgress.addListener(object : TimeBar.OnScrubListener {
            override fun onScrubStart(timeBar: TimeBar, position: Long) {
                cancelHideControllerTask()
            }

            override fun onScrubMove(timeBar: TimeBar, position: Long) {
                cancelHideControllerTask()
            }

            override fun onScrubStop(timeBar: TimeBar, position: Long, canceled: Boolean) {
                enqueueHideControllerTask()
            }
        })
    }

    open fun onPlayerEvent(player: Player, playerEvents: Player.Events) = Unit

    private fun updatePlayPauseButton() {
        binding.playPauseBTN.setImageResource(
            when {
                player?.isPlaying == true -> R.drawable.ic_pause
                player?.playbackState == Player.STATE_ENDED -> R.drawable.ic_restart
                else -> R.drawable.ic_play
            },
        )
    }

    private fun enqueueHideControllerTask() {
        handler.postDelayed(AUTO_HIDE_CONTROLLER_DELAY, HIDE_CONTROLLER_TOKEN) {
            hideController()
        }
    }

    private fun cancelHideControllerTask() {
        handler?.removeCallbacksAndMessages(HIDE_CONTROLLER_TOKEN)
    }

    override fun hideController() {
        // remove the callback to hide the controller
        cancelHideControllerTask()
        super.hideController()
    }

    override fun showController() {
        // remove the previous callback from the queue to prevent a flashing behavior
        cancelHideControllerTask()
        // automatically hide the controller after 2 seconds
        enqueueHideControllerTask()
        super.showController()
    }

    override fun onTouchEvent(event: MotionEvent) = false

    private fun initRewindAndForward() {
        val seekIncrementText = (PlayerHelper.seekIncrement / 1000).toString()
        listOf(
            doubleTapOverlayBinding?.rewindTV,
            doubleTapOverlayBinding?.forwardTV,
            binding.forwardTV,
            binding.rewindTV,
        ).forEach {
            it?.text = seekIncrementText
        }
        binding.forwardBTN.setOnClickListener {
            player?.seekTo(player!!.currentPosition + PlayerHelper.seekIncrement)
        }
        binding.rewindBTN.setOnClickListener {
            player?.seekTo(player!!.currentPosition - PlayerHelper.seekIncrement)
        }
        if (PlayerHelper.doubleTapToSeek) return

        listOf(binding.forwardBTN, binding.rewindBTN).forEach {
            it.visibility = View.VISIBLE
        }
    }

    private fun initializeAdvancedOptions() {
        binding.toggleOptions.setOnClickListener {
            val items = getOptionsMenuItems()
            val bottomSheetFragment = BaseBottomSheet().setItems(items, null)
            bottomSheetFragment.show(supportFragmentManager, null)
        }
    }

    open fun getOptionsMenuItems(): List<BottomSheetItem> = listOf(
            BottomSheetItem(
                context.getString(R.string.repeat_mode),
                R.drawable.ic_repeat,
                {
                    if (player?.repeatMode == RepeatModeUtil.REPEAT_TOGGLE_MODE_NONE) {
                        context.getString(R.string.repeat_mode_none)
                    } else {
                        context.getString(R.string.repeat_mode_current)
                    }
                },
            ) {
                onRepeatModeClicked()
            },
            BottomSheetItem(
                context.getString(R.string.player_resize_mode),
                R.drawable.ic_aspect_ratio,
                {
                    when (resizeMode) {
                        AspectRatioFrameLayout.RESIZE_MODE_FIT -> context.getString(
                            R.string.resize_mode_fit,
                        )
                        AspectRatioFrameLayout.RESIZE_MODE_FILL -> context.getString(
                            R.string.resize_mode_fill,
                        )
                        else -> context.getString(R.string.resize_mode_zoom)
                    }
                },
            ) {
                onResizeModeClicked()
            },
            BottomSheetItem(
                context.getString(R.string.playback_speed),
                R.drawable.ic_speed,
                {
                    "${player?.playbackParameters?.speed?.round(2)}x"
                },
            ) {
                onPlaybackSpeedClicked()
            },
        )

    // lock the player
    private fun lockPlayer(isLocked: Boolean) {
        // isLocked is the current (old) state of the player lock
        binding.exoTopBarRight.isVisible = isLocked
        binding.exoCenterControls.isVisible = isLocked
        binding.bottomBar.isVisible = isLocked
        binding.closeImageButton.isVisible = isLocked
        binding.exoTitle.isVisible = isLocked
        binding.playPauseBTN.isVisible = isLocked

        if (!PlayerHelper.doubleTapToSeek) {
            binding.rewindBTN.isVisible = isLocked
            binding.forwardBTN.isVisible = isLocked
        }

        // hide the dimming background overlay if locked
        binding.exoControlsBackground.setBackgroundColor(
            if (isLocked) {
                ContextCompat.getColor(
                    context,
                    androidx.media3.ui.R.color.exo_black_opacity_60,
                )
            } else {
                Color.TRANSPARENT
            },
        )

        // disable tap and swipe gesture if the player is locked
        playerGestureController.isEnabled = isLocked
    }

    private fun rewind() {
        player?.seekTo((player?.currentPosition ?: 0L) - PlayerHelper.seekIncrement)

        // show the rewind button
        doubleTapOverlayBinding?.apply {
            animateSeeking(rewindBTN, rewindIV, rewindTV, true)

            // start callback to hide the button
            runnableHandler.removeCallbacksAndMessages(HIDE_REWIND_BUTTON_TOKEN)
            runnableHandler.postDelayed(700, HIDE_REWIND_BUTTON_TOKEN) {
                rewindBTN.visibility = View.GONE
            }
        }
    }

    private fun forward() {
        player?.seekTo(player!!.currentPosition + PlayerHelper.seekIncrement)

        // show the forward button
        doubleTapOverlayBinding?.apply {
            animateSeeking(forwardBTN, forwardIV, forwardTV, false)

            // start callback to hide the button
            runnableHandler.removeCallbacksAndMessages(HIDE_FORWARD_BUTTON_TOKEN)
            runnableHandler.postDelayed(700, HIDE_FORWARD_BUTTON_TOKEN) {
                forwardBTN.visibility = View.GONE
            }
        }
    }

    private fun animateSeeking(
        container: FrameLayout,
        imageView: ImageView,
        textView: TextView,
        isRewind: Boolean,
    ) {
        container.visibility = View.VISIBLE
        // the direction of the action
        val direction = if (isRewind) -1 else 1

        // clear previous animation
        imageView.animate()
            .rotation(0F)
            .setDuration(0)
            .start()

        textView.animate()
            .translationX(0f)
            .setDuration(0)
            .start()

        // start the rotate animation of the drawable
        imageView.animate()
            .rotation(direction * 30F)
            .setDuration(ANIMATION_DURATION)
            .withEndAction {
                // reset the animation when finished
                imageView.animate()
                    .rotation(0F)
                    .setDuration(ANIMATION_DURATION)
                    .start()
            }
            .start()

        // animate the text view to move outside the image view
        textView.animate()
            .translationX(direction * 100f)
            .setDuration((ANIMATION_DURATION * 1.5).toLong())
            .withEndAction {
                // move the text back into the button
                handler.postDelayed(100) {
                    textView.animate()
                        .setDuration(ANIMATION_DURATION / 2)
                        .translationX(0f)
                        .start()
                }
            }
    }

    private fun initializeGestureProgress() {
        gestureViewBinding.brightnessProgressBar.let { bar ->
            bar.progress =
                brightnessHelper.getBrightnessWithScale(bar.max.toFloat(), saved = true).toInt()
        }
        gestureViewBinding.volumeProgressBar.let { bar ->
            bar.progress = audioHelper.getVolumeWithScale(bar.max)
        }
    }

    private fun updateBrightness(distance: Float) {
        gestureViewBinding.brightnessControlView.visibility = View.VISIBLE
        val bar = gestureViewBinding.brightnessProgressBar

        if (bar.progress == 0) {
            // If brightness progress goes to below 0, set to system brightness
            if (distance <= 0) {
                brightnessHelper.resetToSystemBrightness()
                gestureViewBinding.brightnessImageView.setImageResource(
                    R.drawable.ic_brightness_auto,
                )
                gestureViewBinding.brightnessTextView.text = resources.getString(R.string.auto)
                return
            }
            gestureViewBinding.brightnessImageView.setImageResource(R.drawable.ic_brightness)
        }

        bar.incrementProgressBy(distance.toInt())
        gestureViewBinding.brightnessTextView.text = "${bar.progress.normalize(0, bar.max, 0, 100)}"
        brightnessHelper.setBrightnessWithScale(bar.progress.toFloat(), bar.max.toFloat())
    }

    private fun updateVolume(distance: Float) {
        val bar = gestureViewBinding.volumeProgressBar
        gestureViewBinding.volumeControlView.apply {
            if (visibility == View.GONE) {
                visibility = View.VISIBLE
                // Volume could be changed using other mediums, sync progress
                // bar with new value.
                bar.progress = audioHelper.getVolumeWithScale(bar.max)
            }
        }

        if (bar.progress == 0) {
            gestureViewBinding.volumeImageView.setImageResource(
                when {
                    distance > 0 -> R.drawable.ic_volume_up
                    else -> R.drawable.ic_volume_off
                },
            )
        }
        bar.incrementProgressBy(distance.toInt())
        audioHelper.setVolumeWithScale(bar.progress, bar.max)

        gestureViewBinding.volumeTextView.text = "${bar.progress.normalize(0, bar.max, 0, 100)}"
    }

    override fun onPlaybackSpeedClicked() {
        player?.let {
            PlaybackOptionsSheet(it as ExoPlayer).show(supportFragmentManager)
        }
    }

    override fun onResizeModeClicked() {
        // switching between original aspect ratio (black bars) and zoomed to fill device screen
        val aspectRatioModeNames = context.resources?.getStringArray(R.array.resizeMode)
            ?.toList().orEmpty()

        val aspectRatioModes = listOf(
            AspectRatioFrameLayout.RESIZE_MODE_FIT,
            AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
            AspectRatioFrameLayout.RESIZE_MODE_FILL,
        )

        BaseBottomSheet()
            .setSimpleItems(aspectRatioModeNames) { index ->
                resizeMode = aspectRatioModes[index]
            }
            .show(supportFragmentManager)
    }

    override fun onRepeatModeClicked() {
        val repeatModeNames = listOf(
            context.getString(R.string.repeat_mode_none),
            context.getString(R.string.repeat_mode_current),
            context.getString(R.string.all),
        )
        // repeat mode options dialog
        BaseBottomSheet()
            .setSimpleItems(repeatModeNames) { index ->
                PlayingQueue.repeatQueue = when (index) {
                    0 -> {
                        player?.repeatMode = Player.REPEAT_MODE_OFF
                        false
                    }
                    1 -> {
                        player?.repeatMode = Player.REPEAT_MODE_ONE
                        false
                    }
                    else -> true
                }
            }
            .show(supportFragmentManager)
    }

    open fun isFullscreen() = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        // add a larger bottom margin to the time bar in landscape mode
        val offset = when {
            isFullscreen() -> 20.dpToPx()
            else -> 10.dpToPx()
        }

        binding.progressBar.updateLayoutParams<MarginLayoutParams> {
            bottomMargin = offset.toInt()
        }

        updateTopBarMargin()

        // don't add extra padding if there's no cutout
        val hasCutout = ViewCompat.getRootWindowInsets(this)?.displayCutout != null
        if (!hasCutout && binding.topBar.marginStart == 0) return

        // add a margin to the top and the bottom bar in landscape mode for notches
        val newMargin = when (newConfig.orientation) {
            Configuration.ORIENTATION_LANDSCAPE -> LANDSCAPE_MARGIN_HORIZONTAL
            else -> 0
        }

        listOf(binding.topBar, binding.bottomBar).forEach {
            it.updateLayoutParams<MarginLayoutParams> {
                marginStart = newMargin
                marginEnd = newMargin
            }
        }
    }

    /**
     * Load the captions style according to the users preferences
     */
    private fun applyCaptionsStyle() {
        val captionStyle = PlayerHelper.getCaptionStyle(context)
        subtitleView?.apply {
            setApplyEmbeddedFontSizes(false)
            setFixedTextSize(Cue.TEXT_SIZE_TYPE_ABSOLUTE, PlayerHelper.captionsTextSize)
            if (!PlayerHelper.useSystemCaptionStyle) return
            setApplyEmbeddedStyles(captionStyle == CaptionStyleCompat.DEFAULT)
            setStyle(captionStyle)
        }
    }

    /**
     * Add extra margin to the top bar to not overlap the status bar
     */
    fun updateTopBarMargin() {
        binding.topBar.updateLayoutParams<MarginLayoutParams> {
            topMargin = getTopBarMarginDp().dpToPx().toInt()
        }
    }

    open fun getTopBarMarginDp(): Int {
        return if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) 10 else 0
    }

    override fun onSingleTap() {
        toggleController()
    }

    override fun onDoubleTapCenterScreen() {
        player?.let { player ->
            if (player.isPlaying) {
                player.pause()
            } else {
                player.play()
            }
        }
    }

    override fun onDoubleTapLeftScreen() {
        if (!PlayerHelper.doubleTapToSeek) return
        rewind()
    }

    override fun onDoubleTapRightScreen() {
        if (!PlayerHelper.doubleTapToSeek) return
        forward()
    }

    override fun onSwipeLeftScreen(distanceY: Float) {
        if (!PlayerHelper.swipeGestureEnabled) {
            if (PlayerHelper.fullscreenGesturesEnabled) onSwipeCenterScreen(distanceY)
            return
        }

        if (isControllerFullyVisible) hideController()
        updateBrightness(distanceY)
    }

    override fun onSwipeRightScreen(distanceY: Float) {
        if (!PlayerHelper.swipeGestureEnabled) {
            if (PlayerHelper.fullscreenGesturesEnabled) onSwipeCenterScreen(distanceY)
            return
        }

        if (isControllerFullyVisible) hideController()
        updateVolume(distanceY)
    }

    override fun onSwipeCenterScreen(distanceY: Float) {
        if (!PlayerHelper.fullscreenGesturesEnabled) return

        if (isControllerFullyVisible) hideController()
        if (distanceY >= 0) return

        playerGestureController.isMoving = false
        (context as? AppCompatActivity)?.onBackPressedDispatcher?.onBackPressed()
    }

    override fun onSwipeEnd() {
        gestureViewBinding.brightnessControlView.visibility = View.GONE
        gestureViewBinding.volumeControlView.visibility = View.GONE
    }

    override fun onZoom() {
        if (!PlayerHelper.pinchGestureEnabled) return
        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM

        if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            subtitleView?.setBottomPaddingFraction(SUBTITLE_BOTTOM_PADDING_FRACTION)
        }
    }

    override fun onMinimize() {
        if (!PlayerHelper.pinchGestureEnabled) return
        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
        subtitleView?.setBottomPaddingFraction(SubtitleView.DEFAULT_BOTTOM_PADDING_FRACTION)
    }

    override fun onFullscreenChange(isFullscreen: Boolean) {
        if (PlayerHelper.swipeGestureEnabled && this::brightnessHelper.isInitialized) {
            if (isFullscreen) {
                brightnessHelper.restoreSavedBrightness()
                if (resizeMode == AspectRatioFrameLayout.RESIZE_MODE_ZOOM) {
                    subtitleView?.setBottomPaddingFraction(SUBTITLE_BOTTOM_PADDING_FRACTION)
                }
            } else {
                brightnessHelper.resetToSystemBrightness(false)
                subtitleView?.setBottomPaddingFraction(SubtitleView.DEFAULT_BOTTOM_PADDING_FRACTION)
            }
        }
    }

    /**
     * Listen for all child touch events
     */
    override fun onInterceptTouchEvent(ev: MotionEvent?): Boolean {
        // when a control is clicked, restart the countdown to hide the controller
        if (isControllerFullyVisible) {
            cancelHideControllerTask()
            enqueueHideControllerTask()
        }
        return super.onInterceptTouchEvent(ev)
    }

    companion object {
        private const val HIDE_CONTROLLER_TOKEN = "hideController"
        private const val HIDE_FORWARD_BUTTON_TOKEN = "hideForwardButton"
        private const val HIDE_REWIND_BUTTON_TOKEN = "hideRewindButton"

        private const val SUBTITLE_BOTTOM_PADDING_FRACTION = 0.158f
        private const val ANIMATION_DURATION = 100L
        private const val AUTO_HIDE_CONTROLLER_DELAY = 2000L
        private val LANDSCAPE_MARGIN_HORIZONTAL = (20).dpToPx().toInt()
    }
}
