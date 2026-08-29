package com.jiotvplus.app.ui

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.jiotvplus.app.data.prefs.TokenStore
import com.jiotvplus.app.ui.navigation.NavGraph
import com.jiotvplus.app.ui.theme.JioTVPlusTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var tokenStore: TokenStore

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val deepLinkContentId = extractChannelId(intent)

        setContent {
            JioTVPlusTheme {
                NavGraph(tokenStore = tokenStore, deepLinkContentId = deepLinkContentId)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    private fun extractChannelId(intent: Intent?): String? {
        val data = intent?.data ?: return null
        if (data.host == "com.jiotvplus.app" && data.path?.startsWith("/channel/") == true) {
            return data.lastPathSegment
        }
        return null
    }
}
