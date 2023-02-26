package com.github.libretube.ui.fragments

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.github.libretube.R
import com.github.libretube.api.PlaylistsHelper
import com.github.libretube.api.RetrofitInstance
import com.github.libretube.api.SubscriptionHelper
import com.github.libretube.constants.FEATURED_VIDEO_MAX_CNT
import com.github.libretube.constants.PreferenceKeys
import com.github.libretube.constants.RECOMMENDATION_VIDEO_MAX_CNT
import com.github.libretube.databinding.FragmentHomeBinding
import com.github.libretube.db.DatabaseHolder
import com.github.libretube.extensions.awaitQuery
import com.github.libretube.extensions.launchWhenCreatedIO
import com.github.libretube.helpers.LocaleHelper
import com.github.libretube.helpers.PreferenceHelper
import com.github.libretube.ui.adapters.PlaylistBookmarkAdapter
import com.github.libretube.ui.adapters.PlaylistsAdapter
import com.github.libretube.ui.adapters.VideosAdapter
import com.github.libretube.ui.base.BaseFragment
import com.github.libretube.ui.models.SubscriptionsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class HomeFragment : BaseFragment() {
    lateinit var binding: FragmentHomeBinding
    private val subscriptionsViewModel: SubscriptionsViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentHomeBinding.inflate(layoutInflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.featuredTV.setOnClickListener {
            findNavController().navigate(R.id.subscriptionsFragment)
        }

        binding.trendingTV.setOnClickListener {
            findNavController().navigate(R.id.trendsFragment)
        }

        binding.playlistsTV.setOnClickListener {
            findNavController().navigate(R.id.libraryFragment)
        }

        binding.bookmarksTV.setOnClickListener {
            findNavController().navigate(R.id.libraryFragment)
        }

        binding.refresh.setOnRefreshListener {
            binding.refresh.isRefreshing = true
            fetchHomeFeed()
        }

        fetchHomeFeed()
    }

    private fun fetchHomeFeed() {
        launchWhenCreatedIO {
            //loadTrending()
            loadRecommendationLocal()
            //loadBookmarks()

        }
        launchWhenCreatedIO {
            loadFeed()
            //loadPlaylists()
           // loadRecommendation()
        }
    }

    private suspend fun loadTrending() {
        val region = LocaleHelper.getTrendingRegion(requireContext())
        val trending = runCatching {
            RetrofitInstance.api.getTrending(region).take(10)
        }.getOrNull().takeIf { it?.isNotEmpty() == true } ?: return

        runOnUiThread {
            makeVisible(binding.trendingRV, binding.trendingTV)
            binding.trendingRV.layoutManager = GridLayoutManager(context, 2)
            binding.trendingRV.adapter = VideosAdapter(
                trending.toMutableList(),
                forceMode = VideosAdapter.Companion.ForceMode.TRENDING
            )
        }
    }

    private suspend fun loadRecommendationLocal() {
        Log.d("Amit","Function-loadRecommendationLocal")
        var recommAdapter: VideosAdapter? = null
        val recommendation = runCatching {
            DatabaseHolder.Database.recommendStreamItemDao().getAll().shuffled()
        }.getOrNull().takeIf { it?.isNotEmpty() == true } ?.take(RECOMMENDATION_VIDEO_MAX_CNT) ?: return

        recommAdapter= VideosAdapter(
            recommendation.toMutableList(),
            showAllAtOnce = false,
            hideWatched = PreferenceHelper.getBoolean(PreferenceKeys.HIDE_WATCHED_FROM_FEED, false)
        )

        runOnUiThread {
            makeVisible(binding.trendingRV, binding.trendingTV)
            binding.trendingRV.layoutManager = GridLayoutManager(context, 1)

            binding.trendingRV.adapter =recommAdapter

            binding.scroll.setOnScrollChangeListener(object: RecyclerView.OnScrollListener(),
                View.OnScrollChangeListener {
                override fun onScrollChange(p0: View?, p1: Int, p2: Int, p3: Int, p4: Int) {
                    //Log.d("Amit","setOnScrollChangeListener $p1 $p2 $p3 $p4")
                    if (!binding.scroll.canScrollVertically(1)) {
                        // scroll view is at bottom
                        //Log.d("Amit","scroll view is at bottom $p1 $p2 $p3 $p4")
                        //Log.d("Amit","recommendation.lastIndex -"+recommendation.lastIndex)
                        //if (recommendation.lastIndex == recommendation.size) return
                        recommAdapter.updateItems()
                    }
                }
            })

        }
    }





    private suspend fun loadFeed() {
        val savedFeed = withContext(Dispatchers.Main) {
            subscriptionsViewModel.videoFeed.value
        }
        val feed = if (
            PreferenceHelper.getBoolean(PreferenceKeys.SAVE_FEED, false) &&
            !savedFeed.isNullOrEmpty()
        ) { savedFeed } else {
            runCatching {
                SubscriptionHelper.getFeedCustomSort()
            }.getOrElse { return }
        }.takeIf { it.isNotEmpty() }?.take(FEATURED_VIDEO_MAX_CNT) ?: return

        //Log.d("Amit","custom sort feed-$feed")



        runOnUiThread {
            makeVisible(binding.featuredRV, binding.featuredTV)
            binding.featuredRV.layoutManager = LinearLayoutManager(
                context,
                LinearLayoutManager.HORIZONTAL,
                false
            )
            binding.featuredRV.adapter = VideosAdapter(
                feed.toMutableList(),
                forceMode = VideosAdapter.Companion.ForceMode.HOME
            )
        }
    }

    private fun loadBookmarks() {
        val bookmarkedPlaylists = awaitQuery {
            DatabaseHolder.Database.playlistBookmarkDao().getAll()
        }.takeIf { it.isNotEmpty() } ?: return

        runOnUiThread {
            makeVisible(binding.bookmarksTV, binding.bookmarksRV)
            binding.bookmarksRV.layoutManager = LinearLayoutManager(
                context,
                LinearLayoutManager.HORIZONTAL,
                false
            )
            binding.bookmarksRV.adapter = PlaylistBookmarkAdapter(
                bookmarkedPlaylists,
                PlaylistBookmarkAdapter.Companion.BookmarkMode.HOME
            )
        }
    }

    private suspend fun loadPlaylists() {
        val playlists = runCatching {
            PlaylistsHelper.getPlaylists().take(20)
        }.getOrNull().takeIf { it?.isNotEmpty() == true } ?: return

        runOnUiThread {
            makeVisible(binding.playlistsRV, binding.playlistsTV)
            binding.playlistsRV.layoutManager = LinearLayoutManager(context)
            binding.playlistsRV.adapter = PlaylistsAdapter(
                playlists.toMutableList(),
                PlaylistsHelper.getPrivatePlaylistType()
            )
            binding.playlistsRV.adapter?.registerAdapterDataObserver(object :
                RecyclerView.AdapterDataObserver() {
                override fun onItemRangeRemoved(positionStart: Int, itemCount: Int) {
                    super.onItemRangeRemoved(positionStart, itemCount)
                    if (itemCount == 0) {
                        binding.playlistsRV.visibility = View.GONE
                        binding.playlistsTV.visibility = View.GONE
                    }
                }
            })
        }
    }

    private fun makeVisible(vararg views: View) {
        views.forEach {
            it.visibility = View.VISIBLE
        }
        binding.progress.visibility = View.GONE
        binding.scroll.visibility = View.VISIBLE
        binding.refresh.isRefreshing = false
    }
}
