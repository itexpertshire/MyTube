package com.github.libretube.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.github.libretube.api.obj.StreamItem
import com.github.libretube.db.obj.RecommendStreamItem

@Dao
interface RecommendStreamItemDao {
    @Query("SELECT * FROM recommendStreamItem")
    suspend fun getAll(): List<StreamItem>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(recommendStreamItems: List<RecommendStreamItem>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(recommendStreamItems: RecommendStreamItem)

    @Delete
    suspend fun delete(recommendStreamItems: RecommendStreamItem)

    @Query("DELETE FROM recommendStreamItem where url='/watch?v='||:id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM recommendStreamItem where title=:title")
    suspend fun deleteByTitle(title: String)

    @Query("SELECT * FROM recommendStreamItem where url='/watch?v='||:id")
    suspend fun getById(id: String): StreamItem

    @Query("DELETE FROM recommendStreamItem")
    suspend fun deleteAll()



}
