package com.echo.recorder.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
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
import com.echo.recorder.ui.theme.EchoMotion
import com.echo.recorder.ui.record.RecordScreen
import com.echo.recorder.ui.record.RecordViewModel

/**
 * 应用导航骨架. 路由: record(首页) / list / settings / password_setup / public_dir / about.
 * 播放不再有独立页, 在列表中原地完成.
 */

// 动效 token 统一收敛至 EchoMotion (Material 强调缓动):
// 进入快起慢停(内容稳稳落位), 离开慢起快走(让位不拖沓). 进出时长匹配 300ms.
// 前进: 新页从右滑入 + 淡入 + 微放大(0.96→1, 由远及近的视差); 旧页向左滑出 + 淡出 + 微放大(→1.04, 退到后方)
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
        // ── 页面切换动效 (iOS 风格 push/pop, 强调自然连贯) ──
        // 前进: 新页从右滑入 + 淡入 + 微放大(0.96→1, 由远及近的视差); 旧页向左滑出 + 淡出 + 微放大(→1.04, 退到后方)
        enterTransition = {
            slideIntoContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Left,
                animationSpec = tween(300, easing = EchoMotion.EmphasizedDecelerate),
            ) + fadeIn(tween(300, easing = EchoMotion.EmphasizedDecelerate)) +
                scaleIn(initialScale = 0.96f, animationSpec = tween(300, easing = EchoMotion.EmphasizedDecelerate))
        },
        exitTransition = {
            slideOutOfContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Left,
                animationSpec = tween(300, easing = EchoMotion.EmphasizedAccelerate),
            ) + fadeOut(tween(300, easing = EchoMotion.EmphasizedAccelerate)) +
                scaleOut(targetScale = 1.04f, animationSpec = tween(300, easing = EchoMotion.EmphasizedAccelerate))
        },
        // 返回: 当前页向右滑出, 上一页从左侧滑回
        popEnterTransition = {
            slideIntoContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Right,
                animationSpec = tween(300, easing = EchoMotion.EmphasizedDecelerate),
            ) + fadeIn(tween(300, easing = EchoMotion.EmphasizedDecelerate)) +
                scaleIn(initialScale = 0.96f, animationSpec = tween(300, easing = EchoMotion.EmphasizedDecelerate))
        },
        popExitTransition = {
            slideOutOfContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Right,
                animationSpec = tween(300, easing = EchoMotion.EmphasizedAccelerate),
            ) + fadeOut(tween(300, easing = EchoMotion.EmphasizedAccelerate)) +
                scaleOut(targetScale = 1.04f, animationSpec = tween(300, easing = EchoMotion.EmphasizedAccelerate))
        },
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
            ListScreen(
                viewModel = listViewModel,
                onOpenPublicDir = { navController.navigate(EchoRoutes.PUBLIC_DIR) },
                onOpenPasswordSetup = { navController.navigate(EchoRoutes.passwordSetupRoute(false)) },
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
            )
        }
        composable(EchoRoutes.ONBOARDING) {
            OnboardingScreen(
                onFinish = { navController.popBackStack() },
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
