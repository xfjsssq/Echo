package com.echo.recorder.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.echo.recorder.ui.about.AboutScreen
import com.echo.recorder.ui.list.ListScreen
import com.echo.recorder.ui.list.ListViewModel
import com.echo.recorder.ui.list.PublicDirManagementScreen
import com.echo.recorder.ui.lock.PasswordSetupScreen
import com.echo.recorder.ui.onboarding.OnboardingScreen
import com.echo.recorder.ui.settings.SettingsScreen
import com.echo.recorder.ui.theme.ThemeMode
import com.echo.recorder.ui.record.RecordScreen
import com.echo.recorder.ui.record.RecordViewModel

/**
 * 应用导航骨架. 路由: record(首页) / list / settings / password_setup / public_dir / about.
 * 播放不再有独立页, 在列表中原地完成.
 */
@Composable
fun EchoNavHost(
    navController: NavHostController,
    recordViewModel: RecordViewModel,
    onRequestPermission: () -> Unit,
    onRestartService: (savePending: Boolean) -> Unit,
    onRequestExit: () -> Unit,
    onLock: () -> Unit = {},
    passwordEnabled: Boolean = false,
    themeMode: ThemeMode = ThemeMode.LIGHT,
    onThemeChange: (ThemeMode) -> Unit = {},
) {
    val context = LocalContext.current
    val listViewModel = remember { ListViewModel(context) }

    NavHost(
        navController = navController,
        startDestination = EchoRoutes.RECORD,
        // ── 页面切换动效 (iOS 风格 push/pop, 克制不抢戏) ──
        // 前进: 新页从右 1/4 屏滑入 + 淡入 + 微缩放; 旧页向左淡出
        enterTransition = {
            slideIntoContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Left,
                animationSpec = tween(320, easing = FastOutSlowInEasing),
            ) + fadeIn(tween(320, easing = FastOutSlowInEasing)) +
                scaleIn(initialScale = 0.985f, animationSpec = tween(320, easing = FastOutSlowInEasing))
        },
        exitTransition = {
            slideOutOfContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Left,
                animationSpec = tween(260, easing = FastOutSlowInEasing),
            ) + fadeOut(tween(200))
        },
        // 返回: 当前页向右滑出, 上一页从左侧滑回
        popEnterTransition = {
            slideIntoContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Right,
                animationSpec = tween(320, easing = FastOutSlowInEasing),
            ) + fadeIn(tween(320, easing = FastOutSlowInEasing)) +
                scaleIn(initialScale = 0.985f, animationSpec = tween(320, easing = FastOutSlowInEasing))
        },
        popExitTransition = {
            slideOutOfContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Right,
                animationSpec = tween(260, easing = FastOutSlowInEasing),
            ) + fadeOut(tween(200)) +
                scaleOut(targetScale = 0.985f, animationSpec = tween(260, easing = FastOutSlowInEasing))
        },
    ) {
        composable(EchoRoutes.RECORD) {
            RecordScreen(
                viewModel = recordViewModel,
                onRequestPermission = onRequestPermission,
                onOpenList = { navController.navigate(EchoRoutes.LIST) },
                onOpenSettings = { navController.navigate(EchoRoutes.SETTINGS) },
                onExit = onRequestExit,
                onLock = onLock,
                passwordEnabled = passwordEnabled,
            )
        }
        composable(EchoRoutes.LIST) {
            ListScreen(
                viewModel = listViewModel,
                onOpenPublicDir = { navController.navigate(EchoRoutes.PUBLIC_DIR) },
            )
        }
        composable(EchoRoutes.SETTINGS) {
            SettingsScreen(
                onRestartService = onRestartService,
                onOpenPasswordSetup = { navController.navigate(EchoRoutes.passwordSetupRoute(false)) },
                onOpenAbout = { navController.navigate(EchoRoutes.ABOUT) },
                onOpenPublicDir = { navController.navigate(EchoRoutes.PUBLIC_DIR) },
                onChangePassword = { navController.navigate(EchoRoutes.passwordSetupRoute(true)) },
                // 设置页"如何使用小E" → 重新弹出引导卡片
                onOpenOnboarding = { navController.navigate(EchoRoutes.ONBOARDING) },
                themeMode = themeMode,
                onThemeChange = onThemeChange,
            )
        }
        composable(EchoRoutes.ONBOARDING) {
            OnboardingScreen(
                onFinish = { navController.popBackStack() },
                // 从设置页进入的引导同样支持主题切换 (选完即关闭引导)
                themeMode = themeMode,
                onThemeChange = onThemeChange,
            )
        }
        composable(
            route = EchoRoutes.PASSWORD_SETUP,
            arguments = listOf(
                navArgument(EchoRoutes.PASSWORD_SETUP_IS_CHANGE) {
                    type = NavType.BoolType
                    defaultValue = false
                },
            ),
        ) { backStackEntry ->
            val isChangePassword =
                backStackEntry.arguments?.getBoolean(EchoRoutes.PASSWORD_SETUP_IS_CHANGE) ?: false
            PasswordSetupScreen(
                isChangePassword = isChangePassword,
                onDone = { navController.popBackStack() },
                onBack = { navController.popBackStack() },
            )
        }
        composable(EchoRoutes.PUBLIC_DIR) {
            PublicDirManagementScreen(onBack = { navController.popBackStack() })
        }
        composable(EchoRoutes.ABOUT) {
            AboutScreen(onBack = { navController.popBackStack() })
        }
    }
}
