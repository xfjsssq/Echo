package com.echo.recorder

import com.echo.recorder.ui.navigation.EchoRoutes
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 导航骨架的可测核心. 路由跳转的完整验证依赖 Compose UI Test (设备), 此处覆盖:
 * - [EchoRoutes] 常量与当前路由表一致 (播放路由已移除, 改为列表内原地播放)
 */
class EchoNavHostTest {

    @Test
    fun `路由常量正确`() {
        assertEquals("record", EchoRoutes.RECORD)
        assertEquals("list", EchoRoutes.LIST)
        assertEquals("settings", EchoRoutes.SETTINGS)
        assertEquals("password_setup", EchoRoutes.PASSWORD_SETUP)
        assertEquals("public_dir", EchoRoutes.PUBLIC_DIR)
        assertEquals("about", EchoRoutes.ABOUT)
        assertEquals("onboarding", EchoRoutes.ONBOARDING)
    }

    @Test
    fun `所有路由常量非空且互不相同`() {
        val routes = listOf(
            EchoRoutes.RECORD,
            EchoRoutes.LIST,
            EchoRoutes.SETTINGS,
            EchoRoutes.PASSWORD_SETUP,
            EchoRoutes.PUBLIC_DIR,
            EchoRoutes.ABOUT,
            EchoRoutes.ONBOARDING,
        )
        assertEquals(routes.size, routes.distinct().size)
        routes.forEach { assertEquals(false, it.isBlank()) }
    }
}
