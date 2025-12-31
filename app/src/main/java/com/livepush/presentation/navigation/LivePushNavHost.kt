package com.livepush.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.livepush.presentation.ui.home.HomeScreen
import com.livepush.presentation.ui.scanner.ScannerScreen
import com.livepush.presentation.ui.settings.SettingsScreen
import com.livepush.presentation.ui.stream.StreamScreen

@Composable
fun LivePushNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    startDestination: String = Screen.Home.route
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier
    ) {
        composable(Screen.Home.route) { backStackEntry ->
            // 获取扫码结果
            val scanResult = backStackEntry.savedStateHandle.get<String>("scan_result")

            HomeScreen(
                onNavigateToStream = { url ->
                    navController.navigate(Screen.Stream.createRoute(url))
                },
                onNavigateToScanner = {
                    navController.navigate(Screen.Scanner.route)
                },
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings.route)
                },
                scanResult = scanResult
            )

            // 清除已使用的扫码结果
            if (scanResult != null) {
                backStackEntry.savedStateHandle.remove<String>("scan_result")
            }
        }

        composable(
            route = Screen.Stream.route,
            arguments = listOf(
                navArgument("streamUrl") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val encodedUrl = backStackEntry.arguments?.getString("streamUrl") ?: ""
            val streamUrl = Screen.Stream.decodeUrl(encodedUrl)

            StreamScreen(
                streamUrl = streamUrl,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(Screen.Scanner.route) {
            ScannerScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onScanResult = { result ->
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set("scan_result", result)
                    navController.popBackStack()
                }
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}
