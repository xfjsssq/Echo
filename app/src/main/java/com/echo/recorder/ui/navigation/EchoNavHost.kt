package com.echo.recorder.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.echo.recorder.ui.list.ListScreen
import com.echo.recorder.ui.list.ListViewModel
import com.echo.recorder.ui.settings.SettingsScreen
import com.echo.recorder.ui.record.RecordScreen
import com.echo.recorder.ui.record.RecordViewModel

/**
 * 应用导航骨架. 三个路由: record(首页) / list / settings.
 * 播放不再有独立页, 在列表中原地完成.
 */
@Composable
fun EchoNavHost(
    navController: NavHostController,
    recordViewModel: RecordViewModel,
    onRequestPermission: () -> Unit,
    onRestartService: (savePending: Boolean) -> Unit,
    onRequestExit: () -> Unit,
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
                onExit = onRequestExit,
            )
        }
        composable(EchoRoutes.LIST) {
            ListScreen(viewModel = listViewModel)
        }
        composable(EchoRoutes.SETTINGS) {
            SettingsScreen(onRestartService = onRestartService)
        }
    }
}
