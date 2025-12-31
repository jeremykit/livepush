package com.livepush.presentation.navigation

import java.net.URLEncoder
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object Scanner : Screen("scanner")
    data object Settings : Screen("settings")

    data object Stream : Screen("stream/{streamUrl}") {
        fun createRoute(streamUrl: String): String {
            val encodedUrl = URLEncoder.encode(streamUrl, StandardCharsets.UTF_8.toString())
            return "stream/$encodedUrl"
        }

        fun decodeUrl(encodedUrl: String): String {
            return URLDecoder.decode(encodedUrl, StandardCharsets.UTF_8.toString())
        }
    }
}
