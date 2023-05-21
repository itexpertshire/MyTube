package com.github.libretube.ui.models

import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.libretube.api.RetrofitInstance
import com.github.libretube.api.obj.CommentsPage
import com.github.libretube.extensions.TAG
import com.github.libretube.ui.extensions.filterNonEmptyComments
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class CommentsViewModel : ViewModel() {
    private var isLoading = false

    val commentsPage = MutableLiveData<CommentsPage?>()

    val commentSheetExpand = MutableLiveData<Boolean?>()

    var currentCommentsPosition = 0

    fun setCommentSheetExpand(value: Boolean?) {
        if (commentSheetExpand.value != value) {
            commentSheetExpand.value = value
        }
    }

    private var nextPage: String? = null

    var videoId: String? = null
    var maxHeight: Int = 0
    var commentsSheetDismiss: (() -> Unit)? = null
    var handleLink: ((url: String) -> Unit)? = null

    fun fetchComments() {
        videoId ?: return
        viewModelScope.launch(Dispatchers.IO) {
            isLoading = true
            val response = try {
                RetrofitInstance.api.getComments(videoId!!)
            } catch (e: Exception) {
                Log.e(TAG(), e.toString())
                return@launch
            }
            nextPage = response.nextpage
            response.comments = response.comments.filterNonEmptyComments()
            commentsPage.postValue(response)
            isLoading = false
        }
    }

    fun fetchNextComments() {
        if (isLoading || nextPage == null || videoId == null) return
        viewModelScope.launch(Dispatchers.IO) {
            isLoading = true
            val response = try {
                RetrofitInstance.api.getCommentsNextPage(videoId!!, nextPage!!)
            } catch (e: Exception) {
                Log.e(TAG(), e.toString())
                return@launch
            }
            val updatedPage = commentsPage.value?.apply {
                comments += response.comments.filterNonEmptyComments()
            }
            nextPage = response.nextpage
            commentsPage.postValue(updatedPage)
            isLoading = false
        }
    }

    fun reset() {
        isLoading = false
        nextPage = null
        commentsPage.value = null
        videoId = null
        setCommentSheetExpand(null)
        currentCommentsPosition = 0
    }
}
