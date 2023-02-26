package com.github.libretube.db

import android.util.Log
import com.github.libretube.api.obj.Streams
import com.github.libretube.constants.KEYWORD_HISTORY_SIZE
import com.github.libretube.constants.PreferenceKeys
import com.github.libretube.db.DatabaseHolder.Database
import com.github.libretube.db.obj.*
import com.github.libretube.extensions.query
import com.github.libretube.extensions.toID
import com.github.libretube.helpers.PreferenceHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext


object DatabaseHelper {
    private const val MAX_SEARCH_HISTORY_SIZE = 20

    suspend fun addToWatchHistory(videoId: String, streams: Streams) = withContext(Dispatchers.IO) {
        val watchHistoryItem = WatchHistoryItem(
            videoId,
            streams.title,
            streams.uploadDate.toString(),
            streams.uploader,
            streams.uploaderUrl.toID(),
            streams.uploaderAvatar,
            streams.thumbnailUrl,
            streams.duration
        )

        //Also remove from recommendation table
        Database.recommendStreamItemDao().deleteById(videoId)

        Database.watchHistoryDao().insertAll(listOf(watchHistoryItem))
        val maxHistorySize = PreferenceHelper.getString(PreferenceKeys.WATCH_HISTORY_SIZE, "100")
        if (maxHistorySize == "unlimited") {
            return@withContext
        }

        // delete the first watch history entry if the limit is reached
        val watchHistory = Database.watchHistoryDao().getAll()
        if (watchHistory.size > maxHistorySize.toInt()) {
            Database.watchHistoryDao().delete(watchHistory.first())
        }
    }

    fun addToSearchHistory(searchHistoryItem: SearchHistoryItem) {
        query {
            Database.searchHistoryDao().insertAll(searchHistoryItem)

            // delete the first watch history entry if the limit is reached
            val searchHistory = Database.searchHistoryDao().getAll()
            if (searchHistory.size > MAX_SEARCH_HISTORY_SIZE) {
                Database.searchHistoryDao()
                    .delete(searchHistory.first())
            }
        }
    }


    suspend fun addToKeywordHistory(videoId: String, streams: Streams) = withContext(Dispatchers.IO) {
        if (Database.keywordHistoryDao().getAll().size < KEYWORD_HISTORY_SIZE) {
            var title = ""
            Log.d("Amit", "addToKeywordHistory")
            val regex = """(#[\w]+)""".toRegex()
            val text = streams.description.replace(System.getProperty("line.separator"), " ")
            //println("Regex Hashtag-$text")
            var matchResult = regex.findAll(text)
            var listKeywords = mutableListOf(KeywordHistoryItem("", "", 0))
            listKeywords.removeAt(0)
            matchResult.map { it.groupValues[1] }
                .forEach { it1 -> listKeywords.add(KeywordHistoryItem(it1, "", 1)) }


            Log.d("Amit", "matchResult->listKeywords $listKeywords")

            if (listKeywords.size >0) {
                Database.keywordHistoryDao().upsertKeyword(listKeywords)
            }

        }
        else {
            //Delete least used keywords
            Database.keywordHistoryDao().purge()
        }

        val maxHistorySize = PreferenceHelper.getString(PreferenceKeys.WATCH_HISTORY_SIZE, "100")
        if (maxHistorySize == "unlimited") {
            return@withContext
        }

        // delete the first watch history entry if the limit is reached
        val keywordHistory = Database.keywordHistoryDao().getAll()
        if (keywordHistory.size > maxHistorySize.toInt()) {
            Database.keywordHistoryDao().delete(keywordHistory.first())
        }
    }

    suspend fun isBlocked(title: String) : Boolean = withContext(Dispatchers.IO) {
        if (Database.blockListDao().getBlockListItem(title).id.isNotEmpty()){
            return@withContext true
            }
        else {
            return@withContext false
        }
    }
    suspend fun addToBlockList(videoId: String) = withContext(Dispatchers.IO) {
        val stream = Database.recommendStreamItemDao().getById(videoId)
        if (stream.shortDescription?.isNotEmpty() == true) {
            var title = ""
            Log.d("Amit", "addToBlockList")
            val regex = """(#[\w]+)""".toRegex()
            val text = Database.recommendStreamItemDao().getById(videoId).shortDescription!!.replace(System.getProperty("line.separator"), " ")
            //println("Regex Hashtag-$text")
            var matchResult = regex.findAll(text)
            var listBlocked = mutableListOf(BlockListItem("", "", 0))
            listBlocked.removeAt(0)
            matchResult.map { it.groupValues[1] }
                .forEach { it1 -> listBlocked.add(BlockListItem(it1, "hashtag", 1)) }


            Log.d("Amit", "Blocked->listBlocked $listBlocked")

            if (listBlocked.size >0) {
                Database.blockListDao().insertAll(listBlocked)
            } else {
                Database.blockListDao().insertAll(listOf(stream.title?.let { BlockListItem(it,"title",1) }) as List<BlockListItem>)
            }

        }
    }

    suspend fun addRecommendation(streamItem: List<RecommendStreamItem>) = withContext(Dispatchers.IO) {
        Log.d("Amit","Recommendation Table Count- "+Database.recommendStreamItemDao().getAll().size)
        //if item is already in watched list then don't add into recommendation
        streamItem.forEach { it ->
            //get the video id
            val videoId = it.url.replace("/watch?v=","")
            if (Database.watchHistoryDao().getVideo(videoId).videoId.isNotEmpty()) {
                return@forEach
            }
            Database.recommendStreamItemDao().insert(it)
        }


    }
}
