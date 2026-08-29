package com.jiotvplus.app.ui.home

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.jiotvplus.app.data.model.Channel
import com.jiotvplus.app.ui.theme.*
import com.jiotvplus.app.util.ScreenUtils
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    onChannelClick: (contentId: String, channelName: String, currentProgram: String) -> Unit,
    onLogout: () -> Unit,
    onLanguageSettings: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val channels by viewModel.filteredChannels.collectAsStateWithLifecycle()
    val genreFilters by viewModel.genreFilters.collectAsStateWithLifecycle()
    val selectedGenre by viewModel.selectedGenre.collectAsStateWithLifecycle()
    val selectedLanguage by viewModel.selectedLanguage.collectAsStateWithLifecycle()
    val availableLanguages by viewModel.availableLanguages.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val recentChannels by viewModel.recentChannels.collectAsStateWithLifecycle()
    val preferredLanguages by viewModel.preferredLanguages.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()

    var isSearching by remember { mutableStateOf(false) }
    var isListening by remember { mutableStateOf(false) }
    var wasVoiceSearch by remember { mutableStateOf(false) }
    val searchFieldFocus = remember { FocusRequester() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val voiceSearchLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        wasVoiceSearch = true
        android.util.Log.d("HomeScreen", "Voice search result: code=${result.resultCode}")
        // Keep listening overlay visible for minimum 2s so user sees feedback
        scope.launch {
            kotlinx.coroutines.delay(2000)
            isListening = false
            if (result.resultCode == Activity.RESULT_OK) {
                val spokenText = result.data
                    ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                    ?.firstOrNull()
                android.util.Log.d("HomeScreen", "Voice search text: $spokenText")
                if (!spokenText.isNullOrBlank()) {
                    viewModel.setSearchQuery(spokenText)
                    isSearching = true
                } else {
                    isSearching = true
                }
            } else {
                isSearching = true
            }
        }
    }

    // Store launch function in a ref so micPermissionLauncher can call it
    val doVoiceSearch = remember { mutableStateOf<(() -> Unit)?>(null) }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            android.util.Log.d("HomeScreen", "Mic permission granted")
            doVoiceSearch.value?.invoke()
        } else {
            android.util.Log.w("HomeScreen", "Mic permission denied — falling back to text search")
            isSearching = true
        }
    }

    fun startVoiceSearch() {
        val micGranted = androidx.core.content.ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.RECORD_AUDIO
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (micGranted) {
            doVoiceSearch.value?.invoke()
        } else {
            android.util.Log.d("HomeScreen", "Requesting mic permission")
            micPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
        }
    }

    // Assign the actual voice search implementation
    LaunchedEffect(Unit) {
        doVoiceSearch.value = {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak a channel name")
            }
            val pm = context.packageManager
            val activities = pm.queryIntentActivities(intent, 0)
            if (activities.isNullOrEmpty()) {
                android.util.Log.w("HomeScreen", "No speech recognition available — falling back to text search")
                isSearching = true
            } else {
                runCatching {
                    isListening = true
                    voiceSearchLauncher.launch(intent)
                }.onFailure { e ->
                    android.util.Log.w("HomeScreen", "Voice search launch failed: ${e.message}")
                    isListening = false
                    isSearching = true
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
    ) {
        // Top bar
        TopBar(
            onRefresh = { viewModel.loadData() },
            onLogout = { viewModel.logout(onLogout) },
            onLanguageSettings = onLanguageSettings,
            onSearchClick = {
                isSearching = !isSearching
                if (!isSearching) viewModel.setSearchQuery("")
            },
            onMicClick = { startVoiceSearch() },
            isSearching = isSearching,
            searchQuery = searchQuery,
            onSearchQueryChange = { viewModel.setSearchQuery(it) },
            searchFieldFocus = searchFieldFocus
        )

        // Auto-focus search field only when user manually opens text search
        // (not when voice search returns with results)
        LaunchedEffect(isSearching) {
            if (isSearching && !wasVoiceSearch) {
                searchFieldFocus.requestFocus()
            }
            if (isSearching) wasVoiceSearch = false
        }

        // "Listening..." overlay while voice recognition is active
        if (isListening) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xE6000000)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(
                        Icons.Default.Mic,
                        contentDescription = null,
                        tint = JioBlue,
                        modifier = Modifier.size(64.dp)
                    )
                    Text(
                        text = "Listening…",
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Speak a channel name",
                        color = OnSurfaceVariant,
                        fontSize = 16.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    CircularProgressIndicator(color = JioBlue, modifier = Modifier.size(32.dp))
                }
            }
        }

        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = JioBlue, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(16.dp))
                    Text("Loading channels…", color = OnSurfaceVariant)
                }
            }
        } else if (error != null && channels.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(error ?: "Unknown error", color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { viewModel.loadData() }) { Text("Retry") }
                }
            }
        } else {
            // Genre filter row
            if (genreFilters.isNotEmpty()) {
                FilterRow(
                    label = "Genre",
                    items = genreFilters.map { it.genre },
                    selected = selectedGenre,
                    onSelect = { viewModel.selectGenre(it) }
                )
            }

            // Channel count
            Text(
                text = "${channels.size} channels" +
                    if (preferredLanguages.isNotEmpty()) " (filtered: ${preferredLanguages.joinToString()})" else "",
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = OnSurfaceVariant
            )

            // Recently played channels row
            if (recentChannels.isNotEmpty()) {
                RecentChannelsRow(
                    channels = recentChannels,
                    onChannelClick = onChannelClick
                )
                Spacer(Modifier.height(8.dp))
            }

            // Channel grid — adaptive columns based on screen size
            val gridCols = remember { ScreenUtils.gridColumns(context) }
            LazyVerticalGrid(
                columns = GridCells.Fixed(gridCols),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(channels, key = { it.contentId }) { channel ->
                    ChannelCard(
                        channel = channel,
                        onClick = {
                            onChannelClick(
                                channel.contentId,
                                channel.name,
                                channel.currentProgram?.title ?: ""
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun TopBar(
    onRefresh: () -> Unit,
    onLogout: () -> Unit,
    onLanguageSettings: () -> Unit,
    onSearchClick: () -> Unit,
    onMicClick: () -> Unit,
    isSearching: Boolean,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    searchFieldFocus: FocusRequester
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceDark)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "JioTV+",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = JioBlue,
                modifier = Modifier.weight(1f)
            )
            // Voice search mic button
            IconButton(onClick = onMicClick) {
                Icon(Icons.Default.Mic, contentDescription = "Voice Search", tint = JioBlue)
            }
            // Search toggle button
            IconButton(onClick = onSearchClick) {
                Icon(
                    if (isSearching) Icons.Default.Close else Icons.Default.Search,
                    contentDescription = if (isSearching) "Close Search" else "Search",
                    tint = if (isSearching) Color.White else OnSurfaceVariant
                )
            }
            // Language settings
            IconButton(onClick = onLanguageSettings) {
                Icon(Icons.Default.Settings, contentDescription = "Language Settings", tint = OnSurfaceVariant)
            }
            IconButton(onClick = onRefresh) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = OnSurfaceVariant)
            }
            IconButton(onClick = onLogout) {
                Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "Logout", tint = OnSurfaceVariant)
            }
        }

        // Search text field (shown when searching)
        if (isSearching) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp)
                    .focusRequester(searchFieldFocus),
                placeholder = { Text("Search channels by name, genre, language…", fontSize = 14.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = JioBlue) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchQueryChange("") }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear", tint = OnSurfaceVariant)
                        }
                    }
                },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = JioBlue,
                    unfocusedBorderColor = SurfaceVariant,
                    cursorColor = JioBlue
                ),
                shape = RoundedCornerShape(8.dp)
            )
        }
    }
}

@Composable
private fun FilterRow(
    label: String,
    items: List<String>,
    selected: String,
    onSelect: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$label: ",
            style = MaterialTheme.typography.labelMedium,
            color = OnSurfaceVariant,
            modifier = Modifier.width(72.dp)
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 4.dp)
        ) {
            items(items) { item ->
                FilterChip(
                    selected = item == selected,
                    onClick = { onSelect(item) },
                    label = { Text(item, fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = JioBlue,
                        selectedLabelColor = Color.White,
                        containerColor = SurfaceVariant,
                        labelColor = OnSurface
                    ),
                    shape = RoundedCornerShape(20.dp),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = item == selected,
                        selectedBorderColor = Color.Transparent,
                        borderColor = SurfaceVariant
                    )
                )
            }
        }
    }
}

@Composable
private fun ChannelCard(
    channel: Channel,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.05f else 1.0f,
        label = "scale"
    )
    val borderColor by animateColorAsState(
        targetValue = if (isFocused) FocusBorder else Color.Transparent,
        label = "border"
    )

    Card(
        modifier = Modifier
            .scale(scale)
            .border(2.dp, borderColor, RoundedCornerShape(8.dp))
            .aspectRatio(16f / 10f)
            .focusable(interactionSource = interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark)
    ) {
        Box(Modifier.fillMaxSize()) {
            AsyncImage(
                model = channel.thumbnail.ifBlank { channel.stillFallback },
                contentDescription = channel.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            // Gradient overlay at bottom
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.5f)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color(0xDD000000))
                        )
                    )
            )

            // Info overlay
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = channel.name,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                channel.currentProgram?.title?.let { prog ->
                    Text(
                        text = prog,
                        color = Color(0xCCFFFFFF),
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    QualityBadge(channel.quality)
                    if (channel.isPremium) PremiumBadge()
                }
            }

            // Channel number badge (top-right)
            if (channel.channelNumber > 0) {
                Text(
                    text = "#${channel.channelNumber}",
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .background(Color(0x99000000), RoundedCornerShape(4.dp))
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    color = Color.White,
                    fontSize = 10.sp
                )
            }
        }
    }
}

@Composable
private fun QualityBadge(quality: String) {
    val color = if (quality.uppercase() == "HD") HDGreen else SDGray
    Text(
        text = quality.uppercase(),
        modifier = Modifier
            .background(color.copy(alpha = 0.9f), RoundedCornerShape(3.dp))
            .padding(horizontal = 4.dp, vertical = 1.dp),
        color = Color.White,
        fontSize = 9.sp,
        fontWeight = FontWeight.Bold
    )
}

@Composable
private fun PremiumBadge() {
    Text(
        text = "PRO",
        modifier = Modifier
            .background(PremiumGold.copy(alpha = 0.9f), RoundedCornerShape(3.dp))
            .padding(horizontal = 4.dp, vertical = 1.dp),
        color = Color.Black,
        fontSize = 9.sp,
        fontWeight = FontWeight.Bold
    )
}

@Composable
private fun RecentChannelsRow(
    channels: List<Channel>,
    onChannelClick: (contentId: String, channelName: String, currentProgram: String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = "Recently Played",
            color = JioBlue,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(horizontal = 4.dp)
        ) {
            items(channels, key = { "recent_${it.contentId}" }) { channel ->
                RecentChannelCard(
                    channel = channel,
                    onClick = {
                        onChannelClick(
                            channel.contentId,
                            channel.name,
                            channel.currentProgram?.title ?: ""
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun RecentChannelCard(
    channel: Channel,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (isFocused) 1.08f else 1.0f, label = "recent_scale")
    val borderColor by animateColorAsState(
        if (isFocused) FocusBorder else Color.Transparent, label = "recent_border"
    )

    Card(
        modifier = Modifier
            .scale(scale)
            .border(2.dp, borderColor, RoundedCornerShape(8.dp))
            .width(140.dp)
            .aspectRatio(16f / 10f)
            .focusable(interactionSource = interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark)
    ) {
        Box(Modifier.fillMaxSize()) {
            AsyncImage(
                model = channel.thumbnail.ifBlank { channel.stillFallback },
                contentDescription = channel.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.5f)
                    .align(Alignment.BottomCenter)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xDD000000))))
            )
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(6.dp),
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                Text(
                    text = channel.name,
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                channel.currentProgram?.title?.let { prog ->
                    Text(
                        text = prog,
                        color = Color(0xCCFFFFFF),
                        fontSize = 9.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
