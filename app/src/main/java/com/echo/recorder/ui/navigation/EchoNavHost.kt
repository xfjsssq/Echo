package com.echo.recorder.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.echo.recorder.ServiceLocator
import com.echo.recorder.ui.list.ListScreen
import com.echo.recorder.ui.list.ListViewModel
import com.echo.recorder.ui.player.PlayerPlaceholderScreen
import com.echo.recorder.ui.settings.SettingsScreen
import com.echo.recorder.ui.record.RecordScreen
import com.echo.recorder.ui.record.RecordViewModel

/**
 * 应用导航骨架. 三个路由: record(首页) / list / player/{recordingId}.
 */
@Composable
fun EchoNavHost(
    navController: NavHostController,
    recordViewModel: RecordViewModel,
    onRequestPermission: () -> Unit,
) {
    val context = LocalContext.current
    val listViewModel = remember { ListViewModel(context) }

    NavHost(
        navController = navController,
        startDestination = EchoRoutes.RECORD,
    ) {
        composable(EchoRoutes.RECORD) {
            RecordScreen(
                viewModel = recordViewModel,
                onRequestPermission = onRequestPermission,
                onOpenList = { navController.navigate(EchoRoutes.LIST) },
                onOpenSettings = { navController.navigate(EchoRoutes.SETTINGS) },
            )
        }
        composable(EchoRoutes.LIST) {
            ListScreen(
                viewModel = listViewModel,
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
        composable(EchoRoutes.SETTINGS) {
            SettingsScreen()
        }
    }
}
