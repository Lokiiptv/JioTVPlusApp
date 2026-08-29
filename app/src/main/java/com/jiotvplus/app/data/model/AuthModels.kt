package com.jiotvplus.app.data.model

import com.google.gson.annotations.SerializedName

// ── OTP Login ────────────────────────────────────────────────────────────────

data class SendOtpRequest(
    val number: String,
    val identifierid: String = ""
)

data class SendOtpResponse(
    val code: Int = 0,
    val message: String = "",
    val identifier: String = "",
    val mobilenumber: String? = null,
    val fttxIds: List<FttxId>? = null
)

data class FttxId(
    val customerId: String? = null,
    val firstName: String? = null,
    val products: List<FttxProduct>? = null
)

data class FttxProduct(
    val identifier: List<FttxIdentifier>? = null,
    val productCode: String? = null,
    val productName: String? = null
)

data class FttxIdentifier(
    val name: String? = null,
    val value: String? = null
)

data class VerifyOtpRequest(
    val identifier: String,
    val otp: String,
    val upgradeAuth: String = "Y",
    val rememberUser: String = "T",
    val deviceInfo: DeviceInfo
)

data class DeviceInfo(
    val consumptionDeviceName: String,
    val info: DeviceInfoDetails
)

data class DeviceInfoDetails(
    val type: String = "android",
    val platform: Platform,
    val androidId: String
)

data class Platform(val name: String)

data class VerifyOtpResponse(
    val code: Int = 0,
    val ssoToken: String = "",
    val jToken: String? = null,
    val lbCookie: String? = null,
    val ssoLevel: String? = null,
    val packageMapping: List<PackageMapping>? = null,
    val sessionAttributes: SessionAttributes? = null
)

data class PackageMapping(
    val packageId: String? = null,
    val packageName: String? = null
)

data class SessionAttributes(
    val otpValidatedDate: String? = null,
    val passwordExpiry: String? = null,
    val profile: Profile? = null,
    val user: UserInfo? = null
)

data class Profile(
    val billingId: String? = null,
    val entitlements: List<String>? = null,
    val profileId: String? = null,
    val profileName: String? = null
)

data class UserInfo(
    val commonName: String? = null,
    val lbCookie: String? = null,
    val mail: String? = null,
    val mobile: String? = null,
    val preferredLocale: String? = null,
    val ssoLevel: String? = null,
    val ssoToken: String = "",
    val subscriberId: String = "",
    val uid: String? = null,
    val unique: String = ""
)

// ── Token Exchange ────────────────────────────────────────────────────────────

data class TokenExchangeRequest(
    val number: String  // base64("9" + phoneNumber)
)

data class TokenExchangeResponse(
    val authToken: String = "",
    val refreshToken: String = "",
    val userId: String = "",
    val subscriberId: String = "",
    val name: String? = null
)
