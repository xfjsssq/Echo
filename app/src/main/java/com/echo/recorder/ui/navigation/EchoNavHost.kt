package com.echo.recorder.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.echo.recorder.ui.list.ListPlaceholderScreen
import com.echo.recorder.ui.player.PlayerPlaceholderScreen
import com.echo.recorder.ui.record.RecordScreen
import com.echo.recorder.ui.record.RecordViewModel

/**
 * 应用导航骨架. 三个路由: record(首页) / list / player/{recordingId}.
 *
 * [recordViewModel] 由宿主 (EchoApp) 持有并注入 recorder.
 */
@Composable
fun EchoNavHost(
    navController: NavHostController,
    recordViewModel: RecordViewModel,
    onRequestPermission: () -> Unit,
) {
    NavHost(
        navController = navController,
        startDestination = EchoRoutes.RECORD,
    ) {
        composable(EchoRoutes.RECORD) {
            RecordScreen(
                viewModel = recordViewModel,
                onRequestPermission = onRequestPermission,
                onOpenList = { navController.navigate(EchoRoutes.LIST) },
            )
        }
        composable(EchoRoutes.LIST) {
            ListPlaceholderScreen(
                onOpenPlayer = { id -> navController.navigate(EchoRoutes.playerRoute(id)) },
            )
        }
        composable(
            route = EchoRoutes.PLAYER_WITH_ARG,
            arguments = listOf(navArgument("recordingId") { type = NavType.StringType }),
        ) { entry ->
            val id = entry.arguments?.getString("recordingId")
            PlayerPlaceholderScreen(recordingId = id)
        }
    }
}
