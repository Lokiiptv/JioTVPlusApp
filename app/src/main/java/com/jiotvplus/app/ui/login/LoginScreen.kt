package com.jiotvplus.app.ui.login

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jiotvplus.app.ui.theme.*
import com.jiotvplus.app.util.DeviceUtils

// Number pad layout: rows of keys
private val NUM_PAD = listOf(
    listOf("1", "2", "3"),
    listOf("4", "5", "6"),
    listOf("7", "8", "9"),
    listOf("⌫", "0", "OK")
)

@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    viewModel: LoginViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val deviceId = remember { DeviceUtils.getAndroidId(context) }

    var input by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val isOtpStep = state is LoginState.OtpSent || state is LoginState.VerifyingOtp
    val isLoading = state is LoginState.SendingOtp || state is LoginState.VerifyingOtp
    val maxLen = if (isOtpStep) 6 else 10

    LaunchedEffect(state) {
        when (val s = state) {
            is LoginState.Success -> onLoginSuccess()
            is LoginState.Error -> {
                errorMessage = s.message
                input = ""
            }
            is LoginState.OtpSent -> {
                // Step changed — clear input for OTP entry
                input = ""
                errorMessage = null
            }
            else -> {}
        }
    }

    fun onKey(key: String) {
        errorMessage = null
        when (key) {
            "⌫" -> if (input.isNotEmpty()) input = input.dropLast(1)
            "OK" -> {
                if (isOtpStep) {
                    if (input.length == 6) viewModel.verifyOtp(input)
                    else errorMessage = "Enter all 6 digits"
                } else {
                    if (input.length == 10) viewModel.sendOtp(input, deviceId)
                    else errorMessage = "Enter a 10-digit mobile number"
                }
            }
            else -> if (input.length < maxLen) input += key
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
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier
                .width(420.dp)
                .padding(horizontal = 24.dp)
        ) {
            // App title
            Text(
                text = "JioTV+",
                fontSize = 52.sp,
                fontWeight = FontWeight.Bold,
                color = JioBlue
            )

            // Instruction
            Text(
                text = if (isOtpStep)
                    "Enter the OTP sent to your number"
                else
                    "Enter your Jio mobile number",
                color = OnSurfaceVariant,
                fontSize = 16.sp,
                textAlign = TextAlign.Center
            )

            // Digit display
            DigitDisplay(
                value = input,
                maxLen = maxLen,
                masked = isOtpStep
            )

            // Error
            AnimatedVisibility(visible = errorMessage != null) {
                Text(
                    text = errorMessage ?: "",
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )
            }

            // Loading
            if (isLoading) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator(
                        color = JioBlue,
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                    Text(
                        text = if (state is LoginState.SendingOtp) "Sending OTP…" else "Verifying…",
                        color = OnSurfaceVariant,
                        fontSize = 14.sp
                    )
                }
            }

            // Number pad
            if (!isLoading) {
                NumberPad(
                    onKey = { onKey(it) },
                    autoFocusKey = "1"
                )
            }

            // Back button for OTP step
            if (isOtpStep && !isLoading) {
                TextButton(onClick = { viewModel.resetToPhoneEntry() }) {
                    Text("← Change number", color = OnSurfaceVariant, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun DigitDisplay(value: String, maxLen: Int, masked: Boolean) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(SurfaceDark)
            .padding(horizontal = 20.dp, vertical = 14.dp)
    ) {
        repeat(maxLen) { i ->
            val char = value.getOrNull(i)
            val isCursor = i == value.length
            Box(
                modifier = Modifier
                    .size(width = 24.dp, height = 36.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = when {
                        char == null && isCursor -> "▌"
                        char == null -> "_"
                        masked -> "●"
                        else -> char.toString()
                    },
                    fontSize = if (char != null && !masked) 22.sp else 18.sp,
                    color = when {
                        isCursor && char == null -> JioBlue
                        char != null -> Color.White
                        else -> OnSurfaceVariant
                    },
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }
            // Separator after every 5 digits for phone readability
            if (!masked && i == 4 && maxLen == 10) {
                Spacer(Modifier.width(8.dp))
            }
        }
    }
}

@Composable
private fun NumberPad(
    onKey: (String) -> Unit,
    autoFocusKey: String = "1"
) {
    // FocusRequesters for each key so we can auto-focus first key
    val focusMap = remember {
        NUM_PAD.flatten().associateWith { FocusRequester() }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        NUM_PAD.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { key ->
                    NumKey(
                        label = key,
                        modifier = Modifier
                            .focusRequester(focusMap[key]!!)
                            .onKeyEvent { event ->
                                if (event.type == KeyEventType.KeyDown &&
                                    (event.key == Key.DirectionCenter || event.key == Key.Enter)
                                ) {
                                    onKey(key)
                                    true
                                } else false
                            },
                        onClick = { onKey(key) },
                        isAction = key == "OK" || key == "⌫"
                    )
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        focusMap[autoFocusKey]?.requestFocus()
    }
}

@Composable
private fun NumKey(
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    isAction: Boolean = false
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val bgColor = when {
        isFocused && isAction -> JioBlue
        isFocused -> JioBlue.copy(alpha = 0.85f)
        isAction -> SurfaceVariant
        else -> SurfaceDark
    }
    val scale = if (isFocused) 1.1f else 1.0f
    val borderColor = if (isFocused) FocusBorder else Color.Transparent

    Box(
        modifier = modifier
            .scale(scale)
            .size(width = 110.dp, height = 56.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .border(2.dp, borderColor, RoundedCornerShape(8.dp))
            .focusable(interactionSource = interactionSource)
            .then(
                Modifier.clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (isFocused || isAction) Color.White else OnSurface,
            fontSize = if (label.length == 1 && label != "⌫") 24.sp else 18.sp,
            fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Normal
        )
    }
}

