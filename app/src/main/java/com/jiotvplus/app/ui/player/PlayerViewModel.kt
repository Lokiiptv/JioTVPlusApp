package com.jiotvplus.app.ui.player

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jiotvplus.app.data.model.Channel
import com.jiotvplus.app.data.prefs.TokenStore
import com.jiotvplus.app.data.repository.ChannelRepository
import com.jiotvplus.app.util.PlayNextManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class StreamConfig(
    val url: String,
    val headers: Map<String, String>,
    val isDash: Boolean = false,
    val keyUrl: String? = null
)

data class TrackInfo(
    val audioTracks: List<Track> = emptyList(),
    val videoTracks: List<Track> = emptyList(),
    val selectedAudioIndex: Int = 0,
    val selectedVideoIndex: Int = 0
)

data class Track(
    val index: Int,
    val label: String,
    val language: String = "",
    val bitrate: Int = 0,
    val height: Int = 0,
    val width: Int = 0
)

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val channelRepository: ChannelRepository,
    private val tokenStore: TokenStore,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    private val _streamConfig = MutableStateFlow<StreamConfig?>(null)
    private val _isLoading = MutableStateFlow(false)
    private val _error = MutableStateFlow<String?>(null)
    private val _channelName = MutableStateFlow("")
    private val _currentProgram = MutableStateFlow("")
    private val _channelList = MutableStateFlow<List<Channel>>(emptyList())
    private val _currentContentId = MutableStateFlow("")
    private val _trackInfo = MutableStateFlow<TrackInfo?>(null)

    val streamConfig: StateFlow<StreamConfig?> = _streamConfig.asStateFlow()
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    val error: StateFlow<String?> = _error.asStateFlow()
    val channelName: StateFlow<String> = _channelName.asStateFlow()
    val currentProgram: StateFlow<String> = _currentProgram.asStateFlow()
    val channelList: StateFlow<List<Channel>> = _channelList.asStateFlow()
    val currentContentId: StateFlow<String> = _currentContentId.asStateFlow()
    val trackInfo: StateFlow<TrackInfo?> = _trackInfo.asStateFlow()

    init {
        loadChannelList()
    }

    private fun loadChannelList() {
        viewModelScope.launch {
            channelRepository.getChannels()
                .onSuccess {
                    _channelList.value = it
                    // If launched via deep link with blank name, fill it from the list
                    val cid = _currentContentId.value
                    if (_channelName.value.isBlank() && cid.isNotBlank()) {
                        it.find { ch -> ch.contentId == cid }?.let { ch ->
                            _channelName.value = ch.name
                            _currentProgram.value = ch.currentProgram?.title ?: ""
                        }
                    }
                }
        }
    }

    fun loadChannel(contentId: String, channelName: String, currentProgram: String) {
        _channelName.value = channelName
        _currentProgram.value = currentProgram
        _currentContentId.value = contentId
        _streamConfig.value = null
        _error.value = null
        _trackInfo.value = null

        viewModelScope.launch {
            _isLoading.value = true
            channelRepository.getPlaybackStream(contentId)
                .onSuccess { config ->
                    _streamConfig.value = config
                    trackRecentlyPlayed(contentId)
                }
                .onFailure { e ->
                    _error.value = e.message ?: "Failed to load channel"
                }
            _isLoading.value = false
        }
    }

    private suspend fun trackRecentlyPlayed(contentId: String) {
        val recent = tokenStore.getRecentChannels().toMutableList()
        recent.remove(contentId)
        recent.add(0, contentId)
        tokenStore.saveRecentChannels(recent.take(20))

        // Add to Android TV "Play Next" row
        val channel = _channelList.value.find { it.contentId == contentId }
        if (channel != null) {
            PlayNextManager.updateWatchNext(appContext, channel)
        }
    }

    fun switchChannel(contentId: String) {
        val channel = _channelList.value.find { it.contentId == contentId } ?: return
        loadChannel(contentId, channel.name, channel.currentProgram?.title ?: "")
    }

    fun updateTrackInfo(
        audioTracks: List<Track>,
        videoTracks: List<Track>,
        selectedAudioIndex: Int,
        selectedVideoIndex: Int
    ) {
        _trackInfo.value = TrackInfo(audioTracks, videoTracks, selectedAudioIndex, selectedVideoIndex)
    }

    fun clearError() {
        _error.value = null
    }

    fun setPlayerError(message: String) {
        _error.value = message
    }
}
