package com.jiotvplus.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jiotvplus.app.data.model.Channel
import com.jiotvplus.app.data.model.GenreItem
import com.jiotvplus.app.data.prefs.TokenStore
import com.jiotvplus.app.data.repository.AuthRepository
import com.jiotvplus.app.data.repository.ChannelRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val channelRepository: ChannelRepository,
    private val authRepository: AuthRepository,
    private val tokenStore: TokenStore
) : ViewModel() {

    private val _allChannels = MutableStateFlow<List<Channel>>(emptyList())
    private val _genreFilters = MutableStateFlow<List<GenreItem>>(emptyList())
    private val _selectedGenre = MutableStateFlow("All")
    private val _selectedLanguage = MutableStateFlow("All")
    private val _isLoading = MutableStateFlow(false)
    private val _error = MutableStateFlow<String?>(null)
    private val _recentChannels = MutableStateFlow<List<Channel>>(emptyList())
    private val _preferredLanguages = MutableStateFlow<Set<String>>(emptySet())
    private val _searchQuery = MutableStateFlow("")

    val genreFilters: StateFlow<List<GenreItem>> = _genreFilters.asStateFlow()
    val selectedGenre: StateFlow<String> = _selectedGenre.asStateFlow()
    val selectedLanguage: StateFlow<String> = _selectedLanguage.asStateFlow()
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    val error: StateFlow<String?> = _error.asStateFlow()
    val recentChannels: StateFlow<List<Channel>> = _recentChannels.asStateFlow()
    val preferredLanguages: StateFlow<Set<String>> = _preferredLanguages.asStateFlow()
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val filteredChannels: StateFlow<List<Channel>> = combine(
        _allChannels, _selectedGenre, _selectedLanguage, _preferredLanguages, _searchQuery
    ) { channels, genre, language, preferred, query ->
        channels.filter { channel ->
            val genreMatch = genre == "All" || channel.genres.any { it.equals(genre, ignoreCase = true) }
            val langMatch = language == "All" || channel.language.equals(language, ignoreCase = true)
            val prefMatch = preferred.isEmpty() || preferred.any { pl ->
                channel.language.equals(pl, ignoreCase = true)
            }
            val searchMatch = query.isBlank() || run {
                val q = query.lowercase().trim()
                channel.name.lowercase().contains(q) ||
                channel.genres.any { it.lowercase().contains(q) } ||
                channel.language.lowercase().contains(q) ||
                channel.contentId.lowercase().contains(q)
            }
            genreMatch && langMatch && prefMatch && searchMatch
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val availableLanguages: StateFlow<List<String>> = combine(
        _genreFilters, _selectedGenre
    ) { filters, genre ->
        filters.find { it.genre.equals(genre, ignoreCase = true) }?.languages
            ?: filters.find { it.genre == "All" }?.languages
            ?: emptyList()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        loadPreferences()
        loadData()
    }

    private fun loadPreferences() {
        viewModelScope.launch {
            _preferredLanguages.value = tokenStore.getLanguages()
            val recentIds = tokenStore.getRecentChannels()
            if (recentIds.isNotEmpty() && _allChannels.value.isNotEmpty()) {
                updateRecentChannels(recentIds)
            }
        }
    }

    private fun updateRecentChannels(recentIds: List<String>) {
        val allChannels = _allChannels.value
        _recentChannels.value = recentIds.mapNotNull { id ->
            allChannels.find { it.contentId == id }
        }
    }

    fun loadData() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            val channelsDeferred = async { channelRepository.getChannels() }
            val filtersDeferred = async { channelRepository.getGenreFilters() }
            val recentDeferred = async { tokenStore.getRecentChannels() }

            channelsDeferred.await()
                .onSuccess {
                    _allChannels.value = it
                    val recentIds = recentDeferred.await()
                    updateRecentChannels(recentIds)
                }
                .onFailure { _error.value = it.message }

            filtersDeferred.await()
                .onSuccess { _genreFilters.value = it }
                .onFailure { }

            _isLoading.value = false
        }
    }

    fun selectGenre(genre: String) {
        _selectedGenre.value = genre
        _selectedLanguage.value = "All"
    }

    fun selectLanguage(language: String) {
        _selectedLanguage.value = language
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun logout(onDone: () -> Unit) {
        viewModelScope.launch {
            authRepository.logout()
            onDone()
        }
    }
}
