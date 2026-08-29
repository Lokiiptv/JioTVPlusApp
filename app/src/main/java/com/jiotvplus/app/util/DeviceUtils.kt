package com.jiotvplus.app.util

import android.content.Context
import android.provider.Settings
import android.util.Base64
import java.util.UUID

object DeviceUtils {

    fun getAndroidId(context: Context): String {
        return try {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
                ?.takeIf { it.isNotBlank() && it != "9774d56d682e549c" }
                ?: UUID.randomUUID().toString().replace("-", "").take(16)
        } catch (e: Exception) {
            UUID.randomUUID().toString().replace("-", "").take(16)
        }
    }

    /** Encodes phone number as base64("+91" + last10Digits) to match 6.0.8 rmn header format */
    fun encodePhoneNumber(phone: String): String {
        val digits = phone.filter { it.isDigit() }.takeLast(10)
        return Base64.encodeToString("+91$digits".toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
    }
}
