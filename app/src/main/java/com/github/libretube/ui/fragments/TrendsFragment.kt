package com.github.libretube.ui.fragments

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.github.libretube.R
import com.github.libretube.api.RetrofitInstance
import com.github.libretube.databinding.FragmentTrendsBinding
import com.github.libretube.extensions.TAG
import com.github.libretube.helpers.LocaleHelper
import com.github.libretube.ui.activities.SettingsActivity
import com.github.libretube.ui.adapters.VideosAdapter
import com.github.libretube.ui.base.BaseFragment
import com.google.android.material.snackbar.Snackbar
import retrofit2.HttpException
import java.io.IOException

class TrendsFragment : BaseFragment() {
    lateinit var binding: FragmentTrendsBinding
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentTrendsBinding.inflate(layoutInflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)



       //fetchTrending()
        binding.homeRefresh.isEnabled = true
        binding.homeRefresh.setOnRefreshListener {
       //fetchTrending()


        }


    }

    private fun fetchTrending() {
        lifecycleScope.launchWhenCreated {
            val response = try {
                RetrofitInstance.api.getTrending(
                    LocaleHelper.getTrendingRegion(requireContext())
                )
            } catch (e: IOException) {
                println(e)
                Log.e(TAG(), "IOException, you might not have internet connection")
                Toast.makeText(context, R.string.unknown_error, Toast.LENGTH_SHORT).show()
                return@launchWhenCreated
            } catch (e: HttpException) {
                Log.e(TAG(), "HttpException, unexpected response")
                Toast.makeText(context, R.string.server_error, Toast.LENGTH_SHORT).show()
                return@launchWhenCreated
            } finally {
                binding.homeRefresh.isRefreshing = false
            }
            runOnUiThread {
                binding.progressBar.visibility = View.GONE

                // show a [SnackBar] if there are no trending videos available
                if (response.isEmpty()) {
                    Snackbar.make(
                        binding.root,
                        R.string.change_region,
                        Snackbar.LENGTH_LONG
                    )
                        .setAction(
                            R.string.settings
                        ) {
                            startActivity(
                                Intent(
                                    context,
                                    SettingsActivity::class.java
                                )
                            )
                        }
                        .show()
                    return@runOnUiThread
                }

                binding.recview.adapter = VideosAdapter(
                    response.toMutableList()
                )

                binding.recview.layoutManager = VideosAdapter.getLayout(requireContext())
            }
        }
    }

}
