package com.jiotvplus.app.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jiotvplus.app.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class LoginState {
    data object Idle : LoginState()
    data object SendingOtp : LoginState()
    data class OtpSent(val identifier: String, val phone: String) : LoginState()
    data object VerifyingOtp : LoginState()
    data object Success : LoginState()
    data class Error(val message: String, val previousState: LoginState = Idle) : LoginState()
}

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _state = MutableStateFlow<LoginState>(LoginState.Idle)
    val state: StateFlow<LoginState> = _state.asStateFlow()

    private var pendingPhone: String = ""
    private var pendingDeviceId: String = ""

    fun sendOtp(phone: String, deviceId: String) {
        if (phone.length < 10) {
            _state.value = LoginState.Error("Please enter a valid 10-digit number")
            return
        }
        pendingPhone = phone
        pendingDeviceId = deviceId
        viewModelScope.launch {
            _state.value = LoginState.SendingOtp
            authRepository.sendOtp(phone, deviceId)
                .onSuccess { identifier ->
                    _state.value = LoginState.OtpSent(identifier, phone)
                }
                .onFailure { e ->
                    _state.value = LoginState.Error(e.message ?: "Failed to send OTP")
                }
        }
    }

    fun verifyOtp(otp: String) {
        if (otp.length != 6) {
            _state.value = LoginState.Error("Please enter the 6-digit OTP")
            return
        }
        val currentState = _state.value
        val identifier = when (currentState) {
            is LoginState.OtpSent -> currentState.identifier
            else -> return
        }

        viewModelScope.launch {
            _state.value = LoginState.VerifyingOtp
            authRepository.verifyOtp(identifier, otp, pendingDeviceId)
                .onSuccess { verifyResponse ->
                    val user = verifyResponse.sessionAttributes?.user
                    if (user != null) {
                        authRepository.exchangeToken(
                            phone = pendingPhone,
                            subscriberId = user.subscriberId,
                            userId = user.unique,
                            ssoToken = user.ssoToken.ifBlank { verifyResponse.ssoToken },
                            deviceId = pendingDeviceId
                        )
                            .onSuccess { _state.value = LoginState.Success }
                            .onFailure { e ->
                                _state.value = LoginState.Error(e.message ?: "Login failed")
                            }
                    } else {
                        _state.value = LoginState.Error("Unexpected response from server")
                    }
                }
                .onFailure { e ->
                    _state.value = LoginState.Error(
                        e.message ?: "OTP verification failed",
                        LoginState.OtpSent(identifier, pendingPhone)
                    )
                }
        }
    }

    fun resetToPhoneEntry() {
        _state.value = LoginState.Idle
    }
}
