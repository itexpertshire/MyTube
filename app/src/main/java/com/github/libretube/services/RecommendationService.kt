package com.github.libretube.services

import android.annotation.SuppressLint
import android.app.job.JobParameters
import android.app.job.JobService
import android.os.AsyncTask
import android.util.Log
import com.github.libretube.api.RetrofitInstance
import com.github.libretube.constants.RECOMMENDATION_JOB_STATUS
import com.github.libretube.constants.RECOMMENDATION_PER_KEYWORD_MAX_CNT
import com.github.libretube.constants.RECOMMENDATION_VIDEO_MAX_CNT
import com.github.libretube.db.DatabaseHelper
import com.github.libretube.db.DatabaseHolder
import com.github.libretube.db.obj.BlockListItem
import com.github.libretube.db.obj.KeywordHistoryItem
import com.github.libretube.extensions.TAG
import com.github.libretube.helpers.PreferenceHelper
import kotlinx.coroutines.runBlocking
import retrofit2.HttpException
import java.io.IOException



@SuppressLint("SpecifyJobSchedulerIdRange")
class RecommendationService : JobService() {

    lateinit var params: JobParameters
    lateinit var task: RecommendTask
    var TAG = RecommendationService::class.java.simpleName

    // Whenever the contraints are satisfied this will get fired.
    override fun onStartJob(params: JobParameters?): Boolean {
        // We land here when system calls our job.
        this.params = params!!

        task = RecommendTask(this)          // Not the best way in prod.
        task.execute(Unit)
        PreferenceHelper.putString(RECOMMENDATION_JOB_STATUS,"Job Started")
        return true     // Our task will run in background, we will take care of notifying the finish.
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        task.cancel(true)       // Cancel the counter task.
        Log.d(RecommendationService::class.java.simpleName, "Job paused.")
        PreferenceHelper.putString(RECOMMENDATION_JOB_STATUS,"Job paused")
        return true
        // I want it to reschedule so returned true, if we would have returned false, then job would have ended here.
        // It would not fire onStartJob() when constraints are re satisfied.
    }


    fun notifyJobFinished() {
        Log.d(RecommendationService::class.java.simpleName,"Job finished. Calling jobFinished()")
        PreferenceHelper.putString(RECOMMENDATION_JOB_STATUS,"Job Finished & Rescheduled")
        // Try to fetch a preference.
        //prefs.edit().putInt(SAVED_INT_KEY,0).apply()
        // Job has finished now, calling jobFinishedI(false) will release all resources and
        // false as we do not want it to reschedule as the job is done now.
        jobFinished(params,false)
    }




    /**
     * Task which performs the counting with added delay. Before it starts, it picks up the value
     * which has been already printed from previous onStartJob() calls.
     */
    class RecommendTask(@SuppressLint("StaticFieldLeak") private val params: RecommendationService) : AsyncTask<Unit,Int,Unit>()  {
        //private val LIMIT = 100
       // private var start = 0

        override fun onPreExecute() {
            super.onPreExecute()
            // Pick the last value which was saved in the last execution and continue from there.

        }
        override  fun doInBackground(vararg params: Unit?) {
            var existingRecommendVideoSize = 0

            var keywordHistory : List<KeywordHistoryItem>
            var blockedList : List<BlockListItem> //List of blocked keywords

            runBlocking {
                blockedList = DatabaseHolder.Database.blockListDao().getAll()
                //Removed blocked keywords from keyword table
                blockedList.forEach { it ->  DatabaseHolder.Database.keywordHistoryDao().deleteByKeyword(it.id)
                                             DatabaseHolder.Database.recommendStreamItemDao().deleteByTitle(it.id)
                }
                keywordHistory = DatabaseHolder.Database.keywordHistoryDao().getOutdatedKeywords()

                Log.d("Amit","$keywordHistory")

                existingRecommendVideoSize = DatabaseHolder.Database.recommendStreamItemDao().getAll().size
            }
            if (keywordHistory.isEmpty() or  (existingRecommendVideoSize > RECOMMENDATION_VIDEO_MAX_CNT)) {
                Log.d("Amit", "keyword list is empty or Recommended video list is reached to max value - $existingRecommendVideoSize")
            } else {

                Log.d("Amit", "keyword search starting")

                keywordHistory.forEach { it ->

                    for (q in it.keyword?.split(",")!!) {
                        Log.d("Amit", "Search - $q")
                        if (q.isNotEmpty()) {
                            try {
                                runBlocking {
                                    val response =
                                        RetrofitInstance.api.getSearchResults(q, "videos")
                                            val responseFilteredItems = response.items.filter { it1 -> it1.verified == true && it1.views > 10000 && it1.uploaderVerified == true && it1.subscribers > 500000 }

                                           //Remove titles from recommendation list if it's present in blocked list
                                    responseFilteredItems.dropWhile { it -> it.title?.let { it1-> DatabaseHelper.isBlocked(it1)} == true }
                                    Log.d("Amit", "Search Result" + responseFilteredItems.size.toString())

                                    if (responseFilteredItems.size < RECOMMENDATION_PER_KEYWORD_MAX_CNT){

                                        DatabaseHolder.Database.keywordHistoryDao().upsertKeyword(
                                            listOf(
                                                KeywordHistoryItem(
                                                    q,
                                                    responseFilteredItems.size.toString(),
                                                    0
                                                )
                                            )
                                        )
                                        //Saving in local Database
                                        DatabaseHelper.addRecommendation(
                                            responseFilteredItems.map { it2 -> it2.toRecommendStreamItem() }
                                                .toMutableList())

                                    } else {
                                        DatabaseHolder.Database.keywordHistoryDao().upsertKeyword(
                                            listOf(
                                                KeywordHistoryItem(
                                                    q,
                                                    RECOMMENDATION_PER_KEYWORD_MAX_CNT.toString(),
                                                    0
                                                )
                                            )
                                        )
                                        //Saving in local Database
                                        DatabaseHelper.addRecommendation(
                                            responseFilteredItems.subList(
                                                0,RECOMMENDATION_PER_KEYWORD_MAX_CNT-1
                                            ).map { it2 -> it2.toRecommendStreamItem() }
                                                .toMutableList())
                                    }
                                }

                            } catch (e: IOException) {
                                println(e)
                                Log.e(TAG(), "IOException, you might not have internet connection")

                            } catch (e: HttpException) {
                                Log.e(TAG(), "HttpException, unexpected response")

                            } finally {
                                //
                            }

                        }
                    }
                }
            }
        }

        // Write the completed status after each work is finished.
        override fun onProgressUpdate(vararg values: Int?) {
            Log.d(RecommendationService::class.java.simpleName, "Counter value: ${values[0]}")
           // val prefs = params.getSharedPreferences("deep_service", Context.MODE_PRIVATE)
            // Try to fetch a preference and add current progress.
           // values[0]?.let { prefs.edit().putInt(SAVED_INT_KEY, it).commit() }
        }

        // Once job is finished, reset the preferences.
        override fun onPostExecute(result: Unit?) {
            params.notifyJobFinished()
        }
    }
}
