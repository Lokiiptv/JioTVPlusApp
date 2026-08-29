package com.jiotvplus.app.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jiotvplus.app.data.model.Channel
import com.jiotvplus.app.data.repository.ChannelRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val channelRepository: ChannelRepository
) : ViewModel() {

    private val _results = MutableStateFlow<List<Channel>>(emptyList())
    private val _isLoading = MutableStateFlow(false)

    val results: StateFlow<List<Channel>> = _results.asStateFlow()
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    fun search(query: String) {
        if (query.isBlank()) return
        viewModelScope.launch {
            _isLoading.value = true
            channelRepository.getChannels()
                .onSuccess { channels ->
                    val q = query.lowercase().trim()
                    _results.value = channels.filter { ch ->
                        ch.name.lowercase().contains(q) ||
                        ch.genres.any { it.lowercase().contains(q) } ||
                        ch.language.lowercase().contains(q) ||
                        ch.contentId.lowercase().contains(q)
                    }
                }
            _isLoading.value = false
        }
    }
}
