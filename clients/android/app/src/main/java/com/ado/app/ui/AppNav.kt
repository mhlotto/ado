package com.ado.app.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.ado.app.data.AdoRepository
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

@Composable
fun AppNav(repository: AdoRepository) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "splash") {
        composable("splash") {
            SplashScreen(
                initialize = repository::initialize,
                friendlyError = repository::friendlyError,
                onFinished = {
                    navController.navigate("projects") {
                        popUpTo("splash") { inclusive = true }
                    }
                },
            )
        }
        composable("projects") {
            ProjectListScreen(
                repository = repository,
                onOpenProject = { navController.navigate("project/${it.routePart()}") },
                onOpenSettings = { navController.navigate("settings") },
            )
        }
        composable(
            route = "project/{projectId}",
            arguments = listOf(navArgument("projectId") { type = NavType.StringType }),
        ) { entry ->
            val projectId = entry.arguments?.getString("projectId").orEmpty().routeDecode()
            ProjectDetailScreen(
                projectId = projectId,
                repository = repository,
                onBack = { navController.popBackStack() },
                onOpenTask = { navController.navigate("task/${it.routePart()}") },
                onOpenSettings = { navController.navigate("settings") },
            )
        }
        composable(
            route = "task/{taskId}",
            arguments = listOf(navArgument("taskId") { type = NavType.StringType }),
        ) { entry ->
            val taskId = entry.arguments?.getString("taskId").orEmpty().routeDecode()
            TaskDetailScreen(
                taskId = taskId,
                repository = repository,
                onBack = { navController.popBackStack() },
                onOpenSettings = { navController.navigate("settings") },
            )
        }
        composable("settings") {
            SettingsScreen(
                repository = repository,
                onBack = { navController.popBackStack() },
                onOpenTemplates = { navController.navigate("templates") },
            )
        }
        composable("templates") {
            TemplateListScreen(
                repository = repository,
                onBack = { navController.popBackStack() },
                onOpenTemplate = { navController.navigate("template/${it.routePart()}") },
                onOpenSettings = { navController.navigate("settings") },
            )
        }
        composable(
            route = "template/{templateKey}",
            arguments = listOf(navArgument("templateKey") { type = NavType.StringType }),
        ) { entry ->
            val templateKey = entry.arguments?.getString("templateKey").orEmpty().routeDecode()
            TemplateDetailScreen(
                templateKey = templateKey,
                repository = repository,
                onBack = { navController.popBackStack() },
                onOpenSettings = { navController.navigate("settings") },
            )
        }
    }
}

private fun String.routePart(): String = URLEncoder.encode(this, StandardCharsets.UTF_8.name())

private fun String.routeDecode(): String = URLDecoder.decode(this, StandardCharsets.UTF_8.name())
