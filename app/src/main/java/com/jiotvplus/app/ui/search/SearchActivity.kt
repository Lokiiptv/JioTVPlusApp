package com.jiotvplus.app.ui.search

import android.app.SearchManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class SearchActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (intent?.action == Intent.ACTION_SEARCH) {
            val query = intent.getStringExtra(SearchManager.QUERY).orEmpty()
            setContent {
                JioTVPlusTheme {
                    SearchResultsScreen(query = query)
                }
            }
        } else {
            finish()
        }
    }
}

@Composable
private fun SearchResultsScreen(
    query: String,
    viewModel: SearchViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val results by viewModel.results.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()

    LaunchedEffect(query) {
        viewModel.search(query)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
    ) {
        // Search header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(SurfaceDark)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Search",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = JioBlue
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = "\"$query\"",
                fontSize = 16.sp,
                color = OnSurfaceVariant
            )
            Spacer(Modifier.weight(1f))
            if (results.isNotEmpty()) {
                Text(
                    text = "${results.size} result${if (results.size > 1) "s" else ""}",
                    color = OnSurfaceVariant,
                    fontSize = 13.sp
                )
            }
        }

        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = JioBlue, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(16.dp))
                    Text("Searching channels…", color = OnSurfaceVariant)
                }
            }
        } else if (results.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "No channels found for \"$query\"",
                    color = OnSurfaceVariant,
                    fontSize = 16.sp
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 200.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(results, key = { it.contentId }) { channel ->
                    SearchResultCard(
                        channel = channel,
                        onClick = {
                            val intent = Intent(context, com.jiotvplus.app.ui.MainActivity::class.java).apply {
                                data = Uri.parse("content://com.jiotvplus.app/channel/${channel.contentId}")
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                            }
                            context.startActivity(intent)
                            (context as? android.app.Activity)?.finish()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchResultCard(
    channel: Channel,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val scale by androidx.compose.animation.core.animateFloatAsState(
        if (isFocused) 1.05f else 1.0f, label = "search_scale"
    )
    val borderColor by androidx.compose.animation.animateColorAsState(
        if (isFocused) FocusBorder else Color.Transparent, label = "search_border"
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
            }
        }
    }
}
