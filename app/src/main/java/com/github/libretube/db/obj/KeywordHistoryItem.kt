package com.github.libretube.db.obj

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.sql.Date


@Entity(tableName = "keywordHistoryItem")
data class KeywordHistoryItem(
    @PrimaryKey val keyword: String = "",
    @ColumnInfo val recommendSize: String? = null,
    @ColumnInfo val touchCnt: Int? = 0,
    @ColumnInfo val fetchLastTime: Date = Date(System.currentTimeMillis())
)
