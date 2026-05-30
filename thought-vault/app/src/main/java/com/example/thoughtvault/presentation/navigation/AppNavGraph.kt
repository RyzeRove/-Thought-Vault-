package com.example.thoughtvault.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.thoughtvault.presentation.detail.DayDetailScreen
import com.example.thoughtvault.presentation.history.HistoryScreen
import com.example.thoughtvault.presentation.home.HomeScreen
import com.example.thoughtvault.presentation.settings.SettingsScreen

/**
 * 应用路由定义。
 * - /home: 首页（输入 + 今日列表）
 * - /history: 历史月份浏览
 * - /detail/{date}: 某天详情（原始 + AI 日报）
 * - /settings: NAS 配置
 */
object Routes {
    const val HOME = "home"
    const val HISTORY = "history"
    const val DAY_DETAIL = "detail/{date}"
    const val SETTINGS = "settings"

    fun dayDetail(date: String) = "detail/$date"
}

@Composable
fun AppNavGraph() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Routes.HOME,
    ) {
        // 首页
        composable(Routes.HOME) {
            HomeScreen(
                onNavigateToHistory = {
                    navController.navigate(Routes.HISTORY)
                },
                onNavigateToSettings = {
                    navController.navigate(Routes.SETTINGS)
                },
            )
        }

        // 历史浏览
        composable(Routes.HISTORY) {
            HistoryScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToDayDetail = { date ->
                    navController.navigate(Routes.dayDetail(date))
                },
            )
        }

        // 日期详情
        composable(
            route = Routes.DAY_DETAIL,
            arguments = listOf(
                navArgument("date") { type = NavType.StringType }
            )
        ) {
            DayDetailScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
            )
        }

        // 设置
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
            )
        }
    }
}
