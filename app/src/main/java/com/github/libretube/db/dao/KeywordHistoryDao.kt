package com.github.libretube.db.dao

import android.database.sqlite.SQLiteConstraintException
import android.util.Log
import androidx.room.*
import com.github.libretube.db.obj.KeywordHistoryItem
import java.sql.Date


@Dao
interface KeywordHistoryDao {
    @Query("SELECT * FROM keywordHistoryItem")
    suspend fun getAll(): List<KeywordHistoryItem>

    @Query("SELECT * FROM keywordHistoryItem WHERE fetchLastTime > :dateVal")
    fun getOutdatedKeywords(dateVal: Date): List<KeywordHistoryItem>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(keywordHistoryItems: List<KeywordHistoryItem>)

    @Insert()
    suspend fun insertKeyword(keywordHistoryItem: KeywordHistoryItem)

    @Update()
    suspend fun updateKeyword(keywordHistoryItem: KeywordHistoryItem)

    @Query("SELECT * FROM keywordHistoryItem WHERE keyword = :id")
    suspend fun getKeywordHistoryItem(id: String): KeywordHistoryItem


    @Delete
    suspend fun delete(keywordHistoryItem: KeywordHistoryItem)

    @Query("DELETE FROM keywordHistoryItem")
    suspend fun deleteAll()

    @Query("DELETE FROM keywordHistoryItem WHERE  keyword = :id")
    suspend fun deleteByKeyword(id: String)

    @Query("DELETE FROM keywordHistoryItem WHERE touchCnt <5")
    suspend fun purge()

    @Query("SELECT * FROM keywordHistoryItem WHERE fetchLastTime < :fromDate")
    fun findKeywordByFetchLastTime(fromDate: Date): List<KeywordHistoryItem>

    suspend fun upsertKeyword(keywordHistoryItem: List<KeywordHistoryItem>) {
        keywordHistoryItem.forEach {
            try {
                Log.d("Amit","Inserting keyword - ${it.keyword}")
                insertKeyword(it)
            }
            catch (e: SQLiteConstraintException) {
                Log.d("Amit","updating keyword - ${it.keyword}")
                val existingKeyword = getKeywordHistoryItem(it.keyword)
                val touchCnt = it.touchCnt?.let { it1 -> existingKeyword.touchCnt?.plus(it1) }
                if (it.touchCnt!! < 1 ) { //Updating only recommendation size
                    updateKeyword(
                        KeywordHistoryItem(
                            it.keyword,
                            it.recommendSize,
                            existingKeyword.touchCnt,
                            Date(System.currentTimeMillis())
                        )
                    )
                } else {

                    updateKeyword(
                        KeywordHistoryItem(
                            it.keyword,
                            existingKeyword.recommendSize,
                            touchCnt,
                            existingKeyword.fetchLastTime
                        )
                    )
                }
            }
        }
    }
}