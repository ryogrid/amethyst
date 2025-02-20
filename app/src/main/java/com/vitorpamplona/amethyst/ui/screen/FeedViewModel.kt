package com.vitorpamplona.amethyst.ui.screen

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.LocalCacheState
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.ui.components.BundledUpdate
import com.vitorpamplona.amethyst.ui.dal.BookmarkPrivateFeedFilter
import com.vitorpamplona.amethyst.ui.dal.BookmarkPublicFeedFilter
import com.vitorpamplona.amethyst.ui.dal.ChannelFeedFilter
import com.vitorpamplona.amethyst.ui.dal.ChatroomFeedFilter
import com.vitorpamplona.amethyst.ui.dal.ChatroomListKnownFeedFilter
import com.vitorpamplona.amethyst.ui.dal.ChatroomListNewFeedFilter
import com.vitorpamplona.amethyst.ui.dal.FeedFilter
import com.vitorpamplona.amethyst.ui.dal.GlobalFeedFilter
import com.vitorpamplona.amethyst.ui.dal.HashtagFeedFilter
import com.vitorpamplona.amethyst.ui.dal.HomeConversationsFeedFilter
import com.vitorpamplona.amethyst.ui.dal.HomeNewThreadFeedFilter
import com.vitorpamplona.amethyst.ui.dal.ThreadFeedFilter
import com.vitorpamplona.amethyst.ui.dal.UserProfileBookmarksFeedFilter
import com.vitorpamplona.amethyst.ui.dal.UserProfileConversationsFeedFilter
import com.vitorpamplona.amethyst.ui.dal.UserProfileNewThreadFeedFilter
import com.vitorpamplona.amethyst.ui.dal.UserProfileReportsFeedFilter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class NostrChannelFeedViewModel : FeedViewModel(ChannelFeedFilter)
class NostrChatRoomFeedViewModel : FeedViewModel(ChatroomFeedFilter)
class NostrGlobalFeedViewModel : FeedViewModel(GlobalFeedFilter)
class NostrThreadFeedViewModel : FeedViewModel(ThreadFeedFilter)
class NostrHashtagFeedViewModel : FeedViewModel(HashtagFeedFilter)
class NostrUserProfileNewThreadsFeedViewModel : FeedViewModel(UserProfileNewThreadFeedFilter)
class NostrUserProfileConversationsFeedViewModel : FeedViewModel(UserProfileConversationsFeedFilter)
class NostrUserProfileReportFeedViewModel : FeedViewModel(UserProfileReportsFeedFilter)
class NostrUserProfileBookmarksFeedViewModel : FeedViewModel(UserProfileBookmarksFeedFilter)
class NostrChatroomListKnownFeedViewModel : FeedViewModel(ChatroomListKnownFeedFilter)
class NostrChatroomListNewFeedViewModel : FeedViewModel(ChatroomListNewFeedFilter)
class NostrHomeFeedViewModel : FeedViewModel(HomeNewThreadFeedFilter)
class NostrHomeRepliesFeedViewModel : FeedViewModel(HomeConversationsFeedFilter)

class NostrBookmarkPublicFeedViewModel : FeedViewModel(BookmarkPublicFeedFilter)
class NostrBookmarkPrivateFeedViewModel : FeedViewModel(BookmarkPrivateFeedFilter)

abstract class FeedViewModel(val localFilter: FeedFilter<Note>) : ViewModel() {
    private val _feedContent = MutableStateFlow<FeedState>(FeedState.Loading)
    val feedContent = _feedContent.asStateFlow()

    fun newListFromDataSource(): List<Note> {
        return localFilter.loadTop()
    }

    private fun refresh() {
        val scope = CoroutineScope(Job() + Dispatchers.Default)
        scope.launch {
            refreshSuspended()
        }
    }

    fun refreshSuspended() {
        val notes = newListFromDataSource()

        val oldNotesState = feedContent.value
        if (oldNotesState is FeedState.Loaded) {
            // Using size as a proxy for has changed.
            if (notes != oldNotesState.feed.value) {
                updateFeed(notes)
            }
        } else {
            updateFeed(notes)
        }
    }

    private fun updateFeed(notes: List<Note>) {
        val scope = CoroutineScope(Job() + Dispatchers.Main)
        scope.launch {
            val currentState = feedContent.value
            if (notes.isEmpty()) {
                _feedContent.update { FeedState.Empty }
            } else if (currentState is FeedState.Loaded) {
                // updates the current list
                currentState.feed.value = notes
            } else {
                _feedContent.update { FeedState.Loaded(mutableStateOf(notes)) }
            }
        }
    }

    private val bundler = BundledUpdate(250, Dispatchers.IO) {
        // adds the time to perform the refresh into this delay
        // holding off new updates in case of heavy refresh routines.
        refreshSuspended()
    }

    fun invalidateData() {
        bundler.invalidate()
    }

    private val cacheListener: (LocalCacheState) -> Unit = {
        invalidateData()
    }

    init {
        LocalCache.live.observeForever(cacheListener)
    }

    override fun onCleared() {
        LocalCache.live.removeObserver(cacheListener)
        super.onCleared()
    }
}
