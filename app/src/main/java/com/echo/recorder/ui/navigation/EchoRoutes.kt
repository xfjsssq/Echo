package com.echo.recorder.ui.navigation

/** 路由常量. */
object EchoRoutes {
    const val RECORD = "record"
    const val LIST = "list"
    const val PLAYER = "player"
    /** 带参数的路由模板. */
    const val PLAYER_WITH_ARG = "player/{recordingId}"

    fun playerRoute(recordingId: String): String = "player/$recordingId"
}
