package com.github.libretube.db.dao


import androidx.room.*
import com.github.libretube.db.obj.BlockListItem
import java.sql.Date


@Dao
interface BlockListDao {
    @Query("SELECT * FROM BlockListItem")
    suspend fun getAll(): List<BlockListItem>

    @Query("SELECT * FROM BlockListItem WHERE updatedTime < datetime('now', 'now', '-1 day')")
    fun getOutdatedKeywords(): List<BlockListItem>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(blockListItems: List<BlockListItem>)

    @Insert()
    suspend fun insertBlockList(blockListItem: BlockListItem)

    @Update()
    suspend fun updateBlockList(blockListItem: BlockListItem)

    @Query("SELECT * FROM BlockListItem WHERE id == :id")
    suspend fun getBlockListItem(id: String): BlockListItem



    @Delete
    suspend fun delete(blockListItem: BlockListItem)

    @Query("DELETE FROM BlockListItem")
    suspend fun deleteAll()

    @Query("DELETE FROM BlockListItem WHERE touchCnt <5")
    suspend fun purge()

    @Query("SELECT * FROM BlockListItem WHERE updatedTime < :fromDate")
    fun findBlockListByTime(fromDate: Date): List<BlockListItem>
/*
    suspend fun upsertKeyword(keywordHistoryItem: List<BlockListItem>) {
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
                        BlockListItem(
                            it.keyword,
                            it.recommendSize,
                            existingKeyword.touchCnt,
                            Date(System.currentTimeMillis())
                        )
                    )
                } else {

                    updateKeyword(
                        BlockListItem(
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

    */
}
