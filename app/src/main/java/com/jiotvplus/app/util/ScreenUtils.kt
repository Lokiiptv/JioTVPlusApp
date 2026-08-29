package com.jiotvplus.app.util

import android.content.Context
import android.content.res.Configuration
import android.app.UiModeManager

object ScreenUtils {

    fun isTV(context: Context): Boolean {
        val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
        return uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
    }

    fun isPhone(context: Context): Boolean = !isTV(context)

    fun isLandscape(context: Context): Boolean {
        return context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    }

    fun dpWidth(context: Context): Float {
        return context.resources.configuration.screenWidthDp.toFloat()
    }

    /** Grid columns for channel grid based on screen width */
    fun gridColumns(context: Context): Int {
        val widthDp = context.resources.configuration.screenWidthDp
        return when {
            widthDp >= 900 -> 6
            widthDp >= 700 -> 5
            widthDp >= 500 -> 4
            widthDp >= 360 -> 3
            else -> 2
        }
    }
}
