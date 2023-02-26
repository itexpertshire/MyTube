package com.github.libretube.db.obj

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.sql.Date


@Entity(tableName = "BlockListItem")
data class BlockListItem(
    @PrimaryKey val id: String = "",
    @ColumnInfo val type: String? = null, //keyword, hashtag, channel id
    @ColumnInfo val touchCnt: Int? = 0,
    @ColumnInfo val updatedTime: Date = Date(System.currentTimeMillis())
)
