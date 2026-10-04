package com.jarves.mh.runtime

import com.jarves.mh.model.AndroidProjectDetails
import java.io.File

object AndroidProjectInspector {
    fun inspect(workspaceDir: File): AndroidProjectDetails? {
        if (!workspaceDir.isDirectory) return null

        val manifest = workspaceDir.walkTopDown().maxDepth(6)
            .firstOrNull { it.isFile && it.name.equals("AndroidManifest.xml", ignoreCase = true) }

        val buildGradle = workspaceDir.walkTopDown().maxDepth(6)
            .firstOrNull { it.isFile && (it.name.equals("build.gradle", ignoreCase = true) || it.name.equals("build.gradle.kts", ignoreCase = true)) }

        val apk = workspaceDir.walkTopDown().maxDepth(8)
            .filter { it.isFile && it.extension.equals("apk", ignoreCase = true) }
            .maxByOrNull(File::lastModified)

        if (manifest == null && buildGradle == null && apk == null) {
            return null
        }

        var appName = "Android App"
        var packageName = "com.example.app"
        var versionName = "1.0.0"
        var versionCode = 1
        var minSdk = 26
        var targetSdk = 35
        val permissions = mutableListOf<String>()
        val activities = mutableListOf<String>()

        // Check strings.xml for app_name
        val stringsXml = workspaceDir.walkTopDown().maxDepth(6)
            .firstOrNull { it.isFile && it.name.equals("strings.xml", ignoreCase = true) }
        stringsXml?.let { file ->
            runCatching {
                val text = file.readText()
                val match = Regex("<string\\s+name=[\"']app_name[\"']>([^<]+)</string>").find(text)
                if (match != null) {
                    appName = match.groupValues[1].trim()
                }
            }
        }

        manifest?.let { file ->
            runCatching {
                val text = file.readText()
                val pkgMatch = Regex("package=[\"']([^\"']+)[\"']").find(text)
                if (pkgMatch != null) {
                    packageName = pkgMatch.groupValues[1].trim()
                }
                Regex("<uses-permission\\s+android:name=[\"']android\\.permission\\.([^\"']+)[\"']").findAll(text).forEach { m ->
                    permissions.add(m.groupValues[1])
                }
                Regex("<activity[\\s\\S]*?android:name=[\"']([^\"']+)[\"']").findAll(text).forEach { m ->
                    activities.add(m.groupValues[1].substringAfterLast('.'))
                }
            }
        }

        buildGradle?.let { file ->
            runCatching {
                val text = file.readText()
                val appMatch = Regex("applicationId\\s*=?\\s*[\"']([^\"']+)[\"']").find(text)
                if (appMatch != null) {
                    packageName = appMatch.groupValues[1].trim()
                }
                val minSdkMatch = Regex("minSdk\\s*=?\\s*([0-9]+)").find(text)
                if (minSdkMatch != null) {
                    minSdk = minSdkMatch.groupValues[1].toIntOrNull() ?: 26
                }
                val targetSdkMatch = Regex("targetSdk\\s*=?\\s*([0-9]+)").find(text)
                if (targetSdkMatch != null) {
                    targetSdk = targetSdkMatch.groupValues[1].toIntOrNull() ?: 35
                }
                val vNameMatch = Regex("versionName\\s*=?\\s*[\"']([^\"']+)[\"']").find(text)
                if (vNameMatch != null) {
                    versionName = vNameMatch.groupValues[1].trim()
                }
                val vCodeMatch = Regex("versionCode\\s*=?\\s*([0-9]+)").find(text)
                if (vCodeMatch != null) {
                    versionCode = vCodeMatch.groupValues[1].toIntOrNull() ?: 1
                }
            }
        }

        return AndroidProjectDetails(
            appName = appName,
            packageName = packageName,
            versionName = versionName,
            versionCode = versionCode,
            minSdk = minSdk,
            targetSdk = targetSdk,
            permissions = permissions.distinct(),
            activities = activities.distinct(),
            hasDebugApk = apk != null && apk.exists() && apk.length() > 0,
            apkFile = apk,
            apkSizeBytes = apk?.length() ?: 0L,
            apkLastModified = apk?.lastModified() ?: 0L,
            hasGradleConfig = buildGradle != null,
        )
    }
}
