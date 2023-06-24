package com.github.libretube.ui.fragments

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.constraintlayout.motion.widget.MotionLayout
import androidx.constraintlayout.motion.widget.TransitionAdapter
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.commit
import com.github.libretube.R
import com.github.libretube.api.obj.StreamItem
import com.github.libretube.constants.IntentData
import com.github.libretube.databinding.FragmentAudioPlayerBinding
import com.github.libretube.enums.ShareObjectType
import com.github.libretube.extensions.normalize
import com.github.libretube.extensions.toID
import com.github.libretube.helpers.AudioHelper
import com.github.libretube.helpers.BackgroundHelper
import com.github.libretube.helpers.ImageHelper
import com.github.libretube.helpers.NavigationHelper
import com.github.libretube.helpers.PlayerHelper
import com.github.libretube.obj.ShareData
import com.github.libretube.services.OnlinePlayerService
import com.github.libretube.ui.activities.MainActivity
import com.github.libretube.ui.dialogs.ShareDialog
import com.github.libretube.ui.interfaces.AudioPlayerOptions
import com.github.libretube.ui.listeners.AudioPlayerThumbnailListener
import com.github.libretube.ui.models.PlayerViewModel
import com.github.libretube.ui.sheets.PlaybackOptionsSheet
import com.github.libretube.ui.sheets.PlayingQueueSheet
import com.github.libretube.ui.sheets.VideoOptionsBottomSheet
import com.github.libretube.util.PlayingQueue
import kotlin.math.abs

class AudioPlayerFragment : Fragment(), AudioPlayerOptions {
    private var _binding: FragmentAudioPlayerBinding? = null
    val binding get() = _binding!!

    private lateinit var audioHelper: AudioHelper
    private val mainActivity get() = context as MainActivity
    private val viewModel: PlayerViewModel by activityViewModels()

    // for the transition
    private var sId: Int = 0
    private var eId: Int = 0

    private var handler = Handler(Looper.getMainLooper())
    private var isPaused: Boolean = false

    private var playerService: OnlinePlayerService? = null

    /** Defines callbacks for service binding, passed to bindService()  */
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            val binder = service as OnlinePlayerService.LocalBinder
            playerService = binder.getService()
            handleServiceConnection()
        }

        override fun onServiceDisconnected(arg0: ComponentName) {}
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        audioHelper = AudioHelper(requireContext())
        Intent(activity, OnlinePlayerService::class.java).also { intent ->
            activity?.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentAudioPlayerBinding.inflate(inflater)
        return binding.root
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initializeTransitionLayout()

        // select the title TV in order for it to automatically scroll
        binding.title.isSelected = true
        binding.uploader.isSelected = true

        binding.minimizePlayer.setOnClickListener {
            val mainMotionLayout = mainActivity.binding.mainMotionLayout
            mainMotionLayout.transitionToStart()
            binding.playerMotionLayout.transitionToEnd()
        }

        binding.dropdownMenu.setOnClickListener {
            onLongTap()
        }

        binding.prev.setOnClickListener {
            val currentIndex = PlayingQueue.currentIndex()
            if (!PlayingQueue.hasPrev()) return@setOnClickListener
            PlayingQueue.onQueueItemSelected(currentIndex - 1)
        }

        binding.next.setOnClickListener {
            val currentIndex = PlayingQueue.currentIndex()
            if (!PlayingQueue.hasNext()) return@setOnClickListener
            PlayingQueue.onQueueItemSelected(currentIndex + 1)
        }

        binding.openQueue.setOnClickListener {
            PlayingQueueSheet().show(childFragmentManager)
        }

        binding.playbackOptions.setOnClickListener {
            playerService?.player?.let {
                PlaybackOptionsSheet(it)
                    .show(childFragmentManager)
            }
        }

        binding.openVideo.setOnClickListener {
            BackgroundHelper.stopBackgroundPlay(requireContext())
            killFragment()
            NavigationHelper.navigateVideo(
                context = requireContext(),
                videoId = PlayingQueue.getCurrent()?.url?.toID(),
                timestamp = playerService?.player?.currentPosition?.div(1000) ?: 0,
                keepQueue = true,
                forceVideo = true,
            )
        }

        binding.share.setOnClickListener {
            val currentVideo = PlayingQueue.getCurrent() ?: return@setOnClickListener
            ShareDialog(
                id = currentVideo.url!!.toID(),
                shareObjectType = ShareObjectType.VIDEO,
                shareData = ShareData(currentVideo = currentVideo.title),
            ).show(childFragmentManager, null)
        }

        binding.chapters.setOnClickListener {
            val playerService = playerService ?: return@setOnClickListener
            if (playerService.streams == null || playerService.player == null) return@setOnClickListener

            if (playerService.streams!!.chapters.isEmpty()) {
                Toast.makeText(context, R.string.emptyList, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            PlayerHelper.showChaptersDialog(
                requireContext(),
                playerService.streams!!.chapters,
                playerService.player!!
            )
        }

        binding.miniPlayerClose.setOnClickListener {
            activity?.unbindService(connection)
            BackgroundHelper.stopBackgroundPlay(requireContext())
            killFragment()
        }

        val listener = AudioPlayerThumbnailListener(requireContext(), this)
        binding.thumbnail.setOnTouchListener(listener)

        binding.playPause.setOnClickListener {
            if (isPaused) playerService?.play() else playerService?.pause()
        }

        binding.miniPlayerPause.setOnClickListener {
            if (isPaused) playerService?.play() else playerService?.pause()
        }

        // load the stream info into the UI
        updateStreamInfo()

        // update the currently shown volume
        binding.volumeProgressBar.let { bar ->
            bar.progress = audioHelper.getVolumeWithScale(bar.max)
        }
    }

    private fun killFragment() {
        viewModel.isFullscreen.value = false
        binding.playerMotionLayout.transitionToEnd()
        mainActivity.supportFragmentManager.commit {
            remove(this@AudioPlayerFragment)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun initializeTransitionLayout() {
        mainActivity.binding.container.visibility = View.VISIBLE
        val mainMotionLayout = mainActivity.binding.mainMotionLayout

        binding.playerMotionLayout.addTransitionListener(object : TransitionAdapter() {
            override fun onTransitionChange(
                motionLayout: MotionLayout?,
                startId: Int,
                endId: Int,
                progress: Float,
            ) {
                mainMotionLayout.progress = abs(progress)
                eId = endId
                sId = startId
            }

            override fun onTransitionCompleted(motionLayout: MotionLayout?, currentId: Int) {
                if (currentId == eId) {
                    viewModel.isMiniPlayerVisible.value = true
                    mainMotionLayout.progress = 1F
                } else if (currentId == sId) {
                    viewModel.isMiniPlayerVisible.value = false
                    mainMotionLayout.progress = 0F
                }
            }
        })

        if (arguments?.getBoolean(IntentData.minimizeByDefault, false) != true) {
            binding.playerMotionLayout.progress = 1f
            binding.playerMotionLayout.transitionToStart()
        } else {
            binding.playerMotionLayout.progress = 0f
            binding.playerMotionLayout.transitionToEnd()
        }
    }

    /**
     * Load the information from a new stream into the UI
     */
    private fun updateStreamInfo(stream: StreamItem? = null) {
        val current = stream ?: PlayingQueue.getCurrent() ?: return

        binding.title.text = current.title
        binding.miniPlayerTitle.text = current.title

        binding.uploader.text = current.uploaderName
        binding.uploader.setOnClickListener {
            NavigationHelper.navigateChannel(requireContext(), current.uploaderUrl?.toID())
        }

        current.thumbnail?.let { updateThumbnailAsync(it) }

        initializeSeekBar()
    }

    private fun updateThumbnailAsync(thumbnailUrl: String) {
        binding.progress.visibility = View.VISIBLE
        binding.thumbnail.visibility = View.GONE

        ImageHelper.getAsync(requireContext(), thumbnailUrl) {
            binding.thumbnail.setImageBitmap(it)
            binding.miniPlayerThumbnail.setImageBitmap(it)
            binding.thumbnail.visibility = View.VISIBLE
            binding.progress.visibility = View.GONE
        }
    }

    private fun initializeSeekBar() {
        binding.timeBar.addOnChangeListener { _, value, fromUser ->
            if (fromUser) playerService?.seekToPosition(value.toLong() * 1000)
        }
        updateSeekBar()
    }

    /**
     * Update the position, duration and text views belonging to the seek bar
     */
    private fun updateSeekBar() {
        val binding = _binding ?: return
        val duration = playerService?.getDuration()?.takeIf { it > 0 } ?: let {
            // if there's no duration available, clear everything
            binding.timeBar.value = 0f
            binding.duration.text = ""
            binding.currentPosition.text = ""
            handler.postDelayed(this::updateSeekBar, 100)
            return
        }
        val currentPosition = playerService?.getCurrentPosition()?.toFloat() ?: 0f

        // set the text for the indicators
        binding.duration.text = DateUtils.formatElapsedTime(duration / 1000)
        binding.currentPosition.text = DateUtils.formatElapsedTime(
            (currentPosition / 1000).toLong(),
        )

        // update the time bar current value and maximum value
        binding.timeBar.valueTo = (duration / 1000).toFloat()
        binding.timeBar.value = minOf(
            currentPosition / 1000,
            binding.timeBar.valueTo,
        )

        handler.postDelayed(this::updateSeekBar, 200)
    }

    private fun handleServiceConnection() {
        playerService?.onIsPlayingChanged = { isPlaying ->
            val iconResource = if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play
            binding.playPause.setIconResource(iconResource)
            binding.miniPlayerPause.setImageResource(iconResource)
            isPaused = !isPlaying
        }
        playerService?.onNewVideo = { streams, videoId ->
            updateStreamInfo(streams.toStreamItem(videoId))
        }
        initializeSeekBar()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onDestroy() {
        // unregister all listeners and the connected [playerService]
        playerService?.onIsPlayingChanged = null
        runCatching {
            activity?.unbindService(connection)
        }

        super.onDestroy()
    }

    override fun onSingleTap() {
        if (isPaused) playerService?.play() else playerService?.pause()
    }

    override fun onLongTap() {
        val current = PlayingQueue.getCurrent()
        VideoOptionsBottomSheet(current?.url?.toID() ?: return, current.title ?: return)
            .show(childFragmentManager)
    }

    override fun onSwipe(distanceY: Float) {
        binding.volumeControls.visibility = View.VISIBLE
        updateVolume(distanceY)
    }

    override fun onSwipeEnd() {
        binding.volumeControls.visibility = View.GONE
    }

    private fun updateVolume(distance: Float) {
        val bar = binding.volumeProgressBar
        binding.volumeControls.apply {
            if (visibility == View.GONE) {
                visibility = View.VISIBLE
                // Volume could be changed using other mediums, sync progress
                // bar with new value.
                bar.progress = audioHelper.getVolumeWithScale(bar.max)
            }
        }

        if (bar.progress == 0) {
            binding.volumeImageView.setImageResource(
                when {
                    distance > 0 -> R.drawable.ic_volume_up
                    else -> R.drawable.ic_volume_off
                },
            )
        }
        bar.incrementProgressBy(distance.toInt() / 3)
        audioHelper.setVolumeWithScale(bar.progress, bar.max)

        binding.volumeTextView.text = "${bar.progress.normalize(0, bar.max, 0, 100)}"
    }
}
