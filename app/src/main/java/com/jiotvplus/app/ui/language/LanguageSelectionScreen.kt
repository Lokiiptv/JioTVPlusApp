package com.jiotvplus.app.ui.language

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jiotvplus.app.data.prefs.TokenStore
import com.jiotvplus.app.ui.theme.*
import com.jiotvplus.app.util.ScreenUtils
import kotlinx.coroutines.launch

private val AVAILABLE_LANGUAGES = listOf(
    "Hindi", "English", "Telugu", "Tamil", "Marathi", "Bengali",
    "Punjabi", "Gujarati", "Odia", "Kannada", "Malayalam",
    "Bhojpuri", "Assamese", "Urdu", "French", "Nepali"
)

@Composable
fun LanguageSelectionScreen(
    tokenStore: TokenStore,
    onDone: () -> Unit,
    forceShow: Boolean = false
) {
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    val context = androidx.compose.ui.platform.LocalContext.current

    var selected by remember { mutableStateOf(setOf("Hindi", "English")) }

    LaunchedEffect(Unit) {
        val saved = tokenStore.getLanguages()
        if (saved.isNotEmpty() && !forceShow) onDone()
        else {
            if (saved.isNotEmpty()) selected = saved
            focusRequester.requestFocus()
        }
    }

    fun toggle(lang: String) {
        selected = if (lang in selected) selected - lang else selected + lang
    }

    fun confirm() {
        if (selected.isNotEmpty()) {
            scope.launch {
                tokenStore.saveLanguages(selected)
                onDone()
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(700.dp)
        ) {
            Text(
                text = "Select Your Languages",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = JioBlue
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Channels will be filtered based on your language preference",
                color = OnSurfaceVariant,
                fontSize = 15.sp
            )
            Spacer(Modifier.height(24.dp))

            LazyVerticalGrid(
                columns = GridCells.Fixed(if (ScreenUtils.isTV(context)) 4 else 3),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f, fill = false)
            ) {
                items(AVAILABLE_LANGUAGES) { lang ->
                    LanguageChip(
                        language = lang,
                        isSelected = lang in selected,
                        onClick = { toggle(lang) },
                        modifier = if (lang == AVAILABLE_LANGUAGES.first()) Modifier.focusRequester(focusRequester) else Modifier
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            Button(
                onClick = { confirm() },
                enabled = selected.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = JioBlue,
                    disabledContainerColor = SurfaceVariant
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .width(200.dp)
                    .height(50.dp)
            ) {
                Text(
                    text = "Continue (${selected.size})",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun LanguageChip(
    language: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (isFocused) 1.08f else 1.0f, label = "scale")
    val borderColor by animateColorAsState(
        if (isFocused) FocusBorder else if (isSelected) JioBlue else SurfaceVariant,
        label = "border"
    )

    Box(
        modifier = modifier
            .scale(scale)
            .height(48.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) JioBlue.copy(alpha = 0.3f) else SurfaceDark)
            .border(2.dp, borderColor, RoundedCornerShape(8.dp))
            .focusable(interactionSource = interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown &&
                    (event.key == Key.DirectionCenter || event.key == Key.Enter)
                ) {
                    onClick()
                    true
                } else false
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = language,
            color = if (isSelected) Color.White else OnSurface,
            fontSize = 15.sp,
            fontWeight = if (isSelected || isFocused) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}
