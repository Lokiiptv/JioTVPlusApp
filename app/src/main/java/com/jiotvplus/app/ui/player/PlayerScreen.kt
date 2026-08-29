package com.jiotvplus.app.ui.player

import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.drm.DefaultDrmSessionManager
import androidx.media3.exoplayer.drm.FrameworkMediaDrm
import androidx.media3.exoplayer.drm.HttpMediaDrmCallback
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import com.jiotvplus.app.data.model.Channel
import com.jiotvplus.app.ui.theme.*
import kotlinx.coroutines.delay

private enum class OverlayMode { HIDDEN, INFO, CHANNELS, TRACKS }

private fun android.content.Context.findActivity(): android.app.Activity? {
    var ctx = this
    while (ctx is android.content.ContextWrapper) {
        if (ctx is android.app.Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    contentId: String,
    channelName: String,
    currentProgram: String,
    onBack: () -> Unit,
    viewModel: PlayerViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val streamConfig by viewModel.streamConfig.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val vmChannelName by viewModel.channelName.collectAsStateWithLifecycle()
    val vmCurrentProgram by viewModel.currentProgram.collectAsStateWithLifecycle()
    val channelList by viewModel.channelList.collectAsStateWithLifecycle()
    val currentContentId by viewModel.currentContentId.collectAsStateWithLifecycle()
    val trackInfo by viewModel.trackInfo.collectAsStateWithLifecycle()

    var overlayMode by remember { mutableStateOf(OverlayMode.HIDDEN) }
    var isPlaying by remember { mutableStateOf(false) }
    var retryCount by remember { mutableStateOf(0) }
    val playerFocusRequester = remember { FocusRequester() }

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            addListener(object : Player.Listener {
                override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    android.util.Log.e("PlayerScreen", "ExoPlayer error", error)
                    if (retryCount < 2) {
                        retryCount++
                        android.util.Log.w("PlayerScreen", "Retrying ($retryCount)...")
                        viewModel.loadChannel(viewModel.currentContentId.value, viewModel.channelName.value, viewModel.currentProgram.value)
                    } else {
                        viewModel.setPlayerError(error.message ?: "Playback error")
                    }
                }
                override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
                    updateTrackInfo(this@apply, viewModel)
                }
            })
        }
    }

    LaunchedEffect(overlayMode, isPlaying) {
        if (overlayMode == OverlayMode.INFO && isPlaying) {
            delay(5000)
            overlayMode = OverlayMode.HIDDEN
        }
    }

    LaunchedEffect(contentId) {
        retryCount = 0
        viewModel.loadChannel(contentId, channelName, currentProgram)
    }

    LaunchedEffect(streamConfig) {
        streamConfig?.let { config ->
            val httpClient = OkHttpClient.Builder()
                .cookieJar(InMemoryCookieJar())
                .addInterceptor(HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BASIC
                })
                .build()
            val dataSourceFactory = OkHttpDataSource.Factory(httpClient)
                .setDefaultRequestProperties(config.headers)

            val mediaSource: MediaSource = if (config.isDash) {
                val factory = DashMediaSource.Factory(dataSourceFactory)
                if (config.keyUrl != null) {
                    try {
                        val drmCallback = HttpMediaDrmCallback(config.keyUrl, dataSourceFactory)
                        val drmSessionManager = DefaultDrmSessionManager.Builder()
                            .setUuidAndExoMediaDrmProvider(C.WIDEVINE_UUID, FrameworkMediaDrm.DEFAULT_PROVIDER)
                            .build(drmCallback)
                        factory.setDrmSessionManagerProvider { drmSessionManager }
                    } catch (e: Exception) {
                        android.util.Log.w("PlayerScreen", "DRM init failed: ${e.message}")
                    }
                }
                factory.createMediaSource(MediaItem.fromUri(config.url))
            } else {
                HlsMediaSource.Factory(dataSourceFactory)
                    .createMediaSource(MediaItem.fromUri(config.url))
            }

            exoPlayer.setMediaSource(mediaSource)
            exoPlayer.prepare()
            exoPlayer.playWhenReady = true
            delay(1000)
            updateTrackInfo(exoPlayer, viewModel)
        }
    }

    DisposableEffect(Unit) {
        // Force landscape orientation for video playback (phones)
        val activity = context.findActivity()
        activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        onDispose {
            exoPlayer.release()
            // Restore screen timeout + orientation
            activity?.let {
                it.window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                it.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        }
    }

    LaunchedEffect(Unit) {
        playerFocusRequester.requestFocus()
    }

    // Keep screen on while playing, allow sleep when paused
    LaunchedEffect(isPlaying) {
        val activity = context.findActivity()
        if (isPlaying) {
            activity?.window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            activity?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(playerFocusRequester)
            .focusable()
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    when (event.key) {
                        Key.Back, Key.Escape -> {
                            if (overlayMode != OverlayMode.HIDDEN) {
                                overlayMode = OverlayMode.HIDDEN
                                true
                            } else {
                                onBack()
                                true
                            }
                        }
                        Key.DirectionUp -> {
                            if (overlayMode == OverlayMode.HIDDEN) {
                                overlayMode = OverlayMode.INFO
                                true
                            } else if (overlayMode == OverlayMode.CHANNELS) {
                                overlayMode = OverlayMode.TRACKS
                                true
                            } else false
                        }
                        Key.DirectionDown -> {
                            if (overlayMode == OverlayMode.HIDDEN || overlayMode == OverlayMode.INFO) {
                                overlayMode = OverlayMode.CHANNELS
                                true
                            } else if (overlayMode == OverlayMode.TRACKS) {
                                overlayMode = OverlayMode.CHANNELS
                                true
                            } else false
                        }
                        else -> {
                            if (overlayMode == OverlayMode.HIDDEN) {
                                overlayMode = OverlayMode.INFO
                            }
                            false
                        }
                    }
                } else false
            }
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                    focusable = android.view.View.NOT_FOCUSABLE
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = JioBlue, modifier = Modifier.size(56.dp))
                    Spacer(Modifier.height(16.dp))
                    Text("Loading stream…", color = Color.White, fontSize = 18.sp)
                }
            }
        }

        error?.let { err ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .background(Color(0xCC000000), RoundedCornerShape(12.dp))
                        .padding(32.dp)
                ) {
                    Text(err, color = Color.Red, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(20.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Button(
                            onClick = {
                                retryCount = 0
                                viewModel.clearError()
                                viewModel.loadChannel(viewModel.currentContentId.value, viewModel.channelName.value, viewModel.currentProgram.value)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = JioBlue)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Retry")
                        }
                        OutlinedButton(onClick = onBack) { Text("Back", color = Color.White) }
                    }
                }
            }
        }

        // Top info overlay
        AnimatedVisibility(
            visible = (overlayMode == OverlayMode.INFO || overlayMode == OverlayMode.TRACKS) && !isLoading && error == null,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.25f)
                        .background(Brush.verticalGradient(listOf(Color(0xCC000000), Color.Transparent)))
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp)
                        .align(Alignment.TopStart),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(
                            text = vmChannelName,
                            color = Color.White,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold
                        )
                        if (vmCurrentProgram.isNotBlank()) {
                            Text(
                                text = vmCurrentProgram,
                                color = Color(0xCCFFFFFF),
                                fontSize = 14.sp
                            )
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = "● LIVE",
                        modifier = Modifier
                            .background(Color.Red, RoundedCornerShape(4.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Track selection overlay (press UP from channel bar)
        AnimatedVisibility(
            visible = overlayMode == OverlayMode.TRACKS && trackInfo != null,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it })
        ) {
            TrackSelectionOverlay(
                trackInfo = trackInfo,
                onAudioSelected = { index ->
                    selectAudioTrack(exoPlayer, index)
                    updateTrackInfo(exoPlayer, viewModel)
                },
                onVideoSelected = { index ->
                    selectVideoTrack(exoPlayer, index)
                    updateTrackInfo(exoPlayer, viewModel)
                },
                onDismiss = { overlayMode = OverlayMode.HIDDEN }
            )
        }

        // Channel switcher bar (press DOWN)
        AnimatedVisibility(
            visible = overlayMode == OverlayMode.CHANNELS && channelList.isNotEmpty(),
            enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it })
        ) {
            ChannelSwitcherBar(
                channels = channelList,
                currentContentId = currentContentId,
                onChannelSelected = { channel ->
                    viewModel.switchChannel(channel.contentId)
                    overlayMode = OverlayMode.HIDDEN
                    retryCount = 0
                },
                onDismiss = { overlayMode = OverlayMode.HIDDEN }
            )
        }

        // Hint text when hidden
        AnimatedVisibility(
            visible = overlayMode == OverlayMode.HIDDEN && isPlaying && !isLoading,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 16.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                Text(
                    text = "↓ Channels  ↑ Tracks  ← Back",
                    color = Color(0x66FFFFFF),
                    fontSize = 12.sp,
                    modifier = Modifier
                        .background(Color(0x33000000), RoundedCornerShape(4.dp))
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@OptIn(UnstableApi::class)
private fun updateTrackInfo(exoPlayer: ExoPlayer, viewModel: PlayerViewModel) {
    try {
        val audioTracks = mutableListOf<Track>()
        val videoTracks = mutableListOf<Track>()
        val trackSelector = exoPlayer.currentTracks
        var selectedAudio = 0
        var selectedVideo = 0

        trackSelector.groups.forEachIndexed { groupIndex, group ->
            if (!group.isSelected) return@forEachIndexed
            for (trackIndex in 0 until group.length) {
                val format = group.getTrackFormat(trackIndex)
                val isAudio = group.type == C.TRACK_TYPE_AUDIO
                val isVideo = group.type == C.TRACK_TYPE_VIDEO
                if (!isAudio && !isVideo) continue

                val label = buildString {
                    if (!format.label.isNullOrBlank()) append(format.label)
                    else if (!format.language.isNullOrBlank()) append(format.language)
                    else if (isAudio) append("Audio")
                    else if (isVideo) append("${format.width}x${format.height}")
                    if (format.bitrate > 0) append(" ${format.bitrate / 1000}kbps")
                }

                val track = Track(
                    index = groupIndex,
                    label = label,
                    language = format.language ?: "",
                    bitrate = format.bitrate,
                    height = format.height,
                    width = format.width
                )
                if (isAudio) {
                    audioTracks.add(track)
                    if (group.isTrackSupported(trackIndex)) selectedAudio = audioTracks.size - 1
                } else {
                    videoTracks.add(track)
                    if (group.isTrackSupported(trackIndex)) selectedVideo = videoTracks.size - 1
                }
            }
        }

        // Simplify: one track per group
        audioTracks.clear()
        videoTracks.clear()
        trackSelector.groups.forEachIndexed { groupIndex, group ->
            val format = group.getTrackFormat(0)
            if (group.type == C.TRACK_TYPE_AUDIO) {
                audioTracks.add(Track(
                    index = groupIndex,
                    label = format.label ?: format.language ?: "Audio ${audioTracks.size + 1}",
                    language = format.language ?: ""
                ))
                if (group.isSelected) selectedAudio = audioTracks.size - 1
            } else if (group.type == C.TRACK_TYPE_VIDEO) {
                videoTracks.add(Track(
                    index = groupIndex,
                    label = "${format.width}x${format.height}" + if (format.bitrate > 0) " ${format.bitrate / 1000}kbps" else "",
                    bitrate = format.bitrate,
                    height = format.height,
                    width = format.width
                ))
                if (group.isSelected) selectedVideo = videoTracks.size - 1
            }
        }

        viewModel.updateTrackInfo(audioTracks, videoTracks, selectedAudio, selectedVideo)
    } catch (e: Exception) {
        android.util.Log.w("PlayerScreen", "Track update failed: ${e.message}")
    }
}

@OptIn(UnstableApi::class)
private fun selectAudioTrack(exoPlayer: ExoPlayer, trackIndex: Int) {
    try {
        val groups = exoPlayer.currentTracks.groups
        if (trackIndex in groups.indices) {
            val group = groups[trackIndex]
            if (group.type == C.TRACK_TYPE_AUDIO) {
                val override = TrackSelectionOverride(group.mediaTrackGroup, 0)
                exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                    .buildUpon()
                    .setOverrideForType(override)
                    .build()
            }
        }
    } catch (e: Exception) {
        android.util.Log.w("PlayerScreen", "Audio track select failed: ${e.message}")
    }
}

@OptIn(UnstableApi::class)
private fun selectVideoTrack(exoPlayer: ExoPlayer, trackIndex: Int) {
    try {
        val groups = exoPlayer.currentTracks.groups
        if (trackIndex in groups.indices) {
            val group = groups[trackIndex]
            if (group.type == C.TRACK_TYPE_VIDEO) {
                val override = TrackSelectionOverride(group.mediaTrackGroup, 0)
                exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                    .buildUpon()
                    .setOverrideForType(override)
                    .build()
            }
        }
    } catch (e: Exception) {
        android.util.Log.w("PlayerScreen", "Video track select failed: ${e.message}")
    }
}

@Composable
private fun ChannelSwitcherBar(
    channels: List<Channel>,
    currentContentId: String,
    onChannelSelected: (Channel) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedGenre by remember { mutableStateOf("All") }
    val genres by remember(channels) {
        derivedStateOf {
            val set = linkedSetOf("All")
            channels.forEach { ch -> ch.genres.forEach { g -> set.add(g) } }
            set.toList()
        }
    }
    val filteredChannels by remember(channels, selectedGenre) {
        derivedStateOf {
            if (selectedGenre == "All") channels
            else channels.filter { it.genres.any { g -> g.equals(selectedGenre, ignoreCase = true) } }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xE6000000))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Channels",
                    color = JioBlue,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = "${filteredChannels.size} channels • Press ← to close",
                    color = OnSurfaceVariant,
                    fontSize = 12.sp
                )
            }
            Spacer(Modifier.height(8.dp))
            // Genre chip row
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 4.dp)
            ) {
                items(genres, key = { it }) { genre ->
                    GenreChip(
                        label = genre,
                        isSelected = genre == selectedGenre,
                        onClick = { selectedGenre = genre }
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(horizontal = 4.dp)
            ) {
                items(filteredChannels, key = { it.contentId }) { channel ->
                    ChannelSwitcherCard(
                        channel = channel,
                        isCurrent = channel.contentId == currentContentId,
                        onClick = { onChannelSelected(channel) }
                    )
                }
            }
        }
    }
}

@Composable
private fun GenreChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(
                when {
                    isSelected -> JioBlue
                    isFocused -> JioBlue.copy(alpha = 0.6f)
                    else -> SurfaceVariant
                }
            )
            .border(
                2.dp,
                if (isFocused) FocusBorder else Color.Transparent,
                RoundedCornerShape(16.dp)
            )
            .focusable(interactionSource = interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (isSelected || isFocused) Color.White else OnSurface,
            fontSize = 13.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1
        )
    }
}

@Composable
private fun ChannelSwitcherCard(
    channel: Channel,
    isCurrent: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (isFocused) 1.1f else 1.0f, label = "ch_scale")
    val borderColor by animateColorAsState(
        when {
            isCurrent -> JioBlue
            isFocused -> FocusBorder
            else -> Color.Transparent
        }, label = "ch_border"
    )

    Card(
        modifier = Modifier
            .scale(scale)
            .border(
                2.dp, borderColor, RoundedCornerShape(8.dp)
            )
            .width(160.dp)
            .aspectRatio(16f / 10f)
            .focusable(interactionSource = interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isCurrent) JioBlue.copy(alpha = 0.2f) else SurfaceDark
        )
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
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                channel.currentProgram?.title?.let { prog ->
                    Text(
                        text = prog,
                        color = Color(0xCCFFFFFF),
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (isCurrent) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .background(JioBlue, RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "● LIVE",
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun TrackSelectionOverlay(
    trackInfo: TrackInfo?,
    onAudioSelected: (Int) -> Unit,
    onVideoSelected: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val info = trackInfo ?: return
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xE6000000))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(24.dp)
        ) {
            Text(
                text = "Audio & Video Tracks",
                color = JioBlue,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(16.dp))

            if (info.audioTracks.isNotEmpty()) {
                Text(
                    text = "Audio",
                    color = OnSurfaceVariant,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    info.audioTracks.forEachIndexed { index, track ->
                        TrackButton(
                            label = track.label,
                            isSelected = index == info.selectedAudioIndex,
                            onClick = { onAudioSelected(track.index) }
                        )
                    }
                }
            }

            if (info.videoTracks.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "Video Quality",
                    color = OnSurfaceVariant,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    info.videoTracks.forEachIndexed { index, track ->
                        TrackButton(
                            label = track.label,
                            isSelected = index == info.selectedVideoIndex,
                            onClick = { onVideoSelected(track.index) }
                        )
                    }
                }
            }

            if (info.audioTracks.isEmpty() && info.videoTracks.isEmpty()) {
                Text(
                    text = "No alternate tracks available for this stream",
                    color = OnSurfaceVariant,
                    fontSize = 14.sp
                )
            }

            Spacer(Modifier.height(16.dp))
            Text(
                text = "Press ← to close",
                color = OnSurfaceVariant.copy(alpha = 0.5f),
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun TrackButton(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(
                when {
                    isSelected -> JioBlue
                    isFocused -> JioBlue.copy(alpha = 0.6f)
                    else -> SurfaceVariant
                }
            )
            .border(
                2.dp,
                if (isFocused) FocusBorder else Color.Transparent,
                RoundedCornerShape(8.dp)
            )
            .focusable(interactionSource = interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (isSelected || isFocused) Color.White else OnSurface,
            fontSize = 13.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@OptIn(UnstableApi::class)
private class InMemoryCookieJar : okhttp3.CookieJar {
    private val storage = java.util.concurrent.ConcurrentHashMap<String, MutableList<okhttp3.Cookie>>()

    override fun saveFromResponse(url: okhttp3.HttpUrl, cookies: List<okhttp3.Cookie>) {
        if (cookies.isEmpty()) return
        val key = url.topPrivateDomain() ?: url.host
        val list = storage.getOrPut(key) { mutableListOf() }
        synchronized(list) {
            list.removeAll { existing -> cookies.any { it.name == existing.name } }
            list.addAll(cookies)
        }
    }

    override fun loadForRequest(url: okhttp3.HttpUrl): List<okhttp3.Cookie> {
        val key = url.topPrivateDomain() ?: url.host
        val list = storage[key] ?: return emptyList()
        synchronized(list) {
            val now = System.currentTimeMillis()
            list.removeAll { it.expiresAt < now }
            return list.filter { it.matches(url) }
        }
    }
}
