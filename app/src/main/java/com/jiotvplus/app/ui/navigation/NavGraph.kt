package com.jiotvplus.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.jiotvplus.app.data.prefs.TokenStore
import com.jiotvplus.app.ui.home.HomeScreen
import com.jiotvplus.app.ui.language.LanguageSelectionScreen
import com.jiotvplus.app.ui.login.LoginScreen
import com.jiotvplus.app.ui.player.PlayerScreen
import kotlinx.coroutines.flow.first
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

private const val ROUTE_LOGIN = "login"
private const val ROUTE_LANGUAGE = "language"
private const val ROUTE_HOME = "home"
private const val ROUTE_PLAYER = "player/{contentId}/{channelName}/{currentProgram}"

@Composable
fun NavGraph(tokenStore: TokenStore, deepLinkContentId: String? = null) {
    val navController = rememberNavController()
    val isLoggedIn by tokenStore.isLoggedIn.collectAsStateWithLifecycle(initialValue = false)

    // Determine start destination based on login + language state + deep link
    val startDestination = remember {
        kotlinx.coroutines.runBlocking {
            val loggedIn = tokenStore.isLoggedIn.first()
            val hasLanguages = tokenStore.getLanguages().isNotEmpty()
            when {
                loggedIn && hasLanguages && deepLinkContentId != null ->
                    "player/$deepLinkContentId/%20/%20"
                loggedIn && hasLanguages -> ROUTE_HOME
                loggedIn && !hasLanguages -> ROUTE_LANGUAGE
                else -> ROUTE_LOGIN
            }
        }
    }

    NavHost(navController = navController, startDestination = startDestination) {

        composable(ROUTE_LOGIN) {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate(ROUTE_LANGUAGE) {
                        popUpTo(ROUTE_LOGIN) { inclusive = true }
                    }
                }
            )
        }

        composable("language") {
            LanguageSelectionScreen(
                tokenStore = tokenStore,
                onDone = {
                    navController.navigate(ROUTE_HOME) {
                        popUpTo(ROUTE_LANGUAGE) { inclusive = true }
                    }
                },
                forceShow = true
            )
        }

        composable(ROUTE_HOME) {
            HomeScreen(
                onChannelClick = { contentId, channelName, currentProgram ->
                    val encodedName = URLEncoder.encode(channelName, StandardCharsets.UTF_8.name())
                    val encodedProg = URLEncoder.encode(currentProgram.ifBlank { " " }, StandardCharsets.UTF_8.name())
                    navController.navigate("player/$contentId/$encodedName/$encodedProg")
                },
                onLogout = {
                    navController.navigate(ROUTE_LOGIN) {
                        popUpTo(ROUTE_HOME) { inclusive = true }
                    }
                },
                onLanguageSettings = {
                    navController.navigate(ROUTE_LANGUAGE)
                }
            )
        }

        composable(
            route = ROUTE_PLAYER,
            arguments = listOf(
                navArgument("contentId") { type = NavType.StringType },
                navArgument("channelName") { type = NavType.StringType },
                navArgument("currentProgram") { type = NavType.StringType }
            )
        ) { backStack ->
            val contentId = backStack.arguments?.getString("contentId") ?: return@composable
            val channelName = URLDecoder.decode(
                backStack.arguments?.getString("channelName") ?: "",
                StandardCharsets.UTF_8.name()
            )
            val currentProgram = URLDecoder.decode(
                backStack.arguments?.getString("currentProgram") ?: "",
                StandardCharsets.UTF_8.name()
            ).trim()

            PlayerScreen(
                contentId = contentId,
                channelName = channelName,
                currentProgram = currentProgram,
                onBack = { navController.popBackStack() }
            )
        }
    }

    // Handle logout redirect — only when isLoggedIn transitions from true to false,
    // NOT on the initial false value (which fires before DataStore/Frida loads)
    var wasLoggedIn by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(isLoggedIn) {
        if (wasLoggedIn == true && !isLoggedIn) {
            val currentRoute = navController.currentDestination?.route
            if (currentRoute != ROUTE_LOGIN && currentRoute != null) {
                navController.navigate(ROUTE_LOGIN) {
                    popUpTo(0) { inclusive = true }
                }
            }
        }
        wasLoggedIn = isLoggedIn
    }
}
