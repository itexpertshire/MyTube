package com.github.libretube.api.obj


import com.github.libretube.db.obj.RecommendStreamItem
import kotlinx.serialization.Serializable

@Serializable
data class ContentItem(
    val url: String,
    val type: String,
    val thumbnail: String,
    val uploaderName: String? = null,
    val uploaded: Long? = null,
    val shortDescription: String? = null,
    // Video only attributes
    val title: String? = null,
    val uploaderUrl: String? = null,
    val uploaderAvatar: String? = null,
    val uploadedDate: String? = null,
    val duration: Long = -1,
    val views: Long = -1,
    val isShort: Boolean? = null,
    val uploaderVerified: Boolean? = null,
    // Channel and Playlist attributes
    val name: String? = null,
    val description: String? = null,
    val subscribers: Long = -1,
    val videos: Long = -1,
    val verified: Boolean? = null
){
    fun toStreamItem(): StreamItem {
        return StreamItem(
        url=url,
        type=type,
        title=title,
        thumbnail=thumbnail,
        uploaderName=uploaderName,
        uploaderUrl=uploaderUrl,
        uploaderAvatar=uploaderAvatar,
        uploadedDate=uploadedDate,
        duration=duration,
        views=views,
        uploaderVerified=uploaderVerified,
        uploaded=uploaded,
        shortDescription=shortDescription,
        isShort= isShort!!
        )
    }

    fun toRecommendStreamItem(): RecommendStreamItem {
        return RecommendStreamItem(
            url=url,
            type=type,
            title=title,
            thumbnail=thumbnail,
            uploaderName=uploaderName,
            uploaderUrl=uploaderUrl,
            uploaderAvatar=uploaderAvatar,
            uploadedDate=uploadedDate,
            duration=duration,
            views=views,
            uploaderVerified=uploaderVerified,
            uploaded=uploaded,
            shortDescription=shortDescription,
            isShort= isShort!!
        )
    }
}
