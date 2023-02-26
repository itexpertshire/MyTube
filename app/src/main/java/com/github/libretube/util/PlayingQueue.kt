package com.github.libretube.util

import android.util.Log
import com.github.libretube.api.PlaylistsHelper
import com.github.libretube.api.RetrofitInstance
import com.github.libretube.api.obj.StreamItem
import com.github.libretube.extensions.move
import com.github.libretube.extensions.toID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object PlayingQueue {
    private val queue = mutableListOf<StreamItem>()
    private var currentStream: StreamItem? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    /**
     * Listener that gets called when the user selects an item from the queue
     */
    private var onQueueTapListener: (StreamItem) -> Unit = {}

    /**
     * Listener that gets called when the current playing video changes
     */
    private val onTrackChangedListeners: MutableList<(StreamItem) -> Unit> = mutableListOf()
    var repeatQueue: Boolean = false

    fun clear() = queue.clear()

    fun add(vararg streamItem: StreamItem) {
        for (stream in streamItem) {
            if (currentStream?.url?.toID() == stream.url?.toID()) continue
            // remove if already present
            queue.remove(stream)
            queue.add(stream)
        }
    }

    fun addAsNext(streamItem: StreamItem) {
        if (currentStream == streamItem) return
        if (queue.contains(streamItem)) queue.remove(streamItem)
        queue.add(
            currentIndex() + 1,
            streamItem
        )
    }

    fun getNext(): String? {
        try {
            return queue[currentIndex() + 1].url?.toID()
        } catch (e: Exception) {
            Log.e("queue ended", e.toString())
        }
        if (repeatQueue) return queue.firstOrNull()?.url?.toID()
        return null
    }

    fun getPrev(): String? {
        return if (currentIndex() > 0) queue[currentIndex() - 1].url?.toID() else null
    }

    fun hasPrev(): Boolean {
        return currentIndex() > 0
    }

    fun hasNext(): Boolean {
        return currentIndex() + 1 < size()
    }

    fun updateCurrent(streamItem: StreamItem) {
        currentStream = streamItem
        onTrackChangedListeners.forEach {
            runCatching {
                it.invoke(streamItem)
            }
        }
        if (!contains(streamItem)) queue.add(0, streamItem)
    }

    fun isNotEmpty() = queue.isNotEmpty()

    fun isEmpty() = queue.isEmpty()

    fun size() = queue.size

    fun currentIndex(): Int {
        return try {
            queue.indexOf(
                queue.first { it.url?.toID() == currentStream?.url?.toID() }
            )
        } catch (e: Exception) {
            0
        }
    }

    fun getCurrent(): StreamItem? = currentStream

    fun contains(streamItem: StreamItem) = queue.any { it.url?.toID() == streamItem.url?.toID() }

    // only returns a copy of the queue, no write access
    fun getStreams() = queue.toList()

    fun setStreams(streams: List<StreamItem>) {
        queue.clear()
        queue.addAll(streams)
    }

    fun remove(index: Int) = queue.removeAt(index)

    fun move(from: Int, to: Int) = queue.move(from, to)

    private fun fetchMoreFromPlaylist(playlistId: String, nextPage: String?) {
        var playlistNextPage: String? = nextPage
        scope.launch {
            while (playlistNextPage != null) {
                RetrofitInstance.authApi.getPlaylistNextPage(
                    playlistId,
                    playlistNextPage!!
                ).apply {
                    add(
                        *this.relatedStreams.orEmpty().toTypedArray()
                    )
                    playlistNextPage = this.nextpage
                }
            }
        }
    }

    fun insertPlaylist(playlistId: String, newCurrentStream: StreamItem) {
        scope.launch {
            try {
                val playlist = PlaylistsHelper.getPlaylist(playlistId)
                add(*playlist.relatedStreams.orEmpty().toTypedArray())
                updateCurrent(newCurrentStream)
                if (playlist.nextpage == null) return@launch
                fetchMoreFromPlaylist(playlistId, playlist.nextpage)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun fetchMoreFromChannel(channelId: String, nextPage: String?) {
        var channelNextPage: String? = nextPage
        scope.launch {
            while (channelNextPage != null) {
                RetrofitInstance.api.getChannelNextPage(channelId, nextPage!!).apply {
                    add(*relatedStreams.toTypedArray())
                    channelNextPage = this.nextpage
                }
            }
        }
    }

    fun insertChannel(channelId: String, newCurrentStream: StreamItem) {
        scope.launch {
            runCatching {
                val channel = RetrofitInstance.api.getChannel(channelId)
                add(*channel.relatedStreams.toTypedArray())
                updateCurrent(newCurrentStream)
                if (channel.nextpage == null) return@launch
                fetchMoreFromChannel(channelId, channel.nextpage)
            }
        }
    }

    fun insertByVideoId(videoId: String) {
        scope.launch {
            runCatching {
                val streams = RetrofitInstance.api.getStreams(videoId.toID())
                add(streams.toStreamItem(videoId))
            }
        }
    }

    fun onQueueItemSelected(index: Int) {
        try {
            val streamItem = queue[index]
            updateCurrent(streamItem)
            onQueueTapListener.invoke(streamItem)
        } catch (e: Exception) {
            Log.e("Queue on tap", "lifecycle already ended")
        }
    }

    fun setOnQueueTapListener(listener: (StreamItem) -> Unit) {
        onQueueTapListener = listener
    }

    fun addOnTrackChangedListener(listener: (StreamItem) -> Unit) {
        onTrackChangedListeners.add(listener)
    }

    fun removeOnTrackChangedListener(listener: (StreamItem) -> Unit) {
        onTrackChangedListeners.remove(listener)
    }

    fun resetToDefaults() {
        repeatQueue = false
        onQueueTapListener = {}
        onTrackChangedListeners.clear()
    }

    fun mixSortStreams(streams: List<StreamItem>) : List<StreamItem> {
        val subscriptionsFeedLists = mutableListOf(listOf<StreamItem>())
        val feed = mutableListOf<StreamItem>()
        Log.d("Amit",         "mixSortStreams size" + streams.size    )
        run {
            streams.distinctBy { it.uploaderName }.map { it.uploaderName }
                .forEach { it1 ->
                    if (it1 != null) {
                        subscriptionsFeedLists.add(
                            streams.filter { it.uploaderName.equals(it1) }
                                .sortedBy { it.uploadedDate }.toMutableList())
                    }
                }



            Log.d(
                "Amit",
                "each video collection-subscriptionsFeedLists size" + subscriptionsFeedLists.size
            )

            // subscriptionsFeedLists.keys.parallelStream().forEach { feed.addAll(subscriptionsFeedLists.getValue(it)) }
            val maxElementsSubscriptions = subscriptionsFeedLists.maxOf { it.size }
            //Log.d("Amit", "maxElementsSubscriptions size - $maxElementsSubscriptions")
            var l = 0
            for (i in 0 until maxElementsSubscriptions step 1) {


                //Log.d("Amit", "i-$i")

                subscriptionsFeedLists.forEach { it1 ->
                    run {
                        //Log.d("Amit", "it1-$it1-l-$l")
                        if (i <= it1.size - 1) {
                            feed.add(l, it1[i])
                            l++
                            //Log.d("Amit", "feed-size" + feed.size)
                        } else {
                            return@forEach
                        }
                    }
                }
            }

    }
       Log.d("Amit", "mixSortStreams feed-size" + feed.size)
        return feed
    }
}
