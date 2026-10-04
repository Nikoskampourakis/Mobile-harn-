package com.jarves.mh.model

import java.io.File

data class AndroidProjectDetails(
    val appName: String = "Android App",
    val packageName: String = "com.example.app",
    val versionName: String = "1.0.0",
    val versionCode: Int = 1,
    val minSdk: Int = 26,
    val targetSdk: Int = 35,
    val permissions: List<String> = emptyList(),
    val activities: List<String> = emptyList(),
    val hasDebugApk: Boolean = false,
    val apkFile: File? = null,
    val apkSizeBytes: Long = 0L,
    val apkLastModified: Long = 0L,
    val hasGradleConfig: Boolean = false,
)
