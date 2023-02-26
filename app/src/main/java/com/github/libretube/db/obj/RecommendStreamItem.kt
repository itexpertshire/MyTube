package com.github.libretube.db.obj

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "recommendStreamItem",
    indices = [Index(value = ["url"], unique = true)]
)
data class RecommendStreamItem(
    @PrimaryKey val url: String ="",
    val type: String? = null,
    val title: String? = null,
    val thumbnail: String? = null,
    val uploaderName: String? = null,
    val uploaderUrl: String? = null,
    val uploaderAvatar: String? = null,
    val uploadedDate: String? = null,
    val duration: Long? = null,
    val views: Long? = null,
    val uploaderVerified: Boolean? = null,
    val uploaded: Long? = null,
    val shortDescription: String? = null,
    val isShort: Boolean = false
)
